package com.tutorcraft.core.access.infrastructure;

import com.tutorcraft.core.access.application.RoleRepository;
import com.tutorcraft.core.access.domain.SystemRole;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Синхронизирует системные роли из кода в БД при старте (код — источник истины). */
@Component
class SystemRolesInitializer {

    private static final Logger log = LoggerFactory.getLogger(SystemRolesInitializer.class);

    private final RoleRepository roles;

    SystemRolesInitializer(RoleRepository roles) {
        this.roles = roles;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(0)
    @Transactional
    public void sync() {
        Arrays.stream(SystemRole.values()).forEach(roles::syncSystemRole);
        log.info("System roles synchronized: {}", SystemRole.values().length);
    }
}
