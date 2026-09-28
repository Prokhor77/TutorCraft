package com.tutorcraft.core.courses.infrastructure;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bson.Document;

/** Пакетная перенумерация позиций (одна bulk-операция, идемпотентна — ADR-004). */
final class MongoPositions {

    private MongoPositions() {
    }

    static void update(MongoCollection<Document> collection, UUID tenantId, Map<UUID, Integer> positions) {
        if (positions.isEmpty()) {
            return;
        }
        List<WriteModel<Document>> writes = positions.entrySet().stream()
                .<WriteModel<Document>>map(entry -> new UpdateOneModel<>(
                        and(eq(MongoFields.ID, entry.getKey()), eq(MongoFields.TENANT_ID, tenantId)),
                        Updates.set(MongoFields.POSITION, entry.getValue())))
                .toList();
        collection.bulkWrite(writes, new BulkWriteOptions().ordered(false));
    }
}
