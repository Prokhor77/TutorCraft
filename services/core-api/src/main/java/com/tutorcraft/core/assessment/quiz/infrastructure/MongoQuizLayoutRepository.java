package com.tutorcraft.core.assessment.quiz.infrastructure;

import static com.tutorcraft.core.assessment.quiz.infrastructure.QuestionDocuments.CATEGORY_ID;
import static com.tutorcraft.core.assessment.quiz.infrastructure.QuestionDocuments.COURSE_ID;
import static com.tutorcraft.core.assessment.quiz.infrastructure.QuestionDocuments.ID;
import static com.tutorcraft.core.assessment.quiz.infrastructure.QuestionDocuments.QUESTION_ID;
import static com.tutorcraft.core.assessment.quiz.infrastructure.QuestionDocuments.TENANT_ID;

import com.tutorcraft.core.assessment.quiz.application.QuizLayoutRepository;
import com.tutorcraft.core.assessment.quiz.domain.LayoutSlot;
import com.tutorcraft.core.assessment.quiz.domain.QuizLayout;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

/** Состав теста: документ {@code quiz_layouts} с {@code _id = itemId}. */
@Repository
class MongoQuizLayoutRepository implements QuizLayoutRepository {

    static final String COLLECTION = "quiz_layouts";
    static final String SLOTS = "slots";
    private static final String RANDOM = "random";
    private static final String POINTS = "points";
    private static final String PAGE = "page";
    private static final String TAG = "tag";
    private static final String COUNT = "count";

    private final MongoTemplate mongo;

    MongoQuizLayoutRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public Optional<QuizLayout> find(UUID tenantId, UUID itemId) {
        Document doc = mongo.findOne(new Query(Criteria.where(TENANT_ID).is(tenantId).and(ID).is(itemId)), Document.class,
                COLLECTION);
        return Optional.ofNullable(doc).map(MongoQuizLayoutRepository::layout);
    }

    @Override
    public void save(UUID tenantId, UUID courseId, UUID itemId, QuizLayout layout) {
        Document doc = new Document(ID, itemId).append(TENANT_ID, tenantId).append(COURSE_ID, courseId)
                .append(SLOTS, layout.slots().stream().map(MongoQuizLayoutRepository::toDocument).toList());
        mongo.save(doc, COLLECTION);
    }

    @Override
    public Map<UUID, Integer> usage(UUID tenantId, Collection<UUID> questionIds) {
        if (questionIds.isEmpty()) {
            return Map.of();
        }
        Query query = new Query(Criteria.where(TENANT_ID).is(tenantId).and(SLOTS + "." + QUESTION_ID).in(questionIds));
        Set<UUID> wanted = new HashSet<>(questionIds);
        Map<UUID, Integer> usage = new HashMap<>();
        mongo.find(query, Document.class, COLLECTION).forEach(doc -> layout(doc).fixedQuestionIds().stream()
                .filter(wanted::contains).forEach(id -> usage.merge(id, 1, Integer::sum)));
        return usage;
    }

    private static Document toDocument(LayoutSlot slot) {
        Document doc = new Document(POINTS, slot.points() == null ? null : slot.points().doubleValue()).append(PAGE, slot.page());
        return switch (slot) {
            case LayoutSlot.Fixed fixed -> doc.append(QUESTION_ID, fixed.questionId());
            case LayoutSlot.Random random -> doc.append(RANDOM, new Document(CATEGORY_ID, random.categoryId())
                    .append(TAG, random.tag()).append(COUNT, random.count()));
        };
    }

    @SuppressWarnings("unchecked")
    private static QuizLayout layout(Document doc) {
        List<Document> slots = (List<Document>) doc.getOrDefault(SLOTS, List.of());
        return new QuizLayout(slots.stream().map(MongoQuizLayoutRepository::toSlot).toList());
    }

    private static LayoutSlot toSlot(Document doc) {
        Number points = (Number) doc.get(POINTS);
        BigDecimal value = points == null ? null : BigDecimal.valueOf(points.doubleValue());
        Integer page = doc.getInteger(PAGE);
        Document random = doc.get(RANDOM, Document.class);
        if (random == null) {
            return new LayoutSlot.Fixed(doc.get(QUESTION_ID, UUID.class), value, page);
        }
        return new LayoutSlot.Random(random.get(CATEGORY_ID, UUID.class), random.getString(TAG), random.getInteger(COUNT),
                value, page);
    }
}
