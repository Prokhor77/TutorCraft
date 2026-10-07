package com.tutorcraft.core.org.application;

import java.util.Map;
import java.util.UUID;

/**
 * Физическое удаление всех данных школы (tenant) при её удалении главным администратором. В отличие от
 * {@code identity.spi.UserDataEraser}, не обходит модули по одному: школа исчезает целиком, поэтому удаляются все
 * строки с её {@code tenant_id} в каждой таблице и все документы с её {@code tenantId} в каждой коллекции — новые
 * таблицы и коллекции охватываются без доработок. Журнал аудита (append-only) сохраняется.
 */
public interface TenantDataPurge {

    /**
     * Удаляет строки tenant в PostgreSQL в порядке внешних ключей, затем саму строку tenants. Ссылки строк других
     * школ на удаляемые строки (nullable-колонки) обнуляются. Вызывается в транзакции вызывающего кода.
     *
     * @return число удалённых строк по таблицам, в порядке удаления
     */
    Map<String, Integer> purgeRelational(UUID tenantId);

    /** Удаляет документы tenant во всех коллекциях MongoDB. Транзакций нет — операция идемпотентна. */
    long purgeDocuments(UUID tenantId);
}
