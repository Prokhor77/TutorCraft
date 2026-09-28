package com.tutorcraft.core.integrations.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.integrations.application.ApiTokenRepository.ApiTokenSummary;
import com.tutorcraft.core.integrations.application.ApiTokenRepository.NewApiToken;
import com.tutorcraft.core.integrations.domain.ApiTokenFormat;
import com.tutorcraft.core.integrations.domain.ApiTokenScope;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.domain.ValidationException;
import com.tutorcraft.core.shared.domain.Validator;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import com.tutorcraft.core.shared.security.TokenHasher;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Управление personal access tokens (FR-INTEG-01). Токен действует от имени создавшего пользователя. */
@Service
public class ApiTokenService {

    private static final int MAX_NAME_LENGTH = 100;

    private final ApiTokenRepository tokens;
    private final AccessService access;
    private final CurrentUserProvider currentUser;
    private final AuditLog audit;
    private final Clock clock;

    public ApiTokenService(ApiTokenRepository tokens, AccessService access, CurrentUserProvider currentUser, AuditLog audit,
                           Clock clock) {
        this.tokens = tokens;
        this.access = access;
        this.currentUser = currentUser;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ApiTokenSummary> list() {
        access.require(Permission.INTEGRATION_MANAGE, AccessContext.tenant());
        return tokens.list(currentUser.require().tenantId());
    }

    /** @return токен в открытом виде — показывается один раз (NFR-SEC-07) */
    @Transactional
    public CreatedToken create(CreateTokenCommand command) {
        CurrentUser user = currentUser.require();
        access.require(Permission.INTEGRATION_MANAGE, AccessContext.tenant());
        Instant now = clock.instant();
        Set<ApiTokenScope> scopes = parseScopes(command.scopes());
        validate(command, now);
        String token = ApiTokenFormat.of(TokenHasher.newToken());
        UUID id = Ids.newId();
        tokens.insert(new NewApiToken(id, user.tenantId(), user.userId(), command.name().trim(), TokenHasher.sha256(token),
                scopes, command.expiresAt(), now));
        audit.record(AuditRecord.of(user.tenantId(), user.userId(), "integration.token_created", "api_token", id.toString())
                .withDiff(Map.of("scopes", scopes.stream().map(ApiTokenScope::key).sorted().toList())));
        return new CreatedToken(id, token);
    }

    @Transactional
    public void revoke(UUID tokenId) {
        CurrentUser user = currentUser.require();
        access.require(Permission.INTEGRATION_MANAGE, AccessContext.tenant());
        if (!tokens.revoke(user.tenantId(), tokenId, clock.instant())) {
            throw new NotFoundException(IntegrationErrors.TOKEN_NOT_FOUND, "API token not found");
        }
        audit.record(AuditRecord.of(user.tenantId(), user.userId(), "integration.token_revoked", "api_token", tokenId.toString()));
    }

    private static Set<ApiTokenScope> parseScopes(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            throw ValidationException.single("scopes", "required", "At least one scope is required");
        }
        return keys.stream()
                .map(key -> ApiTokenScope.find(key)
                        .orElseThrow(() -> ValidationException.single("scopes", "invalid", "Allowed scopes: read, write")))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ApiTokenScope.class)));
    }

    private static void validate(CreateTokenCommand command, Instant now) {
        new Validator()
            .notBlank(command.name(), "name")
            .maxLength(command.name(), MAX_NAME_LENGTH, "name")
            .check(command.expiresAt() == null || command.expiresAt().isAfter(now), "expiresAt", "in_past", "Must be in the future")
            .throwIfInvalid();
    }

    public record CreateTokenCommand(String name, List<String> scopes, Instant expiresAt) {
    }

    public record CreatedToken(UUID id, String token) {

        @Override
        public String toString() {
            return "CreatedToken[id=" + id + "]";
        }
    }
}
