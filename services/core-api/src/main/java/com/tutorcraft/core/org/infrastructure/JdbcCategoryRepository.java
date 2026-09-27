package com.tutorcraft.core.org.infrastructure;

import com.tutorcraft.core.org.application.CategoryRepository;
import com.tutorcraft.core.org.domain.Category;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCategoryRepository implements CategoryRepository {

    private final JdbcClient jdbc;
    private final Clock clock;

    JdbcCategoryRepository(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public List<Category> findAll(UUID tenantId) {
        return jdbc.sql("SELECT id, tenant_id, parent_id, name, position FROM categories WHERE tenant_id = :tenantId ORDER BY position, name")
            .param("tenantId", tenantId)
            .query((rs, n) -> new Category(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                    rs.getObject("parent_id", UUID.class), rs.getString("name"), rs.getInt("position")))
            .list();
    }

    @Override
    public Optional<Category> find(UUID tenantId, UUID id) {
        return jdbc.sql("SELECT id, tenant_id, parent_id, name, position FROM categories WHERE tenant_id = :tenantId AND id = :id")
            .param("tenantId", tenantId).param("id", id)
            .query((rs, n) -> new Category(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                    rs.getObject("parent_id", UUID.class), rs.getString("name"), rs.getInt("position")))
            .optional();
    }

    @Override
    public void insert(Category c) {
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.sql("""
                INSERT INTO categories (id, tenant_id, parent_id, name, position, created_at, updated_at)
                VALUES (:id, :tenantId, :parentId, :name, :position, :now, :now)
                """)
            .param("id", c.id()).param("tenantId", c.tenantId()).param("parentId", c.parentId())
            .param("name", c.name()).param("position", c.position()).param("now", now).update();
    }

    @Override
    public void update(Category c) {
        jdbc.sql("""
                UPDATE categories SET parent_id = :parentId, name = :name, position = :position, updated_at = :now
                WHERE tenant_id = :tenantId AND id = :id
                """)
            .param("parentId", c.parentId()).param("name", c.name()).param("position", c.position())
            .param("now", Timestamp.from(clock.instant())).param("tenantId", c.tenantId()).param("id", c.id()).update();
    }

    @Override
    public void delete(UUID tenantId, UUID id) {
        jdbc.sql("DELETE FROM categories WHERE tenant_id = :tenantId AND id = :id").param("tenantId", tenantId).param("id", id).update();
    }

    @Override
    public int nextPosition(UUID tenantId, UUID parentId) {
        return jdbc.sql("""
                SELECT COALESCE(MAX(position) + 1, 0) FROM categories
                WHERE tenant_id = :tenantId AND parent_id IS NOT DISTINCT FROM CAST(:parentId AS uuid)
                """)
            .param("tenantId", tenantId).param("parentId", parentId).query(Integer.class).single();
    }

    @Override
    public boolean hasChildren(UUID tenantId, UUID id) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM categories WHERE tenant_id = :tenantId AND parent_id = :id)")
            .param("tenantId", tenantId).param("id", id).query(Boolean.class).single();
    }

    @Override
    public List<UUID> selfAndAncestors(UUID tenantId, UUID categoryId) {
        return jdbc.sql("""
                WITH RECURSIVE chain AS (
                    SELECT id, parent_id, 0 AS depth FROM categories WHERE tenant_id = :tenantId AND id = :id
                    UNION ALL
                    SELECT c.id, c.parent_id, chain.depth + 1 FROM categories c
                    JOIN chain ON c.id = chain.parent_id
                    WHERE c.tenant_id = :tenantId AND chain.depth < 50
                )
                SELECT id FROM chain ORDER BY depth
                """)
            .param("tenantId", tenantId).param("id", categoryId).query(UUID.class).list();
    }
}
