package com.tutorcraft.core.files.infrastructure;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Бин активен при {@code tutorcraft.storage.driver=local} (значение по умолчанию). */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ConditionalOnProperty(prefix = "tutorcraft.storage", name = "driver", havingValue = "local", matchIfMissing = true)
@interface LocalStorageEnabled {
}
