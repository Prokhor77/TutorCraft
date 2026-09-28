package com.tutorcraft.core.shared.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Вся конфигурация приложения. Невалидная конфигурация останавливает старт (ARCH-05).
 */
@Validated
@ConfigurationProperties(prefix = "tutorcraft")
public record AppProperties(
        @NotBlank String webOrigin,
        @NotBlank String publicBaseUrl,
        @Valid @NotNull Security security,
        @Valid @NotNull OAuth oauth,
        @Valid @NotNull Storage storage,
        @Valid @NotNull Kafka kafka,
        @Valid @NotNull RateLimit rateLimit,
        @Valid @NotNull Payments payments,
        @Valid @NotNull Quiz quiz,
        @Valid @NotNull Trash trash,
        @Valid @NotNull Seed seed,
        @Valid @NotNull Admin admin) {

    private static final int MIN_SECRET_LENGTH = 32;

    public record Security(
            @NotBlank @Size(min = MIN_SECRET_LENGTH, message = "JWT_SECRET must be at least 32 characters") String jwtSecret,
            @NotNull Duration accessTokenTtl,
            @NotNull Duration refreshTokenTtl,
            boolean cookieSecure,
            @NotBlank String dataEncryptionKey,
            @Min(1) int loginMaxAttemptsPerAccount,
            @Min(1) int loginMaxAttemptsPerIp,
            @NotNull Duration loginWindow,
            @NotNull Duration passwordResetTtl,
            @NotNull Duration invitationTtl) {
    }

    public record OAuth(String googleClientId, String telegramBotToken, String telegramBotUsername,
                        @NotNull Duration telegramAuthMaxAge) {

        public boolean googleEnabled() {
            return googleClientId != null && !googleClientId.isBlank();
        }

        public boolean telegramEnabled() {
            return telegramBotToken != null && !telegramBotToken.isBlank();
        }
    }

    public record Storage(@NotBlank String endpoint, @NotBlank String publicEndpoint, @NotBlank String region,
                          @NotBlank String bucket, @NotBlank String accessKey, @NotBlank String secretKey,
                          @NotNull Duration uploadUrlTtl, @NotNull Duration downloadUrlTtl,
                          @Min(1) long maxFileSizeBytes) {
    }

    public record Kafka(@NotBlank String topicPrefix, @Min(1) int outboxBatchSize, @NotNull Duration outboxPollInterval) {
    }

    public record RateLimit(@Min(1) int requestsPerMinuteUser, @Min(1) int requestsPerMinuteIp) {
    }

    public record Payments(@NotBlank String provider, String yookassaShopId, String yookassaSecretKey,
                           String stripeSecretKey, String stripeWebhookSecret) {
    }

    public record Quiz(@NotNull Duration timeGrace) {
    }

    public record Trash(@NotNull Duration retention) {
    }

    public record Seed(boolean enabled, String demoPassword) {
    }

    /**
     * Главный администратор платформы (ADMIN_EMAIL / ADMIN_PASSWORD). Единственная учётная запись с доступом
     * к администрированию; создаётся или обновляется при старте. Пусто — админ-аккаунт не создаётся.
     */
    public record Admin(String email, String password, String firstName, String lastName) {

        public boolean configured() {
            return email != null && !email.isBlank() && password != null && !password.isBlank();
        }

        @Override
        public String toString() {
            return "Admin[email=" + email + ", password=***]";
        }
    }
}
