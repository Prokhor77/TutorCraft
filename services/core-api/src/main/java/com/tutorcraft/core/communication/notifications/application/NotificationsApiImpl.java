package com.tutorcraft.core.communication.notifications.application;

import com.tutorcraft.core.communication.NotificationsApi;
import com.tutorcraft.core.communication.notifications.application.NotificationRepository.StoredNotification;
import com.tutorcraft.core.communication.notifications.domain.ChannelResolver;
import com.tutorcraft.core.communication.notifications.domain.ChannelResolver.Recipient;
import com.tutorcraft.core.communication.notifications.domain.NotificationChannel;
import com.tutorcraft.core.identity.UsersApi;
import com.tutorcraft.core.identity.UsersApi.UserRef;
import com.tutorcraft.core.shared.config.AppProperties;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.i18n.Messages;
import com.tutorcraft.core.shared.outbox.OutboxPublisher;
import com.tutorcraft.core.shared.outbox.Topics;
import com.tutorcraft.core.shared.persistence.JsonCodec;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Реализация NotificationsApi (FR-NOTIF-01/04): каналы по настройкам, текст на языке получателя, in-app строка
 * (кроме категории account) и событие tc.notify.requested.v1 в outbox для внешних каналов и WebSocket.
 * Идемпотентно: (получатель, dedupeKey) — одно in-app уведомление и одна доставка на канал.
 */
@Component
class NotificationsApiImpl implements NotificationsApi {

    static final String EVENT_TYPE = "notify.requested";
    private static final Logger log = LoggerFactory.getLogger(NotificationsApiImpl.class);
    private static final String ACTIVE = "active";
    private static final String UNDELIVERABLE_EMAIL_SUFFIX = "@telegram.invalid";
    private static final String TITLE_SUFFIX = ".title";
    private static final String BODY_SUFFIX = ".body";
    private static final String ACTION_SUFFIX = ".action";
    private static final String RELATIVE_LINK_PREFIX = "/";
    private static final int MAX_TITLE = 200;
    private static final int MAX_BODY = 2000;
    private static final int MAX_ACTION = 64;
    private static final String COUNTER_CATEGORY = "counter";
    private static final Pattern COUNTER_NAME = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");

    private final UsersApi users;
    private final NotificationRepository notifications;
    private final PreferenceRepository preferences;
    private final Messages messages;
    private final OutboxPublisher outbox;
    private final JsonCodec json;
    private final Clock clock;
    private final String publicBaseUrl;

    NotificationsApiImpl(UsersApi users, NotificationRepository notifications, PreferenceRepository preferences,
                         Messages messages, OutboxPublisher outbox, JsonCodec json, Clock clock, AppProperties properties) {
        this.users = users;
        this.notifications = notifications;
        this.preferences = preferences;
        this.messages = messages;
        this.outbox = outbox;
        this.json = json;
        this.clock = clock;
        this.publicBaseUrl = properties.publicBaseUrl();
    }

    @Override
    @Transactional
    public void notify(NotificationCommand command) {
        validate(command);
        List<UUID> userIds = command.userIds().stream().filter(Objects::nonNull).distinct().toList();
        Map<UUID, UserRef> recipients = users.findAll(command.tenantId(), userIds);
        Map<UUID, Map<NotificationChannel, Boolean>> prefs = preferences.forCategory(command.tenantId(), userIds, command.category());
        userIds.stream()
                .filter(recipients::containsKey)
                .forEach(userId -> deliver(command, recipients.get(userId), prefs.getOrDefault(userId, Map.of())));
    }

