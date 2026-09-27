package com.tutorcraft.core.shared.idempotency;

import com.tutorcraft.core.shared.domain.BusinessRuleException;
import com.tutorcraft.core.shared.domain.ConflictException;
import com.tutorcraft.core.shared.domain.ValidationException;
import com.tutorcraft.core.shared.persistence.JsonCodec;
import com.tutorcraft.core.shared.security.TokenHasher;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Идемпотентность небезопасных операций (API-05, NFR-REL-05, AC-3).
 * Ключ резервируется в той же транзакции, что и операция: при ошибке транзакция откатывается,
 * и клиент может повторить запрос. Параллельный запрос с тем же ключом ждёт блокировку строки.
 */
@Component
public class IdempotencyService {

    private static final int MAX_KEY_LENGTH = 128;

    private final JdbcClient jdbc;
    private final JsonCodec json;
    private final Clock clock;

    public IdempotencyService(JdbcClient jdbc, JsonCodec json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public <T> T execute(IdempotencyScope scope, String key, Object request, Class<T> responseType, Supplier<T> action) {
        validateKey(key);
        String requestHash = TokenHasher.sha256(json.write(request));
        if (reserve(scope, key, requestHash)) {
            T response = action.get();
            storeResponse(scope, key, response);
            return response;
        }
        return replay(scope, key, requestHash, responseType);
    }

    private boolean reserve(IdempotencyScope scope, String key, String requestHash) {
        return jdbc.sql("""
                INSERT INTO idempotency_keys (tenant_id, user_id, operation, idem_key, request_hash, created_at)
                VALUES (:tenantId, :userId, :operation, :key, :hash, :now)
                ON CONFLICT DO NOTHING
                """)
            .param("tenantId", scope.tenantId()).param("userId", scope.userId())
            .param("operation", scope.operation()).param("key", key).param("hash", requestHash)
            .param("now", java.sql.Timestamp.from(clock.instant()))
            .update() == 1;
    }

    private <T> T replay(IdempotencyScope scope, String key, String requestHash, Class<T> responseType) {
        StoredResponse stored = find(scope, key)
                .orElseThrow(() -> new ConflictException("idempotency.in_progress", "Request is still being processed"));
        if (!stored.requestHash().equals(requestHash)) {
            throw new BusinessRuleException("idempotency.mismatch", "Idempotency-Key reused with a different request");
        }
        if (stored.responseJson() == null) {
            throw new ConflictException("idempotency.in_progress", "Request is still being processed");
        }
        return json.read(stored.responseJson(), responseType);
    }

    private Optional<StoredResponse> find(IdempotencyScope scope, String key) {
        return jdbc.sql("""
                SELECT request_hash, response::text AS response FROM idempotency_keys
                WHERE tenant_id = :tenantId AND user_id = :userId AND operation = :operation AND idem_key = :key
                """)
            .param("tenantId", scope.tenantId()).param("userId", scope.userId())
            .param("operation", scope.operation()).param("key", key)
            .query((rs, n) -> new StoredResponse(rs.getString("request_hash"), rs.getString("response")))
            .optional();
    }

    private void storeResponse(IdempotencyScope scope, String key, Object response) {
        jdbc.sql("""
                UPDATE idempotency_keys SET response = :response
                WHERE tenant_id = :tenantId AND user_id = :userId AND operation = :operation AND idem_key = :key
                """)
            .param("response", json.toJsonb(response))
            .param("tenantId", scope.tenantId()).param("userId", scope.userId())
            .param("operation", scope.operation()).param("key", key)
            .update();
    }

    private static void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw ValidationException.single("Idempotency-Key", "required", "Idempotency-Key header is required");
        }
    }

    private record StoredResponse(String requestHash, String responseJson) {
    }

    public record IdempotencyScope(UUID tenantId, UUID userId, String operation) {
    }
}
