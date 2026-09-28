package com.tutorcraft.core.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Сквозная корреляция запроса (NFR-OBS-01): идентификатор запроса и сведения, которые слои внутри запроса
 * передают внешнему фильтру журнала активности через атрибуты запроса (кто выполнил, чем закончился).
 * Атрибуты нужны потому, что SecurityContext и обработчик ошибок живут внутри цепочки, а журнал пишется снаружи.
 */
public final class RequestCorrelation {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String CLIENT_PAGE_HEADER = "X-Client-Page";
    public static final String CLIENT_SESSION_HEADER = "X-Client-Session";
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    private static final String PREFIX = RequestCorrelation.class.getName() + ".";
    private static final String ACTOR_ATTRIBUTE = PREFIX + "actor";
    private static final String ERROR_CODE_ATTRIBUTE = PREFIX + "errorCode";
    private static final String ERROR_ATTRIBUTE = PREFIX + "error";

    private RequestCorrelation() {
    }

    /** Кто выполнил запрос (effective tenant — школа, в которой действует пользователь). */
    public record Actor(UUID userId, UUID tenantId) {
    }

    public static Optional<String> currentRequestId() {
        return Optional.ofNullable(MDC.get(REQUEST_ID_MDC_KEY));
    }

    public static void rememberActor(HttpServletRequest request, UUID userId, UUID tenantId) {
        request.setAttribute(ACTOR_ATTRIBUTE, new Actor(userId, tenantId));
    }

    /** Для входа/регистрации: до выдачи токена пользователь неизвестен фильтру безопасности. */
    public static void rememberActor(UUID userId, UUID tenantId) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            rememberActor(attributes.getRequest(), userId, tenantId);
        }
    }

    public static Optional<Actor> actor(HttpServletRequest request) {
        return request.getAttribute(ACTOR_ATTRIBUTE) instanceof Actor actor ? Optional.of(actor) : Optional.empty();
    }

    /** Код ошибки ответа и (для непредвиденных ошибок) исключение — для журнала активности. */
    public static void rememberError(HttpServletRequest request, String code, Throwable error) {
        request.setAttribute(ERROR_CODE_ATTRIBUTE, code);
        if (error != null) {
            request.setAttribute(ERROR_ATTRIBUTE, error);
        }
    }

    /** Вариант для кода без доступа к запросу (ProblemFactory, @ExceptionHandler). Вне HTTP-запроса — no-op. */
    public static void rememberError(String code, Throwable error) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            rememberError(attributes.getRequest(), code, error);
        }
    }

    public static Optional<String> errorCode(HttpServletRequest request) {
        return request.getAttribute(ERROR_CODE_ATTRIBUTE) instanceof String code ? Optional.of(code) : Optional.empty();
    }

    public static Optional<Throwable> error(HttpServletRequest request) {
        return request.getAttribute(ERROR_ATTRIBUTE) instanceof Throwable error ? Optional.of(error) : Optional.empty();
    }
}
