package com.tutorcraft.core.courses.application.activity;

import com.tutorcraft.core.courses.ItemType;
import com.tutorcraft.core.courses.spi.ActivityType;
import com.tutorcraft.core.shared.content.UrlPolicy;
import com.tutorcraft.core.shared.domain.Validator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Ссылка (FR-CONTENT-03): только http/https (NFR-SEC-03); пустая строка — ссылка ещё не задана (UX-02). */
@Component
class UrlActivityType implements ActivityType {

    private static final String URL = "url";

    @Override
    public ItemType type() {
        return ItemType.URL;
    }

    @Override
    public Map<String, Object> defaults() {
        return settings("");
    }

    @Override
    public Map<String, Object> validate(Map<String, Object> settings) {
        Validator validator = new Validator();
        String url = SettingsFields.optionalString(settings, URL, UrlPolicy.MAX_URL_LENGTH, validator);
        validator.check(url == null || UrlPolicy.isWebUrl(url), SettingsFields.path(URL), "invalid_url",
                "Only http and https links are allowed");
        validator.throwIfInvalid();
        return settings(url == null ? "" : url);
    }

    private static Map<String, Object> settings(String url) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(SettingsFields.KIND, ItemType.URL.key());
        result.put(URL, url);
        return result;
    }
}
