package com.tutorcraft.core.communication.notifications.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.tutorcraft.core.communication.NotificationCategory;
import com.tutorcraft.core.communication.notifications.domain.ChannelResolver.Recipient;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Выбор каналов по настройкам и умолчаниям (FR-NOTIF-02 + гибрид Telegram). */
class ChannelResolverTest {

    private static final Recipient FULL = new Recipient(true, true, true);
    private static final Recipient NO_TELEGRAM = new Recipient(true, true, false);

    @Test
    void defaultsPerCategory() {
        assertThat(resolve(NotificationCategory.GRADE_PUBLISHED, FULL)).containsExactlyInAnyOrder(
                NotificationChannel.WEB, NotificationChannel.EMAIL, NotificationChannel.TELEGRAM);
        assertThat(resolve(NotificationCategory.SUBMISSION_RECEIVED, FULL)).containsExactly(NotificationChannel.WEB);
        assertThat(resolve(NotificationCategory.NEW_ITEM, FULL))
                .containsExactlyInAnyOrder(NotificationChannel.WEB, NotificationChannel.TELEGRAM);
        assertThat(resolve(NotificationCategory.FORUM_REPLY, FULL)).containsExactly(NotificationChannel.WEB);
        assertThat(resolve(NotificationCategory.VIDEO_READY, FULL))
                .containsExactlyInAnyOrder(NotificationChannel.WEB, NotificationChannel.TELEGRAM);
    }

    @Test
    void telegramRequiresLinkedChatAndEmailRequiresDeliverableAddress() {
        assertThat(resolve(NotificationCategory.DEADLINE, NO_TELEGRAM))
                .containsExactlyInAnyOrder(NotificationChannel.WEB, NotificationChannel.EMAIL);
        assertThat(resolve(NotificationCategory.DEADLINE, new Recipient(true, false, true)))
                .containsExactlyInAnyOrder(NotificationChannel.WEB, NotificationChannel.TELEGRAM);
    }

    @Test
    void userPreferencesOverrideDefaults() {
        Map<NotificationChannel, Boolean> prefs = Map.of(NotificationChannel.EMAIL, false, NotificationChannel.WEB, false);
        assertThat(ChannelResolver.resolve(NotificationCategory.GRADE_PUBLISHED, FULL, prefs, false, false))
                .containsExactly(NotificationChannel.TELEGRAM);
        assertThat(ChannelResolver.resolve(NotificationCategory.SUBMISSION_RECEIVED, FULL,
                Map.of(NotificationChannel.EMAIL, true), false, false))
                .containsExactlyInAnyOrder(NotificationChannel.WEB, NotificationChannel.EMAIL);
    }

    @Test
    void forceIgnoresPreferencesButNotCapabilities() {
        Map<NotificationChannel, Boolean> allOff = Map.of(NotificationChannel.WEB, false, NotificationChannel.EMAIL, false,
                NotificationChannel.TELEGRAM, false);
        assertThat(ChannelResolver.resolve(NotificationCategory.ANNOUNCEMENT, NO_TELEGRAM, allOff, true, false))
                .containsExactlyInAnyOrder(NotificationChannel.WEB, NotificationChannel.EMAIL);
    }

    @Test
    void accountCategoryIsEmailOnlyAndNeverStoredInApp() {
        assertThat(ChannelResolver.resolve(NotificationCategory.ACCOUNT, FULL, Map.of(NotificationChannel.EMAIL, false), true,
                false)).containsExactly(NotificationChannel.EMAIL);
        assertThat(ChannelResolver.resolve(NotificationCategory.ACCOUNT, new Recipient(false, true, false), Map.of(), true,
                false)).containsExactly(NotificationChannel.EMAIL);
        assertThat(ChannelResolver.resolve(NotificationCategory.ACCOUNT, new Recipient(true, false, true), Map.of(), true,
                false)).isEmpty();
    }

    @Test
    void directEmailGoesOnlyToEmail() {
        assertThat(ChannelResolver.resolve(NotificationCategory.ACCOUNT, new Recipient(false, false, false), Map.of(), true,
                true)).containsExactly(NotificationChannel.EMAIL);
    }

    @Test
    void inactiveRecipientGetsNothingOutsideAccount() {
        assertThat(ChannelResolver.resolve(NotificationCategory.DEADLINE, new Recipient(false, true, true), Map.of(), true,
                false)).isEmpty();
    }

    @Test
    void configurableCategoriesExcludeAccount() {
        assertThat(PreferenceDefaults.configurable(NotificationCategory.ACCOUNT)).isFalse();
        assertThat(PreferenceDefaults.configurable(NotificationCategory.DEADLINE)).isTrue();
    }

    private static Set<NotificationChannel> resolve(NotificationCategory category, Recipient recipient) {
        return ChannelResolver.resolve(category, recipient, Map.of(), false, false);
    }
}
