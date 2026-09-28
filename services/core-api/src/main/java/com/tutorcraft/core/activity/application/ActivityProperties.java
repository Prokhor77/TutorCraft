package com.tutorcraft.core.activity.application;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Настройки журнала активности ({@code tutorcraft.activity-log.*}).
 *
 * @param enabled        писать журнал (false — фильтр и приём клиентских событий ничего не сохраняют)
 * @param logReads       писать успешные GET; при false в журнал попадают только изменения и неудачные запросы
 * @param retention      срок хранения записей; старые удаляются ежедневным заданием
 * @param queueCapacity  размер буфера до записи в БД; при переполнении записи отбрасываются (метрика dropped)
 * @param batchSize      записей в одном batch insert
 * @param maxStackLength предел длины сохраняемого стека ошибки
 * @param trailBefore    окно «что было до ошибки» для трассировки
 * @param trailAfter     окно «что было после ошибки»
 * @param trailLimit     максимум событий в трассировке
 */
@Validated
@ConfigurationProperties(prefix = "tutorcraft.activity-log")
public record ActivityProperties(
        boolean enabled,
        boolean logReads,
        @NotNull Duration retention,
        @Min(100) int queueCapacity,
        @Min(1) int batchSize,
        @Min(500) int maxStackLength,
        @NotNull Duration trailBefore,
        @NotNull Duration trailAfter,
        @Min(1) int trailLimit) {
}
