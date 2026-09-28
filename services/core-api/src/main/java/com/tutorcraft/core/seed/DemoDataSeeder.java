package com.tutorcraft.core.seed;

import com.tutorcraft.core.org.OrgApi;
import com.tutorcraft.core.seed.DemoAccounts.Accounts;
import com.tutorcraft.core.shared.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Демо-данные для локального запуска (SPEC §14, README): tenant {@code demo}, преподаватель {@code teacher@demo.local}
 * (tenant_admin), {@value DemoAccounts#STUDENT_COUNT} студентов и демо-курс. Только профиль {@code dev} и
 * {@code tutorcraft.seed.enabled=true}; пароль — {@code tutorcraft.seed.demo-password} (из env, не хранится в коде).
 * Выполняется после синхронизации системных ролей и пропускается, если tenant {@code demo} уже есть.
 * Всё создаётся через публичные API и use case-сервисы модулей (те же проверки, что у REST), в одной транзакции
 * PostgreSQL: при ошибке сид повторится при следующем старте.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "tutorcraft.seed.enabled", havingValue = "true")
class DemoDataSeeder {

    static final String DEMO_TENANT_SLUG = "demo";
    /** После SystemRolesInitializer (@Order(0)): роли нужны для назначения tenant_admin. */
    private static final int AFTER_SYSTEM_ROLES = 10;
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final OrgApi org;
    private final DemoAccounts accounts;
    private final DemoCourse course;
    private final SeedAuthentication authentication;
    private final TransactionTemplate transaction;
    private final String password;

    DemoDataSeeder(OrgApi org, DemoAccounts accounts, DemoCourse course, SeedAuthentication authentication,
                   PlatformTransactionManager transactionManager, AppProperties properties) {
        this.org = org;
        this.accounts = accounts;
        this.course = course;
        this.authentication = authentication;
        this.transaction = new TransactionTemplate(transactionManager);
        this.password = properties.seed().demoPassword();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(AFTER_SYSTEM_ROLES)
    public void seed() {
        if (password == null || password.isBlank()) {
            log.warn("Demo data is not seeded: tutorcraft.seed.demo-password (SEED_DEMO_PASSWORD) is empty");
            return;
        }
        if (org.findBySlug(DEMO_TENANT_SLUG).isPresent()) {
            log.info("Demo data already present (tenant '{}'), seeding skipped", DEMO_TENANT_SLUG);
            return;
        }
        transaction.executeWithoutResult(status -> seedAll());
    }

    private void seedAll() {
        Accounts created = accounts.create(DEMO_TENANT_SLUG, password);
        authentication.runAs(created.tenantId(), created.teacherId(), () -> course.create(created));
        log.info("Demo data seeded: tenant {}, teacher {}, {} students", created.tenantId(), created.teacherId(),
                created.studentIds().size());
    }
}
