package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.shared.domain.Money;
import java.util.UUID;

/** CourseCard (контракт §5). */
public record CourseCardView(UUID id, String title, String shortName, String coverUrl, UUID categoryId, String role,
                             Integer progressPercent, String visibility, Money price) {
}
