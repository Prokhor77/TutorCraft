package com.tutorcraft.core.files.infrastructure;

import com.tutorcraft.core.shared.config.AppProperties;
import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3/MinIO: клиент для серверных операций (внутренний endpoint) и presigner для браузерных URL
 * (публичный endpoint — подпись включает host). Path-style обязателен для MinIO. Ключи — только из env.
 * Активно только при {@code tutorcraft.storage.driver=s3}.
 */
@Configuration
@ConditionalOnProperty(prefix = "tutorcraft.storage", name = "driver", havingValue = "s3")
class S3StorageConfig {

    @Bean
    S3Client s3Client(AppProperties properties) {
        AppProperties.Storage storage = requireComplete(properties.storage());
        return S3Client.builder()
                .endpointOverride(URI.create(storage.endpoint()))
                .region(Region.of(storage.region()))
                .credentialsProvider(credentials(storage))
                .serviceConfiguration(pathStyle())
                .build();
    }

    @Bean
    S3Presigner s3Presigner(AppProperties properties) {
        AppProperties.Storage storage = requireComplete(properties.storage());
        return S3Presigner.builder()
                .endpointOverride(URI.create(storage.publicEndpoint()))
                .region(Region.of(storage.region()))
                .credentialsProvider(credentials(storage))
                .serviceConfiguration(pathStyle())
                .build();
    }

    /** ARCH-05: неполная конфигурация S3 останавливает старт (значения секретов в сообщение не попадают). */
    private static AppProperties.Storage requireComplete(AppProperties.Storage storage) {
        Map<String, String> required = Map.of(
                "S3_ENDPOINT", nullToEmpty(storage.endpoint()),
                "S3_PUBLIC_ENDPOINT", nullToEmpty(storage.publicEndpoint()),
                "S3_REGION", nullToEmpty(storage.region()),
                "S3_BUCKET", nullToEmpty(storage.bucket()),
                "S3_ACCESS_KEY", nullToEmpty(storage.accessKey()),
                "S3_SECRET_KEY", nullToEmpty(storage.secretKey()));
        String missing = required.entrySet().stream()
                .filter(entry -> entry.getValue().isBlank())
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.joining(", "));
        if (!missing.isEmpty()) {
            throw new IllegalStateException("STORAGE_DRIVER=s3 requires: " + missing);
        }
        return storage;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static StaticCredentialsProvider credentials(AppProperties.Storage storage) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(storage.accessKey(), storage.secretKey()));
    }

    private static S3Configuration pathStyle() {
        return S3Configuration.builder().pathStyleAccessEnabled(true).build();
    }
}
