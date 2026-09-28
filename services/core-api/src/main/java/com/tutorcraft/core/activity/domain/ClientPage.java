package com.tutorcraft.core.activity.domain;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Страница интерфейса и идентификатор вкладки, которые сообщает веб-клиент. Значения приходят от клиента,
 * поэтому строго проверяются: только путь без query/fragment (в query бывают токены сброса пароля).
 */
public final class ClientPage {

    private static final int MAX_PAGE_LENGTH = 300;
    private static final Pattern PAGE = Pattern.compile("/[A-Za-z0-9/_.~:%@!$'()*+,;=-]*");
    private static final Pattern SESSION = Pattern.compile("[A-Za-z0-9-]{8,64}");
    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9-]{8,64}");

    private ClientPage() {
    }

    public static Optional<String> page(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String path = stripQueryAndFragment(raw.trim());
        if (path.length() > MAX_PAGE_LENGTH || !PAGE.matcher(path).matches()) {
            return Optional.empty();
        }
        return Optional.of(path);
    }

    public static Optional<String> sessionId(String raw) {
        return raw != null && SESSION.matcher(raw).matches() ? Optional.of(raw) : Optional.empty();
    }

    public static Optional<String> requestId(String raw) {
        return raw != null && REQUEST_ID.matcher(raw).matches() ? Optional.of(raw) : Optional.empty();
    }

    private static String stripQueryAndFragment(String value) {
        int cut = value.length();
        int query = value.indexOf('?');
        int fragment = value.indexOf('#');
        if (query >= 0) {
            cut = Math.min(cut, query);
        }
        if (fragment >= 0) {
            cut = Math.min(cut, fragment);
        }
        return value.substring(0, cut);
    }
}
