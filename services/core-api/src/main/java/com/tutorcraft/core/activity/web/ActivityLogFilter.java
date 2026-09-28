package com.tutorcraft.core.activity.web;

import com.tutorcraft.core.activity.application.ActivityProperties;
import com.tutorcraft.core.activity.application.ActivitySink;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.web.RequestCorrelation;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Журнал активности (FR-REPORT-02, NFR-OBS-01): каждый запрос к API — кто, что, откуда, чем закончился.
 * Стоит снаружи Spring Security, поэтому видит и отклонённые запросы (401/403/429). Назначает запросу
 * {@code X-Request-Id}: он попадает в MDC логов, заголовок ответа и Problem ({@code requestId}), так что по коду
 * из сообщения об ошибке администратор находит запись и всю цепочку действий пользователя.
 */
@Component
class ActivityLogFilter extends OncePerRequestFilter implements Ordered {

    static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10;
    private static final Logger log = LoggerFactory.getLogger(ActivityLogFilter.class);
    private static final String API_PREFIX = "/api/";
    private static final int SERVER_ERROR_STATUS = 500;
    private static final int CLIENT_ERROR_STATUS = 400;
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD");
    private static final String OPTIONS_METHOD = "OPTIONS";
    /** Чтение самого журнала и приём клиентских событий не журналируются (иначе журнал пишет сам себя). */
    private static final List<String> SELF_PATHS = List.of("/api/v1/activity-log", "/api/v1/activity/events");

    private final RequestActivityMapper mapper;
    private final ActivitySink sink;
    private final ActivityProperties properties;

    ActivityLogFilter(RequestActivityMapper mapper, ActivitySink sink, ActivityProperties properties) {
        this.mapper = mapper;
        this.sink = sink;
        this.properties = properties;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(API_PREFIX) || OPTIONS_METHOD.equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = Ids.newId().toString();
        MDC.put(RequestCorrelation.REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(RequestCorrelation.REQUEST_ID_HEADER, requestId);
        long started = System.nanoTime();
        Throwable failure = null;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            failure = e;
            throw e;
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            int status = failure == null ? response.getStatus() : SERVER_ERROR_STATUS;
            record(request, new RequestActivityMapper.Completion(requestId, status, durationMs, failure));
            MDC.remove(RequestCorrelation.REQUEST_ID_MDC_KEY);
        }
    }

    private void record(HttpServletRequest request, RequestActivityMapper.Completion completion) {
        if (!properties.enabled() || skipped(request, completion.status())) {
            return;
        }
        try {
            sink.submit(mapper.toEntry(request, completion));
        } catch (RuntimeException e) {
            log.warn("Activity entry skipped: {}", e.getClass().getSimpleName());
        }
    }

    /** Неудачные запросы пишутся всегда; успешные чтения — если включено logReads. */
    private boolean skipped(HttpServletRequest request, int status) {
        if (status >= CLIENT_ERROR_STATUS) {
            return false;
        }
        String uri = request.getRequestURI();
        if (SELF_PATHS.stream().anyMatch(uri::startsWith)) {
            return true;
        }
        return !properties.logReads() && READ_METHODS.contains(request.getMethod());
    }
}
