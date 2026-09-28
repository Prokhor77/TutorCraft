package com.tutorcraft.core.files.infrastructure;

import com.tutorcraft.core.files.application.FileRepository;
import com.tutorcraft.core.files.domain.FilePurpose;
import com.tutorcraft.core.files.domain.FileStatus;
import com.tutorcraft.core.files.domain.StoredFile;
import com.tutorcraft.core.shared.persistence.Jsonb;
import com.tutorcraft.core.shared.persistence.Timestamps;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcFileRepository implements FileRepository {

    private static final String COLUMNS = """
            id, tenant_id, uploaded_by, name, size_bytes, declared_mime, mime, purpose, status, storage_key, sha256, created_at
            """;
    private static final long BYTES_PER_MB = 1024L * 1024;

    private final JdbcClient jdbc;

    JdbcFileRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(StoredFile file) {
        jdbc.sql("""
                INSERT INTO files (id, tenant_id, uploaded_by, name, size_bytes, declared_mime, mime, purpose, status,
                                   storage_key, created_at)
                VALUES (:id, :tenantId, :uploadedBy, :name, :size, :declaredMime, :mime, :purpose, :status, :key, :createdAt)
                """)
            .param("id", file.id()).param("tenantId", file.tenantId()).param("uploadedBy", file.uploadedBy())
            .param("name", file.name()).param("size", file.sizeBytes()).param("declaredMime", file.declaredMime())
            .param("mime", file.mime()).param("purpose", file.purpose().key()).param("status", file.status().key())
            .param("key", file.storageKey()).param("createdAt", Timestamps.of(file.createdAt()))
            .update();
    }

    @Override
    public Optional<StoredFile> find(UUID tenantId, UUID fileId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM files WHERE tenant_id = :tenantId AND id = :id")
                .param("tenantId", tenantId).param("id", fileId)
                .query((rs, n) -> toFile(rs)).optional();
    }

    @Override
    public List<StoredFile> findAll(UUID tenantId, Collection<UUID> fileIds) {
        if (fileIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT " + COLUMNS + " FROM files WHERE tenant_id = :tenantId AND id IN (:ids)")
                .param("tenantId", tenantId).param("ids", List.copyOf(fileIds))
                .query((rs, n) -> toFile(rs)).list();
    }

    @Override
    public Optional<StoredFile> findReadyByHash(UUID tenantId, String sha256) {
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM files WHERE tenant_id = :tenantId AND sha256 = :sha AND status = 'ready'
                ORDER BY created_at LIMIT 1
                """)
                .param("tenantId", tenantId).param("sha", sha256)
                .query((rs, n) -> toFile(rs)).optional();
    }

    @Override
    public boolean markReady(UUID tenantId, UUID fileId, String mime, String sha256, String storageKey, Instant completedAt) {
        return jdbc.sql("""
                UPDATE files SET status = 'ready', mime = :mime, sha256 = :sha, storage_key = :key, completed_at = :at
                WHERE tenant_id = :tenantId AND id = :id AND status = 'pending'
                """)
            .param("mime", mime).param("sha", sha256).param("key", storageKey).param("at", Timestamps.of(completedAt))
            .param("tenantId", tenantId).param("id", fileId)
            .update() == 1;
    }

    @Override
    public void markRejected(UUID tenantId, UUID fileId, String reason, Instant at) {
        jdbc.sql("""
                UPDATE files SET status = 'rejected', reject_reason = :reason, completed_at = :at
                WHERE tenant_id = :tenantId AND id = :id AND status = 'pending'
                """)
            .param("reason", reason).param("at", Timestamps.of(at)).param("tenantId", tenantId).param("id", fileId)
            .update();
    }

    @Override
    public long usedBytes(UUID tenantId) {
        return jdbc.sql("SELECT CAST(COALESCE(SUM(size_bytes), 0) AS bigint) FROM files WHERE tenant_id = :tenantId AND status <> 'rejected'")
                .param("tenantId", tenantId)
                .query(Long.class).single();
    }

    @Override
    public Optional<Long> storageQuotaBytes(UUID tenantId) {
        return jdbc.sql("SELECT quota_storage_mb FROM tenants WHERE id = :tenantId AND quota_storage_mb IS NOT NULL")
                .param("tenantId", tenantId)
                .query((rs, n) -> rs.getLong("quota_storage_mb") * BYTES_PER_MB)
                .optional();
    }

    @Override
    public void insertLink(UUID tenantId, UUID fileId, String ownerType, UUID ownerId, Instant at) {
        jdbc.sql("""
                INSERT INTO file_links (tenant_id, file_id, owner_type, owner_id, created_at)
                VALUES (:tenantId, :fileId, :ownerType, :ownerId, :at)
                ON CONFLICT DO NOTHING
                """)
            .param("tenantId", tenantId).param("fileId", fileId).param("ownerType", ownerType).param("ownerId", ownerId)
            .param("at", Timestamps.of(at))
            .update();
    }

    @Override
    public List<FileLink> links(UUID tenantId, UUID fileId) {
        return jdbc.sql("SELECT owner_type, owner_id FROM file_links WHERE tenant_id = :tenantId AND file_id = :fileId")
                .param("tenantId", tenantId).param("fileId", fileId)
                .query((rs, n) -> new FileLink(rs.getString("owner_type"), rs.getObject("owner_id", UUID.class)))
                .list();
    }

    @Override
    public void insertVideo(UUID tenantId, UUID fileId, Instant at) {
        jdbc.sql("""
                INSERT INTO videos (file_id, tenant_id, status, created_at, updated_at)
                VALUES (:fileId, :tenantId, 'processing', :at, :at)
                ON CONFLICT (file_id) DO NOTHING
                """)
            .param("fileId", fileId).param("tenantId", tenantId).param("at", Timestamps.of(at))
            .update();
    }

    @Override
    public Optional<VideoRecord> findVideo(UUID tenantId, UUID fileId) {
        return jdbc.sql("""
                SELECT file_id, status, master_playlist_key, duration_sec FROM videos
                WHERE tenant_id = :tenantId AND file_id = :fileId
                """)
            .param("tenantId", tenantId).param("fileId", fileId)
            .query((rs, n) -> new VideoRecord(rs.getObject("file_id", UUID.class), rs.getString("status"),
                    rs.getString("master_playlist_key"), rs.getObject("duration_sec", Integer.class)))
            .optional();
    }

    @Override
    public boolean updateVideo(UUID tenantId, UUID fileId, VideoUpdate update, Instant at) {
        return jdbc.sql("""
                UPDATE videos SET status = :status, hls_prefix = :prefix, master_playlist_key = :master,
                       duration_sec = :duration, renditions = :renditions, error = :error, updated_at = :at
                WHERE tenant_id = :tenantId AND file_id = :fileId
                """)
            .param("status", update.status()).param("prefix", update.hlsPrefix()).param("master", update.masterPlaylistKey())
            .param("duration", update.durationSec()).param("renditions", Jsonb.of(update.renditionsJson()))
            .param("error", update.error()).param("at", Timestamps.of(at))
            .param("tenantId", tenantId).param("fileId", fileId)
            .update() == 1;
    }

    private static StoredFile toFile(ResultSet rs) throws SQLException {
        return new StoredFile(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("uploaded_by", UUID.class), rs.getString("name"), rs.getLong("size_bytes"),
                rs.getString("declared_mime"), rs.getString("mime"),
                FilePurpose.find(rs.getString("purpose")).orElseThrow(() -> new IllegalStateException("Unknown purpose")),
                FileStatus.fromKey(rs.getString("status")), rs.getString("storage_key"), rs.getString("sha256"),
                Timestamps.read(rs, "created_at"));
    }
}
