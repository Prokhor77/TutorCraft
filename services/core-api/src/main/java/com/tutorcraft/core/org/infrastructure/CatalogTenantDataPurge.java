package com.tutorcraft.core.org.infrastructure;

import static com.mongodb.client.model.Filters.eq;

import com.tutorcraft.core.org.application.TenantDataPurge;
import com.tutorcraft.core.org.domain.DeletionOrder;
import com.tutorcraft.core.org.domain.DeletionOrder.Reference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Удаление школы по каталогу БД: таблицы с колонкой {@code tenant_id} и внешние ключи читаются из pg_catalog при
 * каждом удалении, поэтому таблицы, добавленные миграциями позже, удаляются без правок этого класса. Таблицы без
 * {@code tenant_id}, ссылающиеся на строки школы (payment_events → orders), очищаются через подзапрос.
 * Append-only таблицы (grade_history) удаляются под флагом {@code tutorcraft.retention_purge} — тот же механизм, что
 * у очистки по сроку и удаления пользователя; флаг действует только в транзакции и снимается сразу после удаления.
 */
@Component
class CatalogTenantDataPurge implements TenantDataPurge {

    private static final Logger log = LoggerFactory.getLogger(CatalogTenantDataPurge.class);

    static final String TENANTS_TABLE = "tenants";
    /** Append-only (DATA-03): записи об удалённой школе, в том числе о самом удалении, остаются. */
    static final Set<String> RETAINED_TABLES = Set.of("audit_log", "flyway_schema_history");

    private static final String TENANT_COLUMN = "tenant_id";
    private static final String PURGE_FLAG = "tutorcraft.retention_purge";
    private static final String MONGO_TENANT_FIELD = "tenantId";
    private static final String MONGO_SYSTEM_PREFIX = "system.";
    /** confdeltype: ON DELETE CASCADE / SET NULL — PostgreSQL сам обработает ссылку при удалении родителя. */
    private static final Set<String> SELF_HANDLED_DELETE_RULES = Set.of("c", "n");

    private final JdbcClient jdbc;
    private final MongoTemplate mongo;

    CatalogTenantDataPurge(JdbcClient jdbc, MongoTemplate mongo) {
        this.jdbc = jdbc;
        this.mongo = mongo;
    }

    @Override
    public Map<String, Integer> purgeRelational(UUID tenantId) {
        jdbc.sql("SELECT id FROM tenants WHERE id = :id FOR UPDATE").param("id", tenantId).query(UUID.class).optional();
        Set<String> tenantTables = new HashSet<>(tenantTables());
        tenantTables.removeAll(RETAINED_TABLES);
        List<ForeignKey> foreignKeys = foreignKeys();

        detachOtherTenants(tenantId, tenantTables, foreignKeys);

        Map<String, List<ForeignKey>> dependents = dependentTables(tenantTables, foreignKeys);
        Set<String> tables = new HashSet<>(tenantTables);
        tables.addAll(dependents.keySet());
        List<Reference> references = foreignKeys.stream()
                .map(key -> new Reference(key.child(), key.parent()))
                .toList();

        Map<String, Integer> deleted = new LinkedHashMap<>();
        setPurgeFlag("on");
        try {
            for (String table : DeletionOrder.of(tables, references)) {
                int rows = dependents.containsKey(table)
                        ? deleteDependent(tenantId, table, dependents.get(table))
                        : jdbc.sql("DELETE FROM " + quote(table) + " WHERE tenant_id = :tenantId")
                            .param("tenantId", tenantId).update();
                if (rows > 0) {
                    deleted.put(table, rows);
                }
            }
        } finally {
            setPurgeFlag("off");
        }
        deleted.put(TENANTS_TABLE, jdbc.sql("DELETE FROM tenants WHERE id = :id").param("id", tenantId).update());
        log.info("Tenant {} purged from PostgreSQL: {}", tenantId, deleted);
        return deleted;
    }

    @Override
    public long purgeDocuments(UUID tenantId) {
        long total = 0;
        for (String collection : mongo.getCollectionNames()) {
            if (collection.startsWith(MONGO_SYSTEM_PREFIX)) {
                continue;
            }
            total += mongo.getCollection(collection).deleteMany(eq(MONGO_TENANT_FIELD, tenantId)).getDeletedCount();
        }
        log.info("Tenant {} purged from MongoDB: {} documents", tenantId, total);
        return total;
    }

