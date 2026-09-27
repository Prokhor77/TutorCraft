package com.tutorcraft.core.support;

import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.security.JwtService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

/**
 * База интеграционных тестов: реальные Postgres/Mongo/Redis/Kafka в Testcontainers (singleton на JVM).
 * Предоставляет MockMvc и помощники создания tenant/пользователей/токенов.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    private static final int REDIS_PORT = 6379;

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(REDIS_PORT);
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.0");

    static {
        POSTGRES.start();
        MONGO.start();
        REDIS.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.mongodb.uri", () -> MONGO.getReplicaSetUrl("tutorcraft"));
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
        registry.add("spring.data.redis.password", () -> "");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected JwtService jwt;

    protected UUID createTenant(String slug) {
        UUID id = Ids.newId();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.sql("INSERT INTO tenants (id, slug, name, created_at, updated_at) VALUES (:id, :slug, :slug, :now, :now)")
            .param("id", id).param("slug", slug + "-" + id.toString().substring(0, 8)).param("now", now).update();
        return id;
    }

    protected UUID createUser(UUID tenantId, String email) {
        UUID id = Ids.newId();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.sql("""
                INSERT INTO users (id, tenant_id, email, first_name, last_name, created_at, updated_at)
                VALUES (:id, :tenantId, :email, 'Test', 'User', :now, :now)
                """)
            .param("id", id).param("tenantId", tenantId).param("email", email).param("now", now).update();
        return id;
    }

    protected void grantTenantRole(UUID tenantId, UUID userId, String roleKey) {
        jdbc.sql("""
                INSERT INTO role_assignments (id, tenant_id, user_id, role_id, context_type, created_at)
                SELECT :id, :tenantId, :userId, r.id, 'tenant', now() FROM roles r WHERE r.key = :key AND r.tenant_id IS NULL
                """)
            .param("id", Ids.newId()).param("tenantId", tenantId).param("userId", userId).param("key", roleKey).update();
    }

    protected String bearer(UUID tenantId, UUID userId) {
        return "Bearer " + jwt.issueAccessToken(userId, tenantId, List.of()).value();
    }
}
