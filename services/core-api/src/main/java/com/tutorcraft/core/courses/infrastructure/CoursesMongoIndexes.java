package com.tutorcraft.core.courses.infrastructure;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/** Индексы коллекций modules/items (создание идемпотентно, при старте приложения). */
@Component
class CoursesMongoIndexes {

    private static final Logger log = LoggerFactory.getLogger(CoursesMongoIndexes.class);

    private final MongoTemplate mongo;

    CoursesMongoIndexes(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        MongoCollection<Document> modules = mongo.getCollection(MongoModuleRepository.COLLECTION);
        modules.createIndex(Indexes.ascending(MongoFields.TENANT_ID, MongoFields.COURSE_ID, MongoFields.POSITION));
        modules.createIndex(Indexes.ascending(MongoFields.DELETED_AT), new IndexOptions().sparse(true));

        MongoCollection<Document> items = mongo.getCollection(MongoItemRepository.COLLECTION);
        items.createIndex(Indexes.ascending(MongoFields.TENANT_ID, MongoFields.COURSE_ID, MongoFields.POSITION));
        items.createIndex(Indexes.ascending(MongoFields.TENANT_ID, MongoFields.MODULE_ID, MongoFields.POSITION));
        items.createIndex(Indexes.ascending(MongoFields.TENANT_ID, MongoFields.COURSE_ID, MongoFields.DUE_AT));
        items.createIndex(Indexes.ascending(MongoFields.DUE_AT), new IndexOptions().sparse(true));
        items.createIndex(Indexes.ascending(MongoFields.DELETED_AT), new IndexOptions().sparse(true));
        log.info("Courses MongoDB indexes ensured");
    }
}
