package com.tutorcraft.core.assessment.quiz.infrastructure;

import static com.tutorcraft.core.assessment.quiz.infrastructure.QuestionDocuments.CATEGORY_ID;
import static com.tutorcraft.core.assessment.quiz.infrastructure.QuestionDocuments.COURSE_ID;
import static com.tutorcraft.core.assessment.quiz.infrastructure.QuestionDocuments.CREATED_AT;
import static com.tutorcraft.core.assessment.quiz.infrastructure.QuestionDocuments.DELETED_AT;
import static com.tutorcraft.core.assessment.quiz.infrastructure.QuestionDocuments.ID;
import static com.tutorcraft.core.assessment.quiz.infrastructure.QuestionDocuments.QUESTION_ID;
import static com.tutorcraft.core.assessment.quiz.infrastructure.QuestionDocuments.TAGS;
import static com.tutorcraft.core.assessment.quiz.infrastructure.QuestionDocuments.TENANT_ID;
import static com.tutorcraft.core.assessment.quiz.infrastructure.QuestionDocuments.TITLE;
import static com.tutorcraft.core.assessment.quiz.infrastructure.QuestionDocuments.TYPE;
import static com.tutorcraft.core.assessment.quiz.infrastructure.QuestionDocuments.UPDATED_AT;
import static com.tutorcraft.core.assessment.quiz.infrastructure.QuestionDocuments.VERSION;

import com.tutorcraft.core.assessment.quiz.application.QuestionRepository;
import com.tutorcraft.core.assessment.quiz.domain.QuestionVersion;
import com.tutorcraft.core.assessment.quiz.domain.StoredQuestion;
import com.tutorcraft.core.shared.api.CursorCodec;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
class MongoQuestionRepository implements QuestionRepository {

    static final String QUESTIONS = "questions";
    static final String VERSIONS = "question_versions";
    private static final String COUNT = "count";

    private final MongoTemplate mongo;

    MongoQuestionRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void save(StoredQuestion question, QuestionVersion version) {
        mongo.save(QuestionDocuments.version(version), VERSIONS);
        mongo.save(QuestionDocuments.question(question), QUESTIONS);
    }

    @Override
    public Optional<StoredQuestion> find(UUID tenantId, UUID questionId) {
        Document doc = mongo.findOne(new Query(alive(tenantId).and(ID).is(questionId)), Document.class, QUESTIONS);
        return Optional.ofNullable(doc).map(QuestionDocuments::question);
    }

    @Override
    public Map<UUID, StoredQuestion> findAll(UUID tenantId, Collection<UUID> questionIds) {
        if (questionIds.isEmpty()) {
            return Map.of();
        }
        return mongo.find(new Query(alive(tenantId).and(ID).in(questionIds)), Document.class, QUESTIONS).stream()
                .map(QuestionDocuments::question).collect(Collectors.toMap(StoredQuestion::id, Function.identity()));
    }

    @Override
    public Optional<QuestionVersion> findVersion(UUID tenantId, UUID versionId) {
        Document doc = mongo.findOne(new Query(Criteria.where(TENANT_ID).is(tenantId).and(ID).is(versionId)), Document.class,
                VERSIONS);
        return Optional.ofNullable(doc).map(QuestionDocuments::version);
    }

    @Override
    public Map<UUID, QuestionVersion> findVersions(UUID tenantId, Collection<UUID> versionIds) {
        if (versionIds.isEmpty()) {
            return Map.of();
        }
        Query query = new Query(Criteria.where(TENANT_ID).is(tenantId).and(ID).in(versionIds));
        return mongo.find(query, Document.class, VERSIONS).stream().map(QuestionDocuments::version)
                .collect(Collectors.toMap(QuestionVersion::id, Function.identity()));
    }

    @Override
    public List<VersionInfo> versions(UUID tenantId, UUID questionId) {
        Query query = new Query(Criteria.where(TENANT_ID).is(tenantId).and(QUESTION_ID).is(questionId))
                .with(Sort.by(Sort.Direction.DESC, VERSION));
        query.fields().include(ID, VERSION, CREATED_AT);
        return mongo.find(query, Document.class, VERSIONS).stream()
                .map(doc -> new VersionInfo(doc.get(ID, UUID.class), doc.getInteger(VERSION),
                        QuestionDocuments.instant(doc.getDate(CREATED_AT))))
                .toList();
    }

    @Override
    public List<StoredQuestion> findForRandomSlot(UUID tenantId, UUID courseId, UUID categoryId, String tag) {
        Criteria criteria = alive(tenantId).and(COURSE_ID).is(courseId);
        if (categoryId != null) {
            criteria = criteria.and(CATEGORY_ID).is(categoryId);
        }
        if (tag != null && !tag.isBlank()) {
            criteria = criteria.and(TAGS).is(tag.strip());
        }
        return mongo.find(new Query(criteria), Document.class, QUESTIONS).stream().map(QuestionDocuments::question).toList();
    }

    @Override
    public PageResponse<StoredQuestion> search(UUID tenantId, UUID courseId, QuestionFilter filter, PageQuery page) {
        Criteria criteria = withFilter(alive(tenantId).and(COURSE_ID).is(courseId), filter);
        Query query = new Query(criteria);
        page.after().ifPresent(position -> query.addCriteria(after(position)));
        query.with(Sort.by(Sort.Direction.DESC, UPDATED_AT, ID)).limit(page.fetchSize());
        List<StoredQuestion> rows = mongo.find(query, Document.class, QUESTIONS).stream()
                .map(QuestionDocuments::question).toList();
        return page.toPage(rows, StoredQuestion::updatedAt, StoredQuestion::id);
    }

    @Override
    public void markDeleted(UUID tenantId, UUID questionId, Instant at) {
        mongo.updateFirst(new Query(alive(tenantId).and(ID).is(questionId)),
                new Update().set(DELETED_AT, Date.from(at)).set(UPDATED_AT, Date.from(at)), QUESTIONS);
    }

    @Override
    public Map<UUID, Long> countByCategory(UUID tenantId, UUID courseId) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(alive(tenantId).and(COURSE_ID).is(courseId).and(CATEGORY_ID).ne(null)),
                Aggregation.group(CATEGORY_ID).count().as(COUNT));
        Map<UUID, Long> counts = new HashMap<>();
        mongo.aggregate(aggregation, QUESTIONS, Document.class).getMappedResults()
                .forEach(doc -> counts.put(doc.get(ID, UUID.class), ((Number) doc.get(COUNT)).longValue()));
        return counts;
    }

    private static Criteria alive(UUID tenantId) {
        return Criteria.where(TENANT_ID).is(tenantId).and(DELETED_AT).is(null);
    }

    private static Criteria withFilter(Criteria criteria, QuestionFilter filter) {
        Criteria result = criteria;
        if (filter.categoryId() != null) {
            result = result.and(CATEGORY_ID).is(filter.categoryId());
        }
        if (filter.tag() != null && !filter.tag().isBlank()) {
            result = result.and(TAGS).is(filter.tag().strip());
        }
        if (filter.type() != null && !filter.type().isBlank()) {
            result = result.and(TYPE).is(filter.type());
        }
        if (filter.text() != null && !filter.text().isBlank()) {
            result = result.and(TITLE).regex(Pattern.quote(filter.text().strip()), "i");
        }
        return result;
    }

    private static Criteria after(CursorCodec.Position position) {
        Date sortKey = Date.from(position.sortKey());
        return new Criteria().orOperator(Criteria.where(UPDATED_AT).lt(sortKey),
                Criteria.where(UPDATED_AT).is(sortKey).and(ID).lt(position.id()));
    }
}
