package com.tutorcraft.core.gradebook.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.gradebook.application.GradebookViews.Setup;
import com.tutorcraft.core.gradebook.application.GradebookViews.SetupCategory;
import com.tutorcraft.core.gradebook.application.GradebookViews.SetupItem;
import com.tutorcraft.core.gradebook.application.GradebookViews.SetupWarning;
import com.tutorcraft.core.gradebook.domain.Aggregation;
import com.tutorcraft.core.gradebook.domain.FormulaDescriber;
import com.tutorcraft.core.gradebook.domain.FormulaTexts;
import com.tutorcraft.core.gradebook.domain.GradeCategory;
import com.tutorcraft.core.gradebook.domain.GradeColumn;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.domain.ValidationException;
import com.tutorcraft.core.shared.domain.Validator;
import com.tutorcraft.core.shared.i18n.Messages;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Настройка итоговой оценки (FR-GRADE-02/03, AC-7): категории с весами, агрегирование, шкала, формула. */
@Service
public class GradebookSetupService {

    private static final int MAX_CATEGORIES = 50;
    private static final int MAX_NAME = 200;
    private static final BigDecimal MAX_WEIGHT = BigDecimal.valueOf(100);
    private static final BigDecimal MAX_SCORE_LIMIT = BigDecimal.valueOf(10_000);

    private final CurrentUserProvider currentUser;
    private final AccessService access;
    private final GradebookStructureRepository structure;
    private final ScaleService scales;
    private final Messages messages;
    private final AuditLog audit;
    private final Clock clock;

