package com.tutorcraft.core.identity.application;

import com.tutorcraft.core.identity.application.OneTimeTokenRepository.Kind;
import com.tutorcraft.core.identity.application.OneTimeTokenRepository.NewToken;
import com.tutorcraft.core.identity.application.OneTimeTokenRepository.OneTimeToken;
import com.tutorcraft.core.shared.domain.BusinessRuleException;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.security.TokenHasher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Выпуск и погашение одноразовых токенов. Новый токен аннулирует прежние неиспользованные того же вида. */
@Component
public class OneTimeTokens {

    private final OneTimeTokenRepository repository;
    private final Clock clock;

    public OneTimeTokens(OneTimeTokenRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public IssuedToken issue(Kind kind, UUID tenantId, UUID userId, Duration ttl) {
        Instant now = clock.instant();
        repository.invalidateOutstanding(kind, tenantId, userId, now);
        String raw = TokenHasher.newToken();
        UUID id = Ids.newId();
        repository.insert(kind, new NewToken(id, tenantId, userId, TokenHasher.sha256(raw), now, now.plus(ttl)));
        return new IssuedToken(id, raw);
    }

    public Optional<OneTimeToken> findValid(Kind kind, String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return repository.findValid(kind, TokenHasher.sha256(raw), clock.instant());
    }

    /** @throws BusinessRuleException {@code auth.token_invalid} для неизвестного, истёкшего или использованного токена */
    public OneTimeToken requireValid(Kind kind, String raw) {
        return findValid(kind, raw).orElseThrow(OneTimeTokens::invalid);
    }

    public boolean tryConsume(Kind kind, OneTimeToken token) {
        return repository.consume(kind, token.id(), clock.instant());
    }

    public void consume(Kind kind, OneTimeToken token) {
        if (!tryConsume(kind, token)) {
            throw invalid();
        }
    }

    private static BusinessRuleException invalid() {
        return new BusinessRuleException(IdentityErrors.TOKEN_INVALID, "Link is invalid or expired");
    }

    /** value показывается пользователю один раз (в ссылке), в БД — только хеш. */
    public record IssuedToken(UUID id, String value) {

        @Override
        public String toString() {
            return "IssuedToken[id=" + id + "]";
        }
    }
}
