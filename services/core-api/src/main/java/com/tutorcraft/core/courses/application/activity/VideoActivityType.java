package com.tutorcraft.core.courses.application.activity;

import com.tutorcraft.core.courses.ItemType;
import com.tutorcraft.core.courses.application.EmbedWhitelistProvider;
import com.tutorcraft.core.courses.spi.ActivityType;
import com.tutorcraft.core.shared.content.UrlPolicy;
import com.tutorcraft.core.shared.domain.Validator;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Видео (FR-CONTENT-03, ADR-008): загруженный файл (HLS после транскодирования) или embed с домена из белого списка
 * tenant. videoStatus/hlsUrl вычисляются при чтении и не хранятся.
 */
@Component
class VideoActivityType implements ActivityType {

    private static final String EMBED_URL = "embedUrl";

    private final EmbedWhitelistProvider whitelist;
    private final CurrentUserProvider currentUser;

    VideoActivityType(EmbedWhitelistProvider whitelist, CurrentUserProvider currentUser) {
        this.whitelist = whitelist;
        this.currentUser = currentUser;
    }

    @Override
    public ItemType type() {
        return ItemType.VIDEO;
    }

    @Override
    public Map<String, Object> defaults() {
        return settings(null, null);
    }

    @Override
    public Map<String, Object> validate(Map<String, Object> settings) {
        Validator validator = new Validator();
        String fileId = SettingsFields.optionalUuid(settings, SettingsFields.FILE_ID, validator);
        String embedUrl = SettingsFields.optionalString(settings, EMBED_URL, UrlPolicy.MAX_URL_LENGTH, validator);
        validator.check(fileId == null || embedUrl == null, SettingsFields.path(EMBED_URL), "file_or_embed",
                "Use either an uploaded file or an embed link");
        validator.check(embedUrl == null || UrlPolicy.isAllowedEmbed(embedUrl, tenantWhitelist()),
                SettingsFields.path(EMBED_URL), "embed_not_allowed", "Embedding is allowed only from whitelisted domains");
        validator.throwIfInvalid();
        return settings(fileId, embedUrl);
    }

    @Override
    public Set<UUID> referencedFileIds(Map<String, Object> settings) {
        return SettingsFields.toUuids(settings.get(SettingsFields.FILE_ID));
    }

    private Set<String> tenantWhitelist() {
        return currentUser.find().map(user -> whitelist.embedWhitelist(user.tenantId())).orElse(Set.of());
    }

    private static Map<String, Object> settings(String fileId, String embedUrl) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(SettingsFields.KIND, ItemType.VIDEO.key());
        result.put(SettingsFields.FILE_ID, fileId);
        result.put(EMBED_URL, embedUrl);
        return result;
    }
}
