package com.tutorcraft.core.assessment.assignment.application;

import com.tutorcraft.core.assessment.assignment.application.BlockDocInput.SanitizedText;
import com.tutorcraft.core.assessment.assignment.application.StudentSubmissionService.DraftCommand;
import com.tutorcraft.core.assessment.assignment.application.StudentSubmissionService.StudentContext;
import com.tutorcraft.core.assessment.assignment.domain.AssignmentSettingsParser;
import com.tutorcraft.core.assessment.assignment.domain.BlockDocContent;
import com.tutorcraft.core.assessment.assignment.domain.Submission;
import com.tutorcraft.core.assessment.assignment.domain.SubmissionContentRules;
import com.tutorcraft.core.assessment.assignment.domain.SubmissionContentRules.SubmittedFile;
import com.tutorcraft.core.files.FilesApi;
import com.tutorcraft.core.files.FilesApi.FileRef;
import com.tutorcraft.core.shared.domain.ValidationException;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Проверка и применение содержимого черновика: файлы (FilesApi), текст (санитайзер BlockDoc), связи FileLink. */
@Component
class SubmissionContent {

    static final String OWNER_TYPE = "submission";
    private static final String FILES_FIELD = "fileIds";
    private static final String TEXT_FIELD = "text";

    private final FilesApi files;
    private final BlockDocInput blockDocs;

    SubmissionContent(FilesApi files, BlockDocInput blockDocs) {
        this.files = files;
        this.blockDocs = blockDocs;
    }

    Submission apply(StudentContext context, Submission current, DraftCommand command, Instant now) {
        SanitizedText text = command.text() == null
                ? new SanitizedText(current.text(), Set.of())
                : blockDocs.sanitize(context.tenantId(), command.text(), TEXT_FIELD);
        List<UUID> fileIds = command.fileIds() == null ? current.fileIds() : distinct(command.fileIds());
        if (fileIds.size() > AssignmentSettingsParser.MAX_FILES_LIMIT) {
            throw ValidationException.single(FILES_FIELD, "too_many_files", "Too many files");
        }
        List<FileRef> attached = files.requireAllReady(context.tenantId(), fileIds, FILES_FIELD);
        List<FileRef> embedded = files.requireAllReady(context.tenantId(), text.fileIds(), TEXT_FIELD);
        requireOwned(attached, context.userId(), current.fileIds(), FILES_FIELD);
        requireOwned(embedded, context.userId(), current.fileIds(), TEXT_FIELD);
        SubmissionContentRules.validate(context.assignment().settings(),
                attached.stream().map(file -> new SubmittedFile(file.name(), file.size())).toList(),
                BlockDocContent.hasText(text.doc()));
        link(context.tenantId(), current.id(), attached, embedded);
        return current.withContent(text.doc(), fileIds, now);
    }

    /** Прикреплять можно только свои файлы (или уже прикреплённые участником группы). */
    private static void requireOwned(List<FileRef> refs, UUID userId, Collection<UUID> alreadyAttached, String field) {
        boolean owned = refs.stream().allMatch(file -> userId.equals(file.uploadedBy()) || alreadyAttached.contains(file.id()));
        if (!owned) {
            throw ValidationException.single(field, "file_not_owned", "You can attach only your own files");
        }
    }

    private void link(UUID tenantId, UUID submissionId, List<FileRef> attached, List<FileRef> embedded) {
        Set<UUID> ids = new LinkedHashSet<>();
        attached.forEach(file -> ids.add(file.id()));
        embedded.forEach(file -> ids.add(file.id()));
        ids.forEach(fileId -> files.link(tenantId, fileId, OWNER_TYPE, submissionId));
    }

    private static List<UUID> distinct(List<UUID> ids) {
        return ids.stream().distinct().toList();
    }
}
