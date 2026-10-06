package com.tutorcraft.core.files.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.files.application.FileRepository.PurposeUsageRow;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Занятое школами место в хранилище — для главного администратора платформы (право platform.manage). Объём считается
 * так же, как квота: размеры всех непринятых к отказу загрузок; HLS-рендишены видео — отдельной оценкой.
 */
@Service
public class StorageUsageService {

    private final FileRepository files;
    private final AccessService access;

    StorageUsageService(FileRepository files, AccessService access) {
        this.files = files;
        this.access = access;
    }

    /** Школы, у которых есть файлы, по убыванию занятого места. */
    @Transactional(readOnly = true)
    public List<TenantStorageView> byTenant() {
        access.require(Permission.PLATFORM_MANAGE, AccessContext.tenant());
        Map<UUID, Long> hls = files.hlsBytesByTenant();
        Map<UUID, List<PurposeUsageRow>> rows = files.usageByTenantAndPurpose().stream()
                .collect(Collectors.groupingBy(PurposeUsageRow::tenantId));
        return rows.entrySet().stream()
                .map(entry -> toView(entry.getKey(), entry.getValue(), hls.getOrDefault(entry.getKey(), 0L)))
                .sorted(Comparator.comparingLong(TenantStorageView::usedBytes).reversed())
                .toList();
    }

    private static TenantStorageView toView(UUID tenantId, List<PurposeUsageRow> rows, long hlsBytes) {
        List<PurposeUsage> byPurpose = rows.stream()
                .map(row -> new PurposeUsage(row.purpose(), row.bytes(), row.files()))
                .sorted(Comparator.comparingLong(PurposeUsage::bytes).reversed())
                .toList();
        long used = byPurpose.stream().mapToLong(PurposeUsage::bytes).sum();
        long count = byPurpose.stream().mapToLong(PurposeUsage::files).sum();
        return new TenantStorageView(tenantId, used, count, hlsBytes, byPurpose);
    }

    public record TenantStorageView(UUID tenantId, long usedBytes, long filesCount, long hlsBytes,
                                    List<PurposeUsage> byPurpose) {
    }

    public record PurposeUsage(String purpose, long bytes, long files) {
    }
}
