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
import com.tutorcraft.core.courses.ItemType;
import com.tutorcraft.core.courses.Visibility;
import com.tutorcraft.core.courses.application.ItemRepository;
import com.tutorcraft.core.courses.domain.CourseItem;
import com.tutorcraft.core.courses.domain.CourseItem.KeyDates;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

/** Элементы курса в MongoDB. Каждый запрос фильтруется по tenantId (DATA-01). */
@Repository
class MongoItemRepository implements ItemRepository {

    static final String COLLECTION = "items";

    private final MongoTemplate mongo;

    MongoItemRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void insertAll(Collection<CourseItem> items) {
        if (items.isEmpty()) {
            return;
        }
        collection().insertMany(items.stream().map(MongoItemRepository::toDocument).toList());
    }

    @Override
    public Optional<CourseItem> find(UUID tenantId, UUID id) {
        return Optional.ofNullable(collection().find(and(eq(MongoFields.ID, id), eq(MongoFields.TENANT_ID, tenantId),
                eq(MongoFields.DELETED_AT, null))).first()).map(MongoItemRepository::fromDocument);
    }

    @Override
    public Optional<CourseItem> findIncludingDeleted(UUID tenantId, UUID id) {
        return Optional.ofNullable(collection().find(and(eq(MongoFields.ID, id), eq(MongoFields.TENANT_ID, tenantId))).first())
                .map(MongoItemRepository::fromDocument);
    }

    @Override
    public Map<UUID, CourseItem> findAll(UUID tenantId, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return list(and(eq(MongoFields.TENANT_ID, tenantId), in(MongoFields.ID, ids), eq(MongoFields.DELETED_AT, null))).stream()
                .collect(Collectors.toMap(CourseItem::id, Function.identity()));
    }

    @Override
    public List<CourseItem> ofCourse(UUID tenantId, UUID courseId) {
        return list(and(eq(MongoFields.TENANT_ID, tenantId), eq(MongoFields.COURSE_ID, courseId), eq(MongoFields.DELETED_AT, null)));
    }

    @Override
    public List<CourseItem> ofModules(UUID tenantId, Collection<UUID> moduleIds) {
        if (moduleIds.isEmpty()) {
            return List.of();
        }
        return list(and(eq(MongoFields.TENANT_ID, tenantId), in(MongoFields.MODULE_ID, moduleIds), eq(MongoFields.DELETED_AT, null)));
    }

    @Override
    public List<CourseItem> ofCourses(UUID tenantId, Collection<UUID> courseIds, Set<ItemType> types) {
        if (courseIds.isEmpty()) {
            return List.of();
        }
        List<Bson> filters = new ArrayList<>(List.of(eq(MongoFields.TENANT_ID, tenantId), in(MongoFields.COURSE_ID, courseIds),
                eq(MongoFields.DELETED_AT, null)));
        if (!types.isEmpty()) {
            filters.add(in(MongoFields.TYPE, types.stream().map(ItemType::key).toList()));
        }
        return list(and(filters));
    }

    @Override
    public boolean update(CourseItem item, long expectedVersion, Instant now) {
        Bson filter = and(eq(MongoFields.ID, item.id()), eq(MongoFields.TENANT_ID, item.tenantId()),
                eq(MongoFields.VERSION, expectedVersion), eq(MongoFields.DELETED_AT, null));
        Bson update = Updates.combine(
                Updates.set(MongoFields.TITLE, item.title()),
                Updates.set(MongoFields.VISIBILITY, item.visibility().key()),
                Updates.set(MongoFields.PUBLISH_AT, MongoFields.date(item.publishAt())),
                Updates.set(MongoFields.SETTINGS, item.settings()),
                Updates.set(MongoFields.CONTENT, item.content()),
                Updates.set(MongoFields.COMPLETION_RULE, item.completionRule()),
                Updates.set(MongoFields.CONDITIONS, item.conditions()),
                Updates.set(MongoFields.DUE_AT, MongoFields.date(item.dates().dueAt())),
                Updates.set(MongoFields.OPEN_AT, MongoFields.date(item.dates().openAt())),
                Updates.set(MongoFields.CLOSE_AT, MongoFields.date(item.dates().closeAt())),
                Updates.set(MongoFields.UPDATED_AT, MongoFields.date(now)),
                Updates.inc(MongoFields.VERSION, 1L));
        return collection().updateOne(filter, update).getMatchedCount() == 1;
    }

