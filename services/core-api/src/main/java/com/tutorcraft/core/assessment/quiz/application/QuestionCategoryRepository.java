package com.tutorcraft.core.assessment.quiz.application;

import com.tutorcraft.core.assessment.quiz.domain.QuestionCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Папки банка вопросов (MongoDB: question_categories). */
public interface QuestionCategoryRepository {

    void insert(QuestionCategory category);

    Optional<QuestionCategory> find(UUID tenantId, UUID categoryId);

    List<QuestionCategory> listOfCourse(UUID tenantId, UUID courseId);
}
