package com.tutorcraft.core.integrations.web;

import com.tutorcraft.core.integrations.application.ApiTokenRepository.ApiTokenSummary;
import com.tutorcraft.core.integrations.application.ApiTokenService;
import com.tutorcraft.core.integrations.application.ApiTokenService.CreateTokenCommand;
import com.tutorcraft.core.integrations.application.ApiTokenService.CreatedToken;
import com.tutorcraft.core.integrations.application.WebhookDeliveryRepository.DeliveryView;
import com.tutorcraft.core.integrations.application.WebhookRepository.WebhookSummary;
import com.tutorcraft.core.integrations.application.WebhookService;
import com.tutorcraft.core.integrations.application.WebhookService.CreatedWebhook;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API-токены и вебхуки (контракт §14). */
@RestController
@RequestMapping("/api/v1")
class IntegrationsController {

    private static final int MAX_NAME = 100;
    private static final int MAX_URL = 2048;
    private static final int MAX_LIST = 10;

    private final ApiTokenService tokens;
    private final WebhookService webhooks;

    IntegrationsController(ApiTokenService tokens, WebhookService webhooks) {
        this.tokens = tokens;
        this.webhooks = webhooks;
    }

    @GetMapping("/tokens")
    List<ApiTokenSummary> listTokens() {
        return tokens.list();
    }

    @PostMapping("/tokens")
    @ResponseStatus(HttpStatus.CREATED)
    CreatedToken createToken(@Valid @RequestBody CreateTokenRequest request) {
        return tokens.create(new CreateTokenCommand(request.name(), request.scopes(), request.expiresAt()));
    }

    @DeleteMapping("/tokens/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeToken(@PathVariable UUID id) {
        tokens.revoke(id);
    }

    @GetMapping("/webhooks")
    List<WebhookSummary> listWebhooks() {
        return webhooks.list();
    }

    @PostMapping("/webhooks")
    @ResponseStatus(HttpStatus.CREATED)
    CreatedWebhook createWebhook(@Valid @RequestBody CreateWebhookRequest request) {
        return webhooks.create(request.url(), request.events());
    }

    @DeleteMapping("/webhooks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteWebhook(@PathVariable UUID id) {
        webhooks.delete(id);
    }

    @GetMapping("/webhooks/{id}/deliveries")
    PageResponse<DeliveryView> deliveries(@PathVariable UUID id, @RequestParam(required = false) String cursor,
                                          @RequestParam(required = false) Integer limit) {
        return webhooks.deliveries(id, PageQuery.of(cursor, limit));
    }

    record CreateTokenRequest(@NotBlank @Size(max = MAX_NAME) String name, @NotEmpty @Size(max = MAX_LIST) List<String> scopes,
                              Instant expiresAt) {
    }

    record CreateWebhookRequest(@NotBlank @Size(max = MAX_URL) String url, @NotEmpty @Size(max = MAX_LIST) List<String> events) {
    }
}
