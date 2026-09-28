package com.tutorcraft.core.gradebook.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.gradebook.application.GradebookViews.ScaleView;
import com.tutorcraft.core.gradebook.application.ScaleRepository.Scale;
import com.tutorcraft.core.gradebook.domain.ScaleLevel;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.domain.ValidationException;
import com.tutorcraft.core.shared.domain.Validator;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Шкалы (FR-GRADE-05): уровня tenant (tenant.manage) и курса (gradebook.configure). При первом обращении
 * tenant получает стандартные шкалы «отлично…неудовлетворительно» и «зачёт/незачёт».
 */
@Service
public class ScaleService {

    private static final int MAX_LEVELS = 20;
    private static final int MAX_NAME = 100;
    private static final BigDecimal MAX_PERCENT = BigDecimal.valueOf(100);
    private static final List<Scale> DEFAULTS = List.of(
            template("Пятибалльная", List.of(level("отлично", 85), level("хорошо", 70), level("удовлетворительно", 50),
                    level("неудовлетворительно", 0))),
            template("Зачёт/незачёт", List.of(level("зачёт", 60), level("незачёт", 0))));

    private final CurrentUserProvider currentUser;
    private final AccessService access;
    private final ScaleRepository scales;
    private final AuditLog audit;
    private final Clock clock;

    ScaleService(CurrentUserProvider currentUser, AccessService access, ScaleRepository scales, AuditLog audit, Clock clock) {
        this.currentUser = currentUser;
        this.access = access;
        this.scales = scales;
        this.audit = audit;
        this.clock = clock;
    }

    /** Шкалы tenant (+ курса, если courseId задан и курс доступен пользователю). */
    @Transactional
    public List<ScaleView> list(UUID courseId) {
        CurrentUser user = currentUser.require();
        if (courseId != null) {
            access.require(Permission.COURSE_VIEW, AccessContext.course(courseId));
        }
        ensureDefaults(user.tenantId());
        return scales.list(user.tenantId(), courseId).stream().map(ScaleService::view).toList();
    }

    @Transactional
    public ScaleView create(String name, List<ScaleLevel> levels, UUID courseId) {
        CurrentUser user = currentUser.require();
        Permission permission = courseId == null ? Permission.TENANT_MANAGE : Permission.GRADEBOOK_CONFIGURE;
        access.require(permission, courseId == null ? AccessContext.tenant() : AccessContext.course(courseId));
        validate(name, levels);
        Scale scale = new Scale(Ids.newId(), user.tenantId(), courseId, name.trim(), List.copyOf(levels));
        scales.insert(scale, clock.instant());
        Scale saved = scales.find(user.tenantId(), scale.id())
                .orElseThrow(() -> ValidationException.single("name", "duplicate", "Scale with this name already exists"));
        audit.record(AuditRecord.of(user.tenantId(), user.userId(), "gradebook.scale_created", "scale", saved.id().toString()));
        return view(saved);
    }

    /** Шкала для журнала курса: шкала tenant или этого курса. */
    @Transactional(readOnly = true)
    public void requireUsable(UUID tenantId, UUID courseId, UUID scaleId) {
        if (scaleId == null) {
            return;
        }
        boolean usable = scales.find(tenantId, scaleId)
                .map(scale -> scale.courseId() == null || scale.courseId().equals(courseId))
                .orElse(false);
        if (!usable) {
            throw ValidationException.single("scaleId", "not_found", "Scale not found");
        }
    }

    private void ensureDefaults(UUID tenantId) {
        if (scales.hasTenantScales(tenantId)) {
            return;
        }
        DEFAULTS.forEach(template -> scales.insert(new Scale(Ids.newId(), tenantId, null, template.name(), template.levels()),
                clock.instant()));
    }

    private static void validate(String name, List<ScaleLevel> levels) {
        Validator validator = new Validator().notBlank(name, "name").maxLength(name, MAX_NAME, "name")
                .check(levels != null && !levels.isEmpty() && levels.size() <= MAX_LEVELS, "levels", "invalid_count",
                        "A scale must have from 1 to " + MAX_LEVELS + " levels");
        if (levels != null) {
            validator.check(levels.stream().allMatch(ScaleService::validLevel), "levels", "invalid",
                            "Each level needs a name and a minimum percent between 0 and 100")
                    .check(levels.stream().map(ScaleLevel::minPercent).filter(Objects::nonNull).distinct().count() == levels.size(),
                            "levels", "duplicate_percent", "Minimum percents must be distinct");
        }
        validator.throwIfInvalid();
    }

    private static boolean validLevel(ScaleLevel level) {
        return level != null && level.name() != null && !level.name().isBlank() && level.name().length() <= MAX_NAME
                && level.minPercent() != null && level.minPercent().signum() >= 0 && level.minPercent().compareTo(MAX_PERCENT) <= 0;
    }

    private static ScaleView view(Scale scale) {
        return new ScaleView(scale.id(), scale.name(), scale.levels(), scale.courseId());
    }

    private static Scale template(String name, List<ScaleLevel> levels) {
        return new Scale(null, null, null, name, levels);
    }

    private static ScaleLevel level(String name, int minPercent) {
        return new ScaleLevel(name, BigDecimal.valueOf(minPercent));
    }
}
