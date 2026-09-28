package com.tutorcraft.core.gradebook.application;

import com.tutorcraft.core.gradebook.application.GradebookStructureRepository.GradebookSettings;
import com.tutorcraft.core.gradebook.domain.ScaleLevel;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Загрузка снимка журнала курса (настройки, категории, столбцы, уровни шкалы). */
@Component
class CourseGradebooks {

    private final GradebookStructureRepository structure;
    private final ScaleRepository scales;

    CourseGradebooks(GradebookStructureRepository structure, ScaleRepository scales) {
        this.structure = structure;
        this.scales = scales;
    }

    CourseGradebook load(UUID tenantId, UUID courseId) {
        GradebookSettings settings = structure.settings(tenantId, courseId).orElse(GradebookSettings.defaults(courseId));
        List<ScaleLevel> levels = settings.scaleId() == null ? List.of()
                : scales.find(tenantId, settings.scaleId()).map(ScaleRepository.Scale::levels).orElse(List.of());
        return new CourseGradebook(settings, structure.categories(tenantId, courseId), structure.columns(tenantId, courseId),
                levels);
    }
}
