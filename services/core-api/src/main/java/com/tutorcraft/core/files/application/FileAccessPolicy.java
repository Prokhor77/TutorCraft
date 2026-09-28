package com.tutorcraft.core.files.application;

import com.tutorcraft.core.files.domain.StoredFile;
import com.tutorcraft.core.files.spi.FileOwnerAccess;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Право чтения файла: загрузивший пользователь или любой, кому модуль-владелец связи (FileLink)
 * разрешает чтение владельца (NFR-SEC-02, объектный уровень).
 */
@Component
public class FileAccessPolicy {

    private final FileRepository files;
    private final Map<String, FileOwnerAccess> ownerAccess;

    public FileAccessPolicy(FileRepository files, List<FileOwnerAccess> ownerAccess) {
        this.files = files;
        this.ownerAccess = ownerAccess.stream()
                .collect(Collectors.toUnmodifiableMap(FileOwnerAccess::ownerType, Function.identity()));
    }

    public boolean canRead(UUID userId, StoredFile file) {
        if (file.uploadedBy(userId)) {
            return true;
        }
        return files.links(file.tenantId(), file.id()).stream().anyMatch(link -> {
            FileOwnerAccess access = ownerAccess.get(link.ownerType());
            return access != null && access.canRead(file.tenantId(), userId, link.ownerId());
        });
    }
}
