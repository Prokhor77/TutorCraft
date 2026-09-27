package com.tutorcraft.core.org.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.courses.CoursesApi;
import com.tutorcraft.core.org.domain.Category;
import com.tutorcraft.core.org.domain.CategoryTree;
import com.tutorcraft.core.shared.domain.BusinessRuleException;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.domain.Validator;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Дерево категорий курсов (FR-COURSE-01). */
@Service
public class CategoryService {

    private final CategoryRepository categories;
    private final CoursesApi courses;
    private final AccessService access;
    private final CurrentUserProvider currentUser;
    private final AuditLog audit;

    public CategoryService(CategoryRepository categories, CoursesApi courses, AccessService access,
                           CurrentUserProvider currentUser, AuditLog audit) {
        this.categories = categories;
        this.courses = courses;
        this.access = access;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<CategoryView> list() {
        UUID tenantId = currentUser.require().tenantId();
        Map<UUID, Integer> counts = courses.countByCategory(tenantId);
        return categories.findAll(tenantId).stream()
                .map(c -> new CategoryView(c.id(), c.parentId(), c.name(), c.position(), counts.getOrDefault(c.id(), 0)))
                .toList();
    }

    @Transactional
    public CategoryView create(String name, UUID parentId) {
        CurrentUser user = currentUser.require();
        requireManage(parentId);
        validateName(name);
        if (parentId != null) {
            find(user.tenantId(), parentId);
        }
        Category category = new Category(Ids.newId(), user.tenantId(), parentId, name.trim(),
                categories.nextPosition(user.tenantId(), parentId));
        categories.insert(category);
        audit.record(AuditRecord.of(user.tenantId(), user.userId(), "category.created", "category", category.id().toString()));
        return new CategoryView(category.id(), parentId, category.name(), category.position(), 0);
    }

    @Transactional
    public CategoryView update(UUID id, String name, UUID parentId, boolean parentProvided, Integer position) {
        CurrentUser user = currentUser.require();
        Category current = find(user.tenantId(), id);
        requireManage(id);
        UUID newParent = parentProvided ? parentId : current.parentId();
        if (parentProvided && new CategoryTree(categories.findAll(user.tenantId())).wouldCreateCycle(id, newParent)) {
            throw new BusinessRuleException("category.cycle", "Category cannot be moved into its own subtree");
        }
        if (name != null) {
            validateName(name);
        }
        Category updated = new Category(id, user.tenantId(), newParent, name == null ? current.name() : name.trim(),
                position == null ? current.position() : position);
        categories.update(updated);
        audit.record(AuditRecord.of(user.tenantId(), user.userId(), "category.updated", "category", id.toString()));
        return new CategoryView(id, updated.parentId(), updated.name(), updated.position(),
                courses.countByCategory(user.tenantId()).getOrDefault(id, 0));
    }

    @Transactional
    public void delete(UUID id) {
        CurrentUser user = currentUser.require();
        find(user.tenantId(), id);
        requireManage(id);
        boolean hasCourses = courses.countByCategory(user.tenantId()).getOrDefault(id, 0) > 0;
        if (hasCourses || categories.hasChildren(user.tenantId(), id)) {
            throw new BusinessRuleException("category.not_empty", "Category contains courses or subcategories");
        }
        categories.delete(user.tenantId(), id);
        audit.record(AuditRecord.of(user.tenantId(), user.userId(), "category.deleted", "category", id.toString()));
    }

    private void requireManage(UUID categoryId) {
        AccessContext context = categoryId == null ? AccessContext.tenant() : AccessContext.category(categoryId);
        access.require(Permission.CATEGORY_MANAGE, context);
    }

    private Category find(UUID tenantId, UUID id) {
        return categories.find(tenantId, id).orElseThrow(() -> new NotFoundException("category.not_found", "Category not found"));
    }

    private static void validateName(String name) {
        new Validator().notBlank(name, "name").maxLength(name, Category.MAX_NAME_LENGTH, "name").throwIfInvalid();
    }

    public record CategoryView(UUID id, UUID parentId, String name, int position, int courseCount) {
    }
}
