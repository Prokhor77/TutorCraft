package com.tutorcraft.core.org.application;

import com.tutorcraft.core.org.domain.Category;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository {

    List<Category> findAll(UUID tenantId);

    Optional<Category> find(UUID tenantId, UUID id);

    void insert(Category category);

    void update(Category category);

    void delete(UUID tenantId, UUID id);

    int nextPosition(UUID tenantId, UUID parentId);

    boolean hasChildren(UUID tenantId, UUID id);

    List<UUID> selfAndAncestors(UUID tenantId, UUID categoryId);
}