    @Override
    @Transactional
    public void pushCounter(UUID tenantId, UUID userId, String name, long value) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        if (name == null || !COUNTER_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid counter name");
        }
        String body = json.write(new CounterBody(name, value));
        outbox.publish(Topics.NOTIFY_REQUESTED, tenantId, EVENT_TYPE, new NotifyRequested(Ids.newId(), userId, COUNTER_CATEGORY,
                List.of(NotificationChannel.WEB.key()), name, body, null, null, null, null, null));
    }

    private void deliver(NotificationCommand command, UserRef user, Map<NotificationChannel, Boolean> userPrefs) {
        boolean directEmail = command.directEmail() != null;
        Set<NotificationChannel> channels = ChannelResolver.resolve(command.category(), recipient(user), userPrefs,
                command.force(), directEmail);
        if (channels.isEmpty()) {
            return;
        }
        Locale locale = Locale.forLanguageTag(user.locale());
        String title = truncate(messages.get(locale, command.messageCode() + TITLE_SUFFIX, command.args().toArray()), MAX_TITLE);
        String body = truncate(messages.get(locale, command.messageCode() + BODY_SUFFIX, command.args().toArray()), MAX_BODY);
        String actionLabel = optionalMessage(locale, command.messageCode() + ACTION_SUFFIX, command.args());
        UUID notificationId = Ids.newId();
        Set<NotificationChannel> fresh = reserve(command, user, channels, notificationId, title, body);
        if (fresh.isEmpty()) {
            return;
        }
        String email = directEmail ? command.directEmail() : user.email();
        outbox.publish(Topics.NOTIFY_REQUESTED, command.tenantId(), EVENT_TYPE, new NotifyRequested(notificationId, user.id(),
                command.category().key(), fresh.stream().map(NotificationChannel::key).sorted().toList(), title, body,
                absolute(command.link()), locale.getLanguage(),
                fresh.contains(NotificationChannel.EMAIL) ? email : null,
                fresh.contains(NotificationChannel.TELEGRAM) ? user.telegramChatId() : null, actionLabel));
        log.debug("Notification {} ({}) queued for user {} via {}", notificationId, command.category().key(), user.id(), fresh);
    }

    /** Фиксирует каналы, по которым это событие ещё не отправлялось получателю. */
    private Set<NotificationChannel> reserve(NotificationCommand command, UserRef user, Set<NotificationChannel> channels,
                                             UUID notificationId, String title, String body) {
        Instant now = clock.instant();
        Set<NotificationChannel> fresh = EnumSet.noneOf(NotificationChannel.class);
        if (channels.contains(NotificationChannel.WEB) && notifications.insertInApp(new StoredNotification(notificationId,
                command.tenantId(), user.id(), command.category().key(), title, body, command.link(), command.dedupeKey(),
                null, now))) {
            fresh.add(NotificationChannel.WEB);
        }
        channels.stream()
                .filter(NotificationChannel::external)
                .filter(channel -> notifications.insertDelivery(command.tenantId(), notificationId, user.id(),
                        command.dedupeKey(), channel, now))
                .forEach(fresh::add);
        return fresh;
    }

    /** Текст кнопки ссылки ({@code <code>.action}) необязателен: без ключа в i18n канал подставит «Открыть». */
    private String optionalMessage(Locale locale, String code, List<Object> args) {
        String text = messages.get(locale, code, args.toArray());
        return code.equals(text) ? null : truncate(text, MAX_ACTION);
    }

    private static Recipient recipient(UserRef user) {
        boolean emailDeliverable = user.email() != null && !user.email().endsWith(UNDELIVERABLE_EMAIL_SUFFIX);
        return new Recipient(ACTIVE.equals(user.status()), emailDeliverable, user.telegramChatId() != null);
    }

    private String absolute(String link) {
        if (link == null || !link.startsWith(RELATIVE_LINK_PREFIX)) {
            return link;
        }
        return publicBaseUrl + link;
    }

    private static void validate(NotificationCommand command) {
        Objects.requireNonNull(command.tenantId(), "tenantId");
        Objects.requireNonNull(command.userIds(), "userIds");
        Objects.requireNonNull(command.category(), "category");
        Objects.requireNonNull(command.messageCode(), "messageCode");
        Objects.requireNonNull(command.args(), "args");
        if (command.dedupeKey() == null || command.dedupeKey().isBlank()) {
            throw new IllegalArgumentException("dedupeKey is required");
        }
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    /** Тело счётчика: notifier пересылает его клиенту как {"type":"counter","data":{...}}. */
    record CounterBody(String name, long value) {
    }

    /** Полезная нагрузка tc.notify.requested.v1 (docs/events/README.md). */
    record NotifyRequested(UUID notificationId, UUID userId, String category, List<String> channels, String title,
                           String body, String link, String locale, String email, Long telegramChatId,
                           String actionLabel) {
    }
}
