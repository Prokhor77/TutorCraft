package com.tutorcraft.core.assessment.quiz.infrastructure;

import com.tutorcraft.core.assessment.quiz.domain.QuestionCategory;
import com.tutorcraft.core.assessment.quiz.domain.QuestionDataParser;
import com.tutorcraft.core.assessment.quiz.domain.QuestionType;
import com.tutorcraft.core.assessment.quiz.domain.QuestionVersion;
import com.tutorcraft.core.assessment.quiz.domain.StoredQuestion;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bson.Document;

/** Маппинг банка вопросов в документы MongoDB и обратно (UUID — standard, даты — Date, баллы — double). */
final class QuestionDocuments {

    static final String ID = "_id";
    static final String TENANT_ID = "tenantId";
    static final String COURSE_ID = "courseId";
    static final String CATEGORY_ID = "categoryId";
    static final String QUESTION_ID = "questionId";
    static final String TAGS = "tags";
    static final String TYPE = "type";
    static final String TITLE = "title";
    static final String VERSION = "version";
    static final String CURRENT_VERSION = "currentVersion";
    static final String CURRENT_VERSION_ID = "currentVersionId";
    static final String DELETED_AT = "deletedAt";
    static final String CREATED_AT = "createdAt";
    static final String UPDATED_AT = "updatedAt";
    static final String PARENT_ID = "parentId";
    static final String NAME = "name";

    private QuestionDocuments() {
    }

    static Document question(StoredQuestion question) {
        return new Document(ID, question.id()).append(TENANT_ID, question.tenantId()).append(COURSE_ID, question.courseId())
                .append(CATEGORY_ID, question.categoryId()).append(TAGS, question.tags()).append(TYPE, question.type().key())
                .append(TITLE, question.title()).append(CURRENT_VERSION, question.currentVersion())
                .append(CURRENT_VERSION_ID, question.currentVersionId()).append(CREATED_AT, date(question.createdAt()))
                .append(UPDATED_AT, date(question.updatedAt())).append(DELETED_AT, null);
    }

    @SuppressWarnings("unchecked")
    static StoredQuestion question(Document doc) {
        return new StoredQuestion(doc.get(ID, UUID.class), doc.get(TENANT_ID, UUID.class), doc.get(COURSE_ID, UUID.class),
                doc.get(CATEGORY_ID, UUID.class), (List<String>) doc.getOrDefault(TAGS, List.of()),
                QuestionType.fromKey(doc.getString(TYPE), TYPE), doc.getString(TITLE), doc.getInteger(CURRENT_VERSION),
                doc.get(CURRENT_VERSION_ID, UUID.class), instant(doc.getDate(CREATED_AT)), instant(doc.getDate(UPDATED_AT)));
    }

    static Document version(QuestionVersion version) {
        return new Document(ID, version.id()).append(TENANT_ID, version.tenantId()).append(QUESTION_ID, version.questionId())
                .append(VERSION, version.version()).append(TYPE, version.type().key()).append(TITLE, version.title())
                .append("body", version.body()).append("data", version.data().toMap())
                .append("defaultScore", version.defaultScore().doubleValue())
                .append("generalFeedback", version.generalFeedback()).append(CREATED_AT, date(version.createdAt()))
                .append("createdBy", version.createdBy());
    }

    @SuppressWarnings("unchecked")
    static QuestionVersion version(Document doc) {
        QuestionType type = QuestionType.fromKey(doc.getString(TYPE), TYPE);
        return new QuestionVersion(doc.get(ID, UUID.class), doc.get(TENANT_ID, UUID.class), doc.get(QUESTION_ID, UUID.class),
                doc.getInteger(VERSION), type, doc.getString(TITLE), (Map<String, Object>) doc.get("body"),
                QuestionDataParser.parse(type, (Map<String, Object>) doc.get("data")),
                BigDecimal.valueOf(((Number) doc.get("defaultScore")).doubleValue()),
                (Map<String, Object>) doc.get("generalFeedback"), instant(doc.getDate(CREATED_AT)),
                doc.get("createdBy", UUID.class));
    }

    static Document category(QuestionCategory category) {
        return new Document(ID, category.id()).append(TENANT_ID, category.tenantId()).append(COURSE_ID, category.courseId())
                .append(PARENT_ID, category.parentId()).append(NAME, category.name())
                .append(CREATED_AT, date(category.createdAt()));
    }

    static QuestionCategory category(Document doc) {
        return new QuestionCategory(doc.get(ID, UUID.class), doc.get(TENANT_ID, UUID.class), doc.get(COURSE_ID, UUID.class),
                doc.get(PARENT_ID, UUID.class), doc.getString(NAME), instant(doc.getDate(CREATED_AT)));
    }

    static Date date(Instant instant) {
        return instant == null ? null : Date.from(instant);
    }

    static Instant instant(Date date) {
        return date == null ? null : date.toInstant();
    }
}
