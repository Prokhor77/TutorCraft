package com.tutorcraft.core.assessment.quiz.infrastructure;

import static com.tutorcraft.core.assessment.quiz.infrastructure.QuestionDocuments.COURSE_ID;
import static com.tutorcraft.core.assessment.quiz.infrastructure.QuestionDocuments.ID;
import static com.tutorcraft.core.assessment.quiz.infrastructure.QuestionDocuments.NAME;
import static com.tutorcraft.core.assessment.quiz.infrastructure.QuestionDocuments.TENANT_ID;

import com.tutorcraft.core.assessment.quiz.application.QuestionCategoryRepository;
import com.tutorcraft.core.assessment.quiz.domain.QuestionCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
class MongoQuestionCategoryRepository implements QuestionCategoryRepository {

    static final String COLLECTION = "question_categories";

    private final MongoTemplate mongo;

    MongoQuestionCategoryRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void insert(QuestionCategory category) {
        mongo.save(QuestionDocuments.category(category), COLLECTION);
    }

    @Override
    public Optional<QuestionCategory> find(UUID tenantId, UUID categoryId) {
        Document doc = mongo.findOne(new Query(Criteria.where(TENANT_ID).is(tenantId).and(ID).is(categoryId)), Document.class,
                COLLECTION);
        return Optional.ofNullable(doc).map(QuestionDocuments::category);
    }

    @Override
    public List<QuestionCategory> listOfCourse(UUID tenantId, UUID courseId) {
        Query query = new Query(Criteria.where(TENANT_ID).is(tenantId).and(COURSE_ID).is(courseId))
                .with(Sort.by(Sort.Direction.ASC, NAME));
        return mongo.find(query, Document.class, COLLECTION).stream().map(QuestionDocuments::category).toList();
    }
}
