package com.tutorcraft.core.integrations.infrastructure;

import com.tutorcraft.core.integrations.application.WebhookSender;
import com.tutorcraft.core.integrations.domain.WebhookSignature;
import java.net.URI;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * HTTP POST вебхука через RestClient: таймауты 10 с, без следования редиректам для POST
 * (SimpleClientHttpRequestFactory следует им только для GET) — редирект не обходит SSRF-проверку.
 */
@Component
class RestClientWebhookSender implements WebhookSender {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String USER_AGENT = "TutorCraft-Webhooks/1";

    private final RestClient restClient;

    RestClientWebhookSender() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) TIMEOUT.toMillis());
        factory.setReadTimeout((int) TIMEOUT.toMillis());
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public Result send(URI url, String body, String signature) {
        try {
            Integer status = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(WebhookSignature.HEADER, signature)
                    .header(HttpHeaders.USER_AGENT, USER_AGENT)
                    .body(body)
                    .exchange((request, response) -> response.getStatusCode().value());
            return new Result(status, null);
        } catch (RestClientException e) {
            return new Result(null, e.getClass().getSimpleName());
        }
    }
}
