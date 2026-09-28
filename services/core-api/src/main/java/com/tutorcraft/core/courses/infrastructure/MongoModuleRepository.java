package com.tutorcraft.core.courses.infrastructure;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gte;
import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Filters.lt;
import static com.mongodb.client.model.Filters.ne;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.tutorcraft.core.courses.Visibility;
import com.tutorcraft.core.courses.application.ModuleRepository;
import com.tutorcraft.core.courses.domain.CourseModule;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

/** Модули курса в MongoDB. Каждый запрос фильтруется по tenantId (DATA-01). */
@Repository
class MongoModuleRepository implements ModuleRepository {

    static final String COLLECTION = "modules";

    private final MongoTemplate mongo;

    MongoModuleRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void insertAll(Collection<CourseModule> modules) {
        if (modules.isEmpty()) {
            return;
        }
        collection().insertMany(modules.stream().map(MongoModuleRepository::toDocument).toList());
    }

    @Override
    public Optional<CourseModule> find(UUID tenantId, UUID id) {
        return Optional.ofNullable(collection().find(and(eq(MongoFields.ID, id), eq(MongoFields.TENANT_ID, tenantId),
                eq(MongoFields.DELETED_AT, null))).first()).map(MongoModuleRepository::fromDocument);
    }

    @Override
    public Optional<CourseModule> findIncludingDeleted(UUID tenantId, UUID id) {
        return Optional.ofNullable(collection().find(and(eq(MongoFields.ID, id), eq(MongoFields.TENANT_ID, tenantId))).first())
                .map(MongoModuleRepository::fromDocument);
    }

    @Override
    public List<CourseModule> ofCourse(UUID tenantId, UUID courseId) {
        return list(and(eq(MongoFields.TENANT_ID, tenantId), eq(MongoFields.COURSE_ID, courseId), eq(MongoFields.DELETED_AT, null)));
    }

    @Override
    public List<CourseModule> ofCourses(UUID tenantId, Collection<UUID> courseIds) {
        if (courseIds.isEmpty()) {
            return List.of();
        }
        return list(and(eq(MongoFields.TENANT_ID, tenantId), in(MongoFields.COURSE_ID, courseIds), eq(MongoFields.DELETED_AT, null)));
    }

    @Override
    public boolean update(CourseModule module, long expectedVersion) {
        Bson filter = and(eq(MongoFields.ID, module.id()), eq(MongoFields.TENANT_ID, module.tenantId()),
                eq(MongoFields.VERSION, expectedVersion), eq(MongoFields.DELETED_AT, null));
        Bson update = Updates.combine(
                Updates.set(MongoFields.TITLE, module.title()),
                Updates.set(MongoFields.VISIBILITY, module.visibility().key()),
                Updates.set(MongoFields.PUBLISH_AT, MongoFields.date(module.publishAt())),
                Updates.set(MongoFields.CONDITIONS, module.conditions()),
                Updates.inc(MongoFields.VERSION, 1L));
        return collection().updateOne(filter, update).getMatchedCount() == 1;
    }

    @Override
    public void move(UUID tenantId, UUID id, UUID parentId, int position) {
        collection().updateOne(and(eq(MongoFields.ID, id), eq(MongoFields.TENANT_ID, tenantId)), Updates.combine(
                Updates.set(MongoFields.PARENT_ID, parentId),
                Updates.set(MongoFields.POSITION, position),
                Updates.inc(MongoFields.VERSION, 1L)));
    }

    @Override
    public void updatePositions(UUID tenantId, Map<UUID, Integer> positions) {
        MongoPositions.update(collection(), tenantId, positions);
    }

    @Override
    public void softDelete(UUID tenantId, Collection<UUID> ids, Instant at) {
        if (ids.isEmpty()) {
            return;
        }
        collection().updateMany(and(eq(MongoFields.TENANT_ID, tenantId), in(MongoFields.ID, ids), eq(MongoFields.DELETED_AT, null)),
                Updates.combine(Updates.set(MongoFields.DELETED_AT, MongoFields.date(at)), Updates.inc(MongoFields.VERSION, 1L)));
    }

    @Override
    public void restore(UUID tenantId, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return;
        }
        collection().updateMany(and(eq(MongoFields.TENANT_ID, tenantId), in(MongoFields.ID, ids)),
                Updates.combine(Updates.set(MongoFields.DELETED_AT, null), Updates.inc(MongoFields.VERSION, 1L)));
    }

    @Override
    public List<CourseModule> deletedSince(UUID tenantId, UUID courseId, Instant since) {
        return list(and(eq(MongoFields.TENANT_ID, tenantId), eq(MongoFields.COURSE_ID, courseId),
                ne(MongoFields.DELETED_AT, null), gte(MongoFields.DELETED_AT, MongoFields.date(since))));
    }

    @Override
    public long purgeDeletedBefore(Instant cutoff) {
        return collection().deleteMany(lt(MongoFields.DELETED_AT, MongoFields.date(cutoff))).getDeletedCount();
    }

    @Override
    public void deleteAllOfCourse(UUID tenantId, UUID courseId) {
        collection().deleteMany(and(eq(MongoFields.TENANT_ID, tenantId), eq(MongoFields.COURSE_ID, courseId)));
    }

    private List<CourseModule> list(Bson filter) {
        List<CourseModule> result = new ArrayList<>();
        collection().find(filter).sort(Sorts.ascending(MongoFields.POSITION))
                .forEach(document -> result.add(fromDocument(document)));
        return result;
    }

    private MongoCollection<Document> collection() {
        return mongo.getCollection(COLLECTION);
    }

    private static Document toDocument(CourseModule module) {
        return new Document(MongoFields.ID, module.id())
                .append(MongoFields.TENANT_ID, module.tenantId())
                .append(MongoFields.COURSE_ID, module.courseId())
                .append(MongoFields.PARENT_ID, module.parentId())
                .append(MongoFields.TITLE, module.title())
                .append(MongoFields.POSITION, module.position())
                .append(MongoFields.VISIBILITY, module.visibility().key())
                .append(MongoFields.PUBLISH_AT, MongoFields.date(module.publishAt()))
                .append(MongoFields.CONDITIONS, module.conditions())
                .append(MongoFields.VERSION, module.version())
                .append(MongoFields.DELETED_AT, MongoFields.date(module.deletedAt()));
    }

    private static CourseModule fromDocument(Document document) {
        return new CourseModule(document.get(MongoFields.ID, UUID.class), document.get(MongoFields.TENANT_ID, UUID.class),
                document.get(MongoFields.COURSE_ID, UUID.class), document.get(MongoFields.PARENT_ID, UUID.class),
                document.getString(MongoFields.TITLE), MongoFields.position(document),
                Visibility.fromKey(document.getString(MongoFields.VISIBILITY)), MongoFields.instant(document, MongoFields.PUBLISH_AT),
                MongoFields.map(document, MongoFields.CONDITIONS), MongoFields.version(document),
                MongoFields.instant(document, MongoFields.DELETED_AT));
    }
}
