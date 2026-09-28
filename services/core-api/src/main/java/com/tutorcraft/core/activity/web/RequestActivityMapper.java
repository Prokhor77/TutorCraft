package com.tutorcraft.core.activity.web;

import com.tutorcraft.core.activity.application.ActivityProperties;
import com.tutorcraft.core.activity.domain.ActivityEntry;
import com.tutorcraft.core.activity.domain.ActivityKind;
import com.tutorcraft.core.activity.domain.ClientPage;
import com.tutorcraft.core.activity.domain.ErrorDescriber;
import com.tutorcraft.core.activity.domain.PathParams;
import com.tutorcraft.core.activity.domain.SensitiveDataMasker;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.web.ClientIp;
import com.tutorcraft.core.shared.web.RequestCorrelation;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

/** Собирает запись журнала из завершённого HTTP-запроса. Тело и query-строка не читаются никогда. */
@Component
class RequestActivityMapper {

    static final int MAX_USER_AGENT_LENGTH = 300;
    static final int MAX_PATH_LENGTH = 500;
    private static final int CLIENT_ERROR_STATUS = 400;
    private static final int SERVER_ERROR_STATUS = 500;
    private static final String HTTP_CODE_PREFIX = "http.";

    private final ActivityProperties properties;
    private final Clock clock;

    RequestActivityMapper(ActivityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** Результат обработки запроса: итоговый статус, длительность и исключение, вылетевшее мимо MVC (если было). */
    record Completion(String requestId, int status, long durationMs, Throwable failure) {
    }

    ActivityEntry toEntry(HttpServletRequest request, Completion completion) {
        return new ActivityEntry(Ids.newId(), clock.instant(), ActivityKind.REQUEST, actorOf(request),
                correlationOf(request, completion.requestId()), httpOf(request, completion), errorOf(request, completion));
    }

    private static ActivityEntry.Actor actorOf(HttpServletRequest request) {
        Optional<RequestCorrelation.Actor> actor = RequestCorrelation.actor(request);
        return new ActivityEntry.Actor(actor.map(RequestCorrelation.Actor::tenantId).orElse(null),
                actor.map(RequestCorrelation.Actor::userId).orElse(null), ClientIp.of(request),
                SensitiveDataMasker.truncate(request.getHeader("User-Agent"), MAX_USER_AGENT_LENGTH));
    }

    private static ActivityEntry.Correlation correlationOf(HttpServletRequest request, String requestId) {
        return new ActivityEntry.Correlation(requestId,
                ClientPage.sessionId(request.getHeader(RequestCorrelation.CLIENT_SESSION_HEADER)).orElse(null),
                ClientPage.page(request.getHeader(RequestCorrelation.CLIENT_PAGE_HEADER)).orElse(null));
    }

    private static ActivityEntry.HttpDetails httpOf(HttpServletRequest request, Completion completion) {
        Map<String, String> variables = pathVariables(request);
        String path = PathParams.maskPath(SensitiveDataMasker.truncate(request.getRequestURI(), MAX_PATH_LENGTH), variables);
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return new ActivityEntry.HttpDetails(request.getMethod(), pattern == null ? null : pattern.toString(), path,
                PathParams.sanitize(variables), handlerOf(request), completion.status(), completion.durationMs());
    }

    private ActivityEntry.ErrorDetails errorOf(HttpServletRequest request, Completion completion) {
        if (completion.status() < CLIENT_ERROR_STATUS) {
            return null;
        }
        String code = RequestCorrelation.errorCode(request).orElse(HTTP_CODE_PREFIX + completion.status());
        if (completion.status() < SERVER_ERROR_STATUS) {
            return new ActivityEntry.ErrorDetails(code, null, null, null);
        }
        Throwable error = completion.failure() != null ? completion.failure() : RequestCorrelation.error(request).orElse(null);
        return ErrorDescriber.describe(code, error, properties.maxStackLength());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> pathVariables(HttpServletRequest request) {
        Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return variables instanceof Map<?, ?> map ? (Map<String, String>) map : Map.of();
    }

    /** {@code CourseController.create} — чтобы по записи сразу найти код, который её обработал. */
    private static String handlerOf(HttpServletRequest request) {
        Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (handler instanceof HandlerMethod method) {
            return method.getBeanType().getSimpleName() + "." + method.getMethod().getName();
        }
        return null;
    }
}