    GradebookSetupService(CurrentUserProvider currentUser, AccessService access, GradebookStructureRepository structure,
                          ScaleService scales, Messages messages, AuditLog audit, Clock clock) {
        this.currentUser = currentUser;
        this.access = access;
        this.structure = structure;
        this.scales = scales;
        this.messages = messages;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Setup setup(UUID courseId) {
        CurrentUser user = currentUser.require();
        access.require(Permission.GRADE_VIEW_ALL, AccessContext.course(courseId));
        return view(user.tenantId(), courseId);
    }

    @Transactional
    public Setup update(UUID courseId, SetupCommand command) {
        CurrentUser user = currentUser.require();
        access.require(Permission.GRADEBOOK_CONFIGURE, AccessContext.course(courseId));
        Aggregation aggregation = validate(command);
        scales.requireUsable(user.tenantId(), courseId, command.scaleId());
        Instant now = clock.instant();
        Set<UUID> categoryIds = saveCategories(user.tenantId(), courseId, command.categories(), now);
        assignItems(user.tenantId(), courseId, command.items(), categoryIds);
        structure.saveSettings(user.tenantId(), courseId, aggregation, command.scaleId(), now);
        audit.record(AuditRecord.of(user.tenantId(), user.userId(), "gradebook.setup_changed", "course", courseId.toString())
                .withDiff(Map.of("aggregation", aggregation.key(), "categories", categoryIds.size())));
        return view(user.tenantId(), courseId);
    }

    /** Ручной столбец журнала (например, «Активность на семинарах»). */
    @Transactional
    public SetupItem addManualItem(UUID courseId, String name, BigDecimal maxScore, UUID categoryId) {
        CurrentUser user = currentUser.require();
        access.require(Permission.GRADEBOOK_CONFIGURE, AccessContext.course(courseId));
        new Validator().notBlank(name, "name").maxLength(name, MAX_NAME, "name")
                .check(maxScore != null && maxScore.signum() >= 0 && maxScore.compareTo(MAX_SCORE_LIMIT) <= 0,
                        "maxScore", "out_of_range", "maxScore must be between 0 and 10000")
                .check(categoryId == null || categoryExists(user.tenantId(), courseId, categoryId), "categoryId",
                        "not_found", "Category not found")
                .throwIfInvalid();
        GradeColumn column = new GradeColumn(Ids.newId(), courseId, null, name.trim(), maxScore, categoryId, 0);
        structure.insertColumn(user.tenantId(), column, clock.instant());
        audit.record(AuditRecord.of(user.tenantId(), user.userId(), "gradebook.item_created", "grade_item", column.id().toString()));
        return SetupItem.of(column);
    }

    private Setup view(UUID tenantId, UUID courseId) {
        GradebookStructureRepository.GradebookSettings settings = structure.settings(tenantId, courseId)
                .orElse(GradebookStructureRepository.GradebookSettings.defaults(courseId));
        List<GradeCategory> categories = structure.categories(tenantId, courseId);
        List<GradeColumn> columns = structure.columns(tenantId, courseId);
        Locale locale = LocaleContextHolder.getLocale();
        FormulaDescriber.Preview preview = FormulaDescriber.describe(settings.aggregation(), categories, columns,
                texts(locale), locale);
        return new Setup(settings.aggregation().key(),
                categories.stream().map(c -> new SetupCategory(c.id(), c.name(), c.weight())).toList(),
                columns.stream().map(SetupItem::of).toList(), settings.scaleId(), preview.formula(),
                preview.warnings().stream().map(w -> new SetupWarning(w.code(), w.message())).toList());
    }

    private FormulaTexts texts(Locale locale) {
        return new FormulaTexts(messages.get(locale, "gradebook.formula.final"), messages.get(locale, "gradebook.formula.points"),
                messages.get(locale, "gradebook.formula.empty"), messages.get(locale, "gradebook.warning.weights_not_100"),
                messages.get(locale, "gradebook.warning.empty_category"), messages.get(locale, "gradebook.warning.zero_max"));
    }

    private static Aggregation validate(SetupCommand command) {
        Validator validator = new Validator()
                .check(command.categories().size() <= MAX_CATEGORIES, "categories", "too_many", "Too many categories");
        for (int index = 0; index < command.categories().size(); index++) {
            CategoryInput category = command.categories().get(index);
            String field = "categories[" + index + "]";
            validator.notBlank(category.name(), field + ".name").maxLength(category.name(), MAX_NAME, field + ".name")
                    .check(category.weight() != null && category.weight().signum() >= 0
                            && category.weight().compareTo(MAX_WEIGHT) <= 0, field + ".weight", "out_of_range",
                            "Weight must be between 0 and 100");
        }
        validator.throwIfInvalid();
        return Aggregation.find(command.aggregation())
                .orElseThrow(() -> ValidationException.single("aggregation", "invalid", "Unknown aggregation"));
    }

    private Set<UUID> saveCategories(UUID tenantId, UUID courseId, List<CategoryInput> inputs, Instant now) {
        Set<UUID> existing = structure.categories(tenantId, courseId).stream().map(GradeCategory::id).collect(Collectors.toSet());
        List<UUID> kept = new ArrayList<>();
        for (int position = 0; position < inputs.size(); position++) {
            CategoryInput input = inputs.get(position);
            boolean known = input.id() != null && existing.contains(input.id());
            GradeCategory category = new GradeCategory(known ? input.id() : Ids.newId(), input.name().trim(), input.weight(), position);
            if (known) {
                structure.updateCategory(tenantId, courseId, category);
            } else {
                structure.insertCategory(tenantId, courseId, category, now);
            }
            kept.add(category.id());
        }
        structure.deleteCategoriesExcept(tenantId, courseId, kept);
        return Set.copyOf(kept);
    }

    private void assignItems(UUID tenantId, UUID courseId, List<ItemInput> items, Set<UUID> categoryIds) {
        Set<UUID> columnIds = structure.columns(tenantId, courseId).stream().map(GradeColumn::id).collect(Collectors.toSet());
        Validator validator = new Validator();
        for (int index = 0; index < items.size(); index++) {
            ItemInput item = items.get(index);
            validator.check(columnIds.contains(item.gradeItemId()), "items[" + index + "].gradeItemId", "not_found",
                            "Grade item not found")
                    .check(item.categoryId() == null || categoryIds.contains(item.categoryId()),
                            "items[" + index + "].categoryId", "not_found", "Category not found");
        }
        validator.throwIfInvalid();
        items.forEach(item -> structure.assignCategory(tenantId, courseId, item.gradeItemId(), item.categoryId()));
    }

    private boolean categoryExists(UUID tenantId, UUID courseId, UUID categoryId) {
        return structure.categories(tenantId, courseId).stream().anyMatch(category -> category.id().equals(categoryId));
    }

    public record CategoryInput(UUID id, String name, BigDecimal weight) {
    }

    public record ItemInput(UUID gradeItemId, UUID categoryId) {
    }

    public record SetupCommand(String aggregation, List<CategoryInput> categories, List<ItemInput> items, UUID scaleId) {

        public SetupCommand {
            categories = categories == null ? List.of() : List.copyOf(categories);
            items = items == null ? List.of() : List.copyOf(items);
        }
    }
}
