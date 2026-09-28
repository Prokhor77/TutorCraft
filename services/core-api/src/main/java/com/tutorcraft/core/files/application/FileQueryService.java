package com.tutorcraft.core.files.application;

import com.tutorcraft.core.files.domain.StoredFile;
import com.tutorcraft.core.shared.domain.BusinessRuleException;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Метаданные и скачивание файла с проверкой права чтения. Нет права или чужой tenant — 404. */
@Service
public class FileQueryService {

    private final FileRepository files;
    private final FileAccessPolicy accessPolicy;
    private final FileViews views;
    private final FileUrls urls;
    private final CurrentUserProvider currentUser;

    public FileQueryService(FileRepository files, FileAccessPolicy accessPolicy, FileViews views, FileUrls urls,
                            CurrentUserProvider currentUser) {
        this.files = files;
        this.accessPolicy = accessPolicy;
        this.views = views;
        this.urls = urls;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public FileMetaView meta(UUID fileId) {
        return views.view(requireReadable(fileId));
    }

    @Transactional(readOnly = true)
    public String downloadUrl(UUID fileId) {
        StoredFile file = requireReadable(fileId);
        if (!file.isReady()) {
            throw new BusinessRuleException(FilesErrors.NOT_READY, "File is not ready");
        }
        return urls.download(file);
    }

    private StoredFile requireReadable(UUID fileId) {
        CurrentUser user = currentUser.require();
        return files.find(user.tenantId(), fileId)
                .filter(file -> accessPolicy.canRead(user.userId(), file))
                .orElseThrow(() -> new NotFoundException(FilesErrors.NOT_FOUND, "File not found"));
    }
}
