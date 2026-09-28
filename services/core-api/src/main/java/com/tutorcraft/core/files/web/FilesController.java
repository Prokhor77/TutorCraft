package com.tutorcraft.core.files.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tutorcraft.core.files.application.FileMetaView;
import com.tutorcraft.core.files.application.FileQueryService;
import com.tutorcraft.core.files.application.UploadCompletionService;
import com.tutorcraft.core.files.application.UploadService;
import com.tutorcraft.core.files.application.UploadService.UploadRequest;
import com.tutorcraft.core.files.application.UploadService.UploadTicket;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Файлы (контракт §7). */
@RestController
@RequestMapping("/api/v1/files")
class FilesController {

    private static final int MAX_NAME = 255;
    private static final int MAX_TYPE = 255;
    private static final int MAX_PURPOSE = 32;

    private final UploadService uploads;
    private final UploadCompletionService completion;
    private final FileQueryService queries;

    FilesController(UploadService uploads, UploadCompletionService completion, FileQueryService queries) {
        this.uploads = uploads;
        this.completion = completion;
        this.queries = queries;
    }

    @PostMapping("/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    UploadTicket createUpload(@Valid @RequestBody CreateUploadRequest request) {
        return uploads.createUpload(new UploadRequest(request.fileName(), request.contentType(), request.size(), request.purpose()));
    }

    @PostMapping("/{id}/complete")
    FileMeta complete(@PathVariable UUID id) {
        return FileMeta.of(completion.complete(id));
    }

    @GetMapping("/{id}")
    FileMeta get(@PathVariable UUID id) {
        return FileMeta.of(queries.meta(id));
    }

    @GetMapping("/{id}/download")
    ResponseEntity<Void> download(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(queries.downloadUrl(id))).build();
    }

    record CreateUploadRequest(@NotBlank @Size(max = MAX_NAME) String fileName, @NotBlank @Size(max = MAX_TYPE) String contentType,
                               @NotNull @Positive Long size, @NotBlank @Size(max = MAX_PURPOSE) String purpose) {
    }

    /** FileMeta: поле video присутствует только у видеофайлов. */
    record FileMeta(UUID id, String name, long size, String mime, String status, String url,
                    @JsonInclude(JsonInclude.Include.NON_NULL) FileMetaView.VideoView video) {

        static FileMeta of(FileMetaView view) {
            return new FileMeta(view.id(), view.name(), view.size(), view.mime(), view.status(), view.url(), view.video());
        }
    }
}
