package com.tutorcraft.core.identity.application;

import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.identity.application.OneTimeTokenRepository.Kind;
import com.tutorcraft.core.identity.application.OneTimeTokenRepository.OneTimeToken;
import com.tutorcraft.core.identity.application.OneTimeTokens.IssuedToken;
import com.tutorcraft.core.identity.domain.UserAccount;
import com.tutorcraft.core.shared.config.AppProperties;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.outbox.ProcessedEvents;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Привязка Telegram-чата для уведомлений (FR-NOTIF-HYB-01): одноразовый код в deep-link бота;
 * notifier после /start публикует tc.telegram.linked.v1, и чат сохраняется у пользователя.
 */
@Service
public class TelegramLinkService {

    static final String CONSUMER = "identity.telegram-linked";
    private static final Logger log = LoggerFactory.getLogger(TelegramLinkService.class);
    private static final Duration CODE_TTL = Duration.ofMinutes(15);
    private static final String DEEP_LINK_TEMPLATE = "https://t.me/%s?start=%s";

    private final OneTimeTokens tokens;
    private final UserRepository users;
    private final ProcessedEvents processedEvents;
    private final CurrentUserProvider currentUser;
    private final AuditLog audit;
    private final String botUsername;

    public TelegramLinkService(OneTimeTokens tokens, UserRepository users, ProcessedEvents processedEvents,
                               CurrentUserProvider currentUser, AuditLog audit, AppProperties properties) {
        this.tokens = tokens;
        this.users = users;
        this.processedEvents = processedEvents;
        this.currentUser = currentUser;
        this.audit = audit;
        this.botUsername = properties.oauth().telegramBotUsername();
    }

    @Transactional
    public String createDeepLink() {
        if (botUsername == null || botUsername.isBlank()) {
            throw new NotFoundException(IdentityErrors.PROVIDER_DISABLED, "Telegram is not configured");
        }
        CurrentUser user = currentUser.require();
        IssuedToken code = tokens.issue(Kind.TELEGRAM_LINK, user.tenantId(), user.userId(), CODE_TTL);
        return DEEP_LINK_TEMPLATE.formatted(botUsername, code.value());
    }

    /** Идемпотентно по eventId (at-least-once доставка Kafka). */
    @Transactional
    public void completeLink(UUID eventId, TelegramLinked event) {
        if (!processedEvents.markProcessed(eventId, CONSUMER)) {
            return;
        }
        Optional<OneTimeToken> code = tokens.findValid(Kind.TELEGRAM_LINK, event.linkCode());
        if (code.isEmpty() || !tokens.tryConsume(Kind.TELEGRAM_LINK, code.get())) {
            log.info("Telegram link event {} ignored: code unknown, expired or used", eventId);
            return;
        }
        OneTimeToken token = code.get();
        users.findById(token.tenantId(), token.userId()).ifPresent(user -> link(user, event));
    }

    private void link(UserAccount user, TelegramLinked event) {
        Long telegramUserId = telegramIdFreeFor(user, event.telegramUserId()) ? event.telegramUserId() : null;
        users.linkTelegram(user.tenantId(), user.id(), telegramUserId, event.chatId());
        audit.record(AuditRecord.of(user.tenantId(), user.id(), "user.telegram_linked", "user", user.id().toString()));
        log.info("Telegram chat linked to user {}", user.id());
    }

    /** telegram_user_id уникален в tenant: не переносим его с другой учётной записи. */
    private boolean telegramIdFreeFor(UserAccount user, Long telegramUserId) {
        if (telegramUserId == null) {
            return false;
        }
        return users.findByTelegramUserId(user.tenantId(), telegramUserId)
                .map(owner -> owner.id().equals(user.id()))
                .orElse(true);
    }

    public record TelegramLinked(String linkCode, Long chatId, Long telegramUserId, String username) {
    }
}