    @Override
    public void move(UUID tenantId, UUID id, UUID moduleId, int position, Instant now) {
        collection().updateOne(and(eq(MongoFields.ID, id), eq(MongoFields.TENANT_ID, tenantId)), Updates.combine(
                Updates.set(MongoFields.MODULE_ID, moduleId),
                Updates.set(MongoFields.POSITION, position),
                Updates.set(MongoFields.UPDATED_AT, MongoFields.date(now)),
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
                Updates.combine(Updates.set(MongoFields.DELETED_AT, MongoFields.date(at)),
                        Updates.set(MongoFields.UPDATED_AT, MongoFields.date(at)), Updates.inc(MongoFields.VERSION, 1L)));
    }

    @Override
    public void restore(UUID tenantId, Collection<UUID> ids, Instant now) {
        if (ids.isEmpty()) {
            return;
        }
        collection().updateMany(and(eq(MongoFields.TENANT_ID, tenantId), in(MongoFields.ID, ids)),
                Updates.combine(Updates.set(MongoFields.DELETED_AT, null), Updates.set(MongoFields.UPDATED_AT, MongoFields.date(now)),
                        Updates.inc(MongoFields.VERSION, 1L)));
    }

    @Override
    public List<CourseItem> deletedSince(UUID tenantId, UUID courseId, Instant since) {
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

    private List<CourseItem> list(Bson filter) {
        List<CourseItem> result = new ArrayList<>();
        collection().find(filter).sort(Sorts.ascending(MongoFields.POSITION))
                .forEach(document -> result.add(fromDocument(document)));
        return result;
    }

    private MongoCollection<Document> collection() {
        return mongo.getCollection(COLLECTION);
    }

    private static Document toDocument(CourseItem item) {
        return new Document(MongoFields.ID, item.id())
                .append(MongoFields.TENANT_ID, item.tenantId())
                .append(MongoFields.COURSE_ID, item.courseId())
                .append(MongoFields.MODULE_ID, item.moduleId())
                .append(MongoFields.TYPE, item.type().key())
                .append(MongoFields.TITLE, item.title())
                .append(MongoFields.POSITION, item.position())
                .append(MongoFields.VISIBILITY, item.visibility().key())
                .append(MongoFields.PUBLISH_AT, MongoFields.date(item.publishAt()))
                .append(MongoFields.SETTINGS, item.settings())
                .append(MongoFields.CONTENT, item.content())
                .append(MongoFields.COMPLETION_RULE, item.completionRule())
                .append(MongoFields.CONDITIONS, item.conditions())
                .append(MongoFields.DUE_AT, MongoFields.date(item.dates().dueAt()))
                .append(MongoFields.OPEN_AT, MongoFields.date(item.dates().openAt()))
                .append(MongoFields.CLOSE_AT, MongoFields.date(item.dates().closeAt()))
                .append(MongoFields.VERSION, item.version())
                .append(MongoFields.DELETED_AT, MongoFields.date(item.deletedAt()))
                .append(MongoFields.CREATED_AT, MongoFields.date(item.createdAt()))
                .append(MongoFields.UPDATED_AT, MongoFields.date(item.updatedAt()));
    }

    private static CourseItem fromDocument(Document document) {
        KeyDates dates = new KeyDates(MongoFields.instant(document, MongoFields.DUE_AT),
                MongoFields.instant(document, MongoFields.OPEN_AT), MongoFields.instant(document, MongoFields.CLOSE_AT));
        return new CourseItem(document.get(MongoFields.ID, UUID.class), document.get(MongoFields.TENANT_ID, UUID.class),
                document.get(MongoFields.COURSE_ID, UUID.class), document.get(MongoFields.MODULE_ID, UUID.class),
                ItemType.fromKey(document.getString(MongoFields.TYPE)), document.getString(MongoFields.TITLE),
                MongoFields.position(document), Visibility.fromKey(document.getString(MongoFields.VISIBILITY)),
                MongoFields.instant(document, MongoFields.PUBLISH_AT), MongoFields.map(document, MongoFields.SETTINGS),
                MongoFields.map(document, MongoFields.CONTENT), MongoFields.map(document, MongoFields.COMPLETION_RULE),
                MongoFields.map(document, MongoFields.CONDITIONS), dates, MongoFields.version(document),
                MongoFields.instant(document, MongoFields.DELETED_AT), MongoFields.instant(document, MongoFields.CREATED_AT),
                MongoFields.instant(document, MongoFields.UPDATED_AT));
    }
}
