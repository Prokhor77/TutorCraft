package com.tutorcraft.core.integrations.application;

import com.tutorcraft.core.integrations.application.WebhookDeliveryRepository.ClaimedDelivery;
import com.tutorcraft.core.integrations.application.WebhookRepository.WebhookEndpoint;
import com.tutorcraft.core.integrations.domain.RetrySchedule;
import com.tutorcraft.core.integrations.domain.WebhookSignature;
import com.tutorcraft.core.integrations.domain.WebhookUrlGuard.Verdict;
import com.tutorcraft.core.shared.security.SecretCipher;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Фоновая доставка вебхуков (FR-INTEG-02): пачки pending-доставок захватываются с арендой, отправляются вне
 * транзакции БД, результат пишется в журнал. Повторы — {@link RetrySchedule}. URL перепроверяется перед
 * каждой отправкой (защита от DNS rebinding).
 */
@Component
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);
    private static final int BATCH_SIZE = 20;
    private static final Duration LEASE = Duration.ofMinutes(2);
    private static final int MAX_ERROR_LENGTH = 500;
    private static final String WEBHOOK_DELETED = "webhook_deleted";
    private static final String HTTP_ERROR_PREFIX = "HTTP ";

    private final WebhookDeliveryRepository deliveries;
    private final WebhookRepository webhooks;
    private final WebhookUrlPolicy urlPolicy;
    private final WebhookSender sender;
    private final SecretCipher cipher;
    private final Clock clock;

    public WebhookDispatcher(WebhookDeliveryRepository deliveries, WebhookRepository webhooks, WebhookUrlPolicy urlPolicy,
                             WebhookSender sender, SecretCipher cipher, Clock clock) {
        this.deliveries = deliveries;
        this.webhooks = webhooks;
        this.urlPolicy = urlPolicy;
        this.sender = sender;
        this.cipher = cipher;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${tutorcraft.webhooks.poll-interval:PT5S}")
    public void dispatch() {
        Instant now = clock.instant();
        List<ClaimedDelivery> batch = deliveries.claimDue(now, now.plus(LEASE), BATCH_SIZE);
        batch.forEach(this::deliverSafely);
    }

    private void deliverSafely(ClaimedDelivery delivery) {
        int attempt = delivery.attempts() + 1;
        try {
            deliver(delivery, attempt);
        } catch (RuntimeException e) {
            log.error("Webhook delivery {} failed unexpectedly: {}", delivery.id(), e.getClass().getSimpleName());
            recordResult(delivery, attempt, new WebhookSender.Result(null, e.getClass().getSimpleName()));
        }
    }

    private void deliver(ClaimedDelivery delivery, int attempt) {
        Optional<WebhookEndpoint> endpoint = webhooks.endpoint(delivery.tenantId(), delivery.webhookId());
        if (endpoint.isEmpty()) {
            deliveries.markFailed(delivery.id(), delivery.attempts(), null, WEBHOOK_DELETED, clock.instant());
            return;
        }
        Verdict verdict = urlPolicy.check(endpoint.get().url());
        if (verdict != Verdict.ALLOWED) {
            log.warn("Webhook {} URL rejected at dispatch: {}", delivery.webhookId(), verdict);
            deliveries.markFailed(delivery.id(), attempt, null, verdict.name().toLowerCase(Locale.ROOT), clock.instant());
            return;
        }
        recordResult(delivery, attempt, send(endpoint.get(), delivery));
    }

    private WebhookSender.Result send(WebhookEndpoint endpoint, ClaimedDelivery delivery) {
        long timestamp = clock.instant().getEpochSecond();
        String signature = WebhookSignature.header(cipher.decrypt(endpoint.secretEncrypted()), timestamp, delivery.payloadJson());
        return sender.send(URI.create(endpoint.url()), delivery.payloadJson(), signature);
    }

    private void recordResult(ClaimedDelivery delivery, int attempt, WebhookSender.Result result) {
        Instant now = clock.instant();
        if (result.successful()) {
            deliveries.markSucceeded(delivery.id(), attempt, result.statusCode(), now);
            return;
        }
        String error = truncate(result.error() != null ? result.error() : HTTP_ERROR_PREFIX + result.statusCode());
        Optional<Duration> retryIn = RetrySchedule.delayAfter(attempt);
        if (retryIn.isPresent()) {
            deliveries.markRetry(delivery.id(), attempt, result.statusCode(), error, now.plus(retryIn.get()), now);
            return;
        }
        deliveries.markFailed(delivery.id(), attempt, result.statusCode(), error, now);
        log.warn("Webhook delivery {} gave up after {} attempts", delivery.id(), attempt);
    }

    private static String truncate(String error) {
        return error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error;
    }
}