    /** Строки других школ, ссылающиеся на удаляемые (например, users.created_by), теряют ссылку, а не мешают удалению. */
    private void detachOtherTenants(UUID tenantId, Set<String> tenantTables, List<ForeignKey> foreignKeys) {
        for (ForeignKey key : foreignKeys) {
            boolean crossTenantCandidate = tenantTables.contains(key.child()) && tenantTables.contains(key.parent())
                    && key.nullable() && !SELF_HANDLED_DELETE_RULES.contains(key.deleteRule());
            if (!crossTenantCandidate) {
                continue;
            }
            jdbc.sql("UPDATE %s SET %s = NULL WHERE tenant_id <> :tenantId AND %s IN (SELECT %s FROM %s WHERE tenant_id = :tenantId)"
                    .formatted(quote(key.child()), quote(key.column()), quote(key.column()), quote(key.parentColumn()),
                            quote(key.parent())))
                .param("tenantId", tenantId)
                .update();
        }
    }

    /** Таблицы без tenant_id, ссылающиеся на строки школы (а не на них — каскад), и их ссылки. */
    private static Map<String, List<ForeignKey>> dependentTables(Set<String> tenantTables, List<ForeignKey> foreignKeys) {
        Map<String, List<ForeignKey>> dependents = new HashMap<>();
        for (ForeignKey key : foreignKeys) {
            boolean dependent = !tenantTables.contains(key.child()) && !RETAINED_TABLES.contains(key.child())
                    && !TENANTS_TABLE.equals(key.child()) && tenantTables.contains(key.parent())
                    && !SELF_HANDLED_DELETE_RULES.contains(key.deleteRule());
            if (dependent) {
                dependents.computeIfAbsent(key.child(), table -> new ArrayList<>()).add(key);
            }
        }
        return dependents;
    }

    private int deleteDependent(UUID tenantId, String table, List<ForeignKey> keys) {
        List<String> conditions = keys.stream()
                .map(key -> "%s IN (SELECT %s FROM %s WHERE tenant_id = :tenantId)"
                        .formatted(quote(key.column()), quote(key.parentColumn()), quote(key.parent())))
                .toList();
        return jdbc.sql("DELETE FROM " + quote(table) + " WHERE " + String.join(" OR ", conditions))
            .param("tenantId", tenantId)
            .update();
    }

    private List<String> tenantTables() {
        return jdbc.sql("""
                SELECT c.relname
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                JOIN pg_attribute a ON a.attrelid = c.oid AND a.attname = :column AND NOT a.attisdropped
                WHERE n.nspname = current_schema() AND c.relkind IN ('r', 'p')
                """)
            .param("column", TENANT_COLUMN)
            .query(String.class)
            .list();
    }

    /** Одноколоночные внешние ключи текущей схемы (составных в схеме нет). */
    private List<ForeignKey> foreignKeys() {
        return jdbc.sql("""
                SELECT child.relname AS child, ca.attname AS child_column, NOT ca.attnotnull AS nullable,
                       parent.relname AS parent, pa.attname AS parent_column, con.confdeltype::text AS delete_rule
                FROM pg_constraint con
                JOIN pg_class child ON child.oid = con.conrelid
                JOIN pg_class parent ON parent.oid = con.confrelid
                JOIN pg_namespace n ON n.oid = child.relnamespace
                JOIN pg_attribute ca ON ca.attrelid = con.conrelid AND ca.attnum = con.conkey[1]
                JOIN pg_attribute pa ON pa.attrelid = con.confrelid AND pa.attnum = con.confkey[1]
                WHERE con.contype = 'f' AND n.nspname = current_schema() AND cardinality(con.conkey) = 1
                """)
            .query((rs, n) -> new ForeignKey(rs.getString("child"), rs.getString("child_column"),
                    rs.getBoolean("nullable"), rs.getString("parent"), rs.getString("parent_column"),
                    rs.getString("delete_rule")))
            .list();
    }

    private void setPurgeFlag(String value) {
        jdbc.sql("SELECT set_config(:name, :value, true)").param("name", PURGE_FLAG).param("value", value)
                .query(String.class).single();
    }

    private static String quote(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }

    private record ForeignKey(String child, String column, boolean nullable, String parent, String parentColumn,
                              String deleteRule) {
    }
}
