package com.tutorcraft.core.files.infrastructure;

import com.tutorcraft.core.shared.config.AppProperties;
import java.net.URI;
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
 */
@Configuration
class S3StorageConfig {

    @Bean
    S3Client s3Client(AppProperties properties) {
        AppProperties.Storage storage = properties.storage();
        return S3Client.builder()
                .endpointOverride(URI.create(storage.endpoint()))
                .region(Region.of(storage.region()))
                .credentialsProvider(credentials(storage))
                .serviceConfiguration(pathStyle())
                .build();
    }

    @Bean
    S3Presigner s3Presigner(AppProperties properties) {
        AppProperties.Storage storage = properties.storage();
        return S3Presigner.builder()
                .endpointOverride(URI.create(storage.publicEndpoint()))
                .region(Region.of(storage.region()))
                .credentialsProvider(credentials(storage))
                .serviceConfiguration(pathStyle())
                .build();
    }

    private static StaticCredentialsProvider credentials(AppProperties.Storage storage) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(storage.accessKey(), storage.secretKey()));
    }

    private static S3Configuration pathStyle() {
        return S3Configuration.builder().pathStyleAccessEnabled(true).build();
    }
}
