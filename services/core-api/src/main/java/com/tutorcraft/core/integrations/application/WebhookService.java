package com.tutorcraft.core.integrations.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.integrations.application.WebhookDeliveryRepository.DeliveryView;
import com.tutorcraft.core.integrations.application.WebhookRepository.NewWebhook;
import com.tutorcraft.core.integrations.application.WebhookRepository.WebhookSummary;
import com.tutorcraft.core.integrations.domain.WebhookEvent;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.domain.ValidationException;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import com.tutorcraft.core.shared.security.SecretCipher;
import com.tutorcraft.core.shared.security.TokenHasher;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Подписки на исходящие вебхуки и журнал доставки (FR-INTEG-02). */
@Service
public class WebhookService {

    private static final String SECRET_PREFIX = "whsec_";
    private static final int MAX_URL_LENGTH = 2048;

    private final WebhookRepository webhooks;
    private final WebhookDeliveryRepository deliveries;
    private final WebhookUrlPolicy urlPolicy;
    private final SecretCipher cipher;
    private final AccessService access;
    private final CurrentUserProvider currentUser;
    private final AuditLog audit;
    private final Clock clock;

    public WebhookService(WebhookRepository webhooks, WebhookDeliveryRepository deliveries, WebhookUrlPolicy urlPolicy,
                          SecretCipher cipher, AccessService access, CurrentUserProvider currentUser, AuditLog audit,
                          Clock clock) {
        this.webhooks = webhooks;
        this.deliveries = deliveries;
        this.urlPolicy = urlPolicy;
        this.cipher = cipher;
        this.access = access;
        this.currentUser = currentUser;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<WebhookSummary> list() {
        access.require(Permission.INTEGRATION_MANAGE, AccessContext.tenant());
        return webhooks.list(currentUser.require().tenantId());
    }

    /** @return секрет подписи в открытом виде — показывается один раз; в БД хранится зашифрованным */
    @Transactional
    public CreatedWebhook create(String url, List<String> events) {
        CurrentUser user = currentUser.require();
        access.require(Permission.INTEGRATION_MANAGE, AccessContext.tenant());
        if (url == null || url.length() > MAX_URL_LENGTH) {
            throw ValidationException.single("url", "invalid", "URL is required and must be shorter than 2048 characters");
        }
        urlPolicy.require(url);
        List<String> eventKeys = parseEvents(events);
        String secret = SECRET_PREFIX + TokenHasher.newToken();
        UUID id = Ids.newId();
        webhooks.insert(new NewWebhook(id, user.tenantId(), url.trim(), eventKeys, cipher.encrypt(secret), user.userId(),
                clock.instant()));
        audit.record(AuditRecord.of(user.tenantId(), user.userId(), "integration.webhook_created", "webhook", id.toString())
                .withDiff(Map.of("events", eventKeys)));
        return new CreatedWebhook(id, secret);
    }

    @Transactional
    public void delete(UUID webhookId) {
        CurrentUser user = currentUser.require();
        access.require(Permission.INTEGRATION_MANAGE, AccessContext.tenant());
        if (!webhooks.delete(user.tenantId(), webhookId, clock.instant())) {
            throw notFound();
        }
        audit.record(AuditRecord.of(user.tenantId(), user.userId(), "integration.webhook_deleted", "webhook", webhookId.toString()));
    }

    @Transactional(readOnly = true)
    public PageResponse<DeliveryView> deliveries(UUID webhookId, PageQuery page) {
        access.require(Permission.INTEGRATION_MANAGE, AccessContext.tenant());
        UUID tenantId = currentUser.require().tenantId();
        if (!webhooks.exists(tenantId, webhookId)) {
            throw notFound();
        }
        return page.toPage(deliveries.page(tenantId, webhookId, page), DeliveryView::createdAt, DeliveryView::id);
    }

    private static List<String> parseEvents(List<String> events) {
        if (events == null || events.isEmpty()) {
            throw ValidationException.single("events", "required", "At least one event is required");
        }
        return events.stream()
                .map(key -> WebhookEvent.find(key)
                        .orElseThrow(() -> ValidationException.single("events", "invalid", "Unknown event")))
                .map(WebhookEvent::key)
                .distinct()
                .sorted()
                .toList();
    }

    private static NotFoundException notFound() {
        return new NotFoundException(IntegrationErrors.WEBHOOK_NOT_FOUND, "Webhook not found");
    }

    public record CreatedWebhook(UUID id, String secret) {

        @Override
        public String toString() {
            return "CreatedWebhook[id=" + id + "]";
        }
    }
}
