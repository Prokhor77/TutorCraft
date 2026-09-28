package com.tutorcraft.core.gradebook.web;

import com.tutorcraft.core.gradebook.application.GradeEditService;
import com.tutorcraft.core.gradebook.application.GradebookExportService;
import com.tutorcraft.core.gradebook.application.GradebookExportService.ExportFile;
import com.tutorcraft.core.gradebook.application.GradebookSetupService;
import com.tutorcraft.core.gradebook.application.GradebookSetupService.CategoryInput;
import com.tutorcraft.core.gradebook.application.GradebookSetupService.ItemInput;
import com.tutorcraft.core.gradebook.application.GradebookSetupService.SetupCommand;
import com.tutorcraft.core.gradebook.application.GradebookTableService;
import com.tutorcraft.core.gradebook.application.GradebookViews;
import com.tutorcraft.core.shared.api.IfMatch;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** Журнал оценок курса (контракт §9). */
@RestController
@RequestMapping("/api/v1")
class GradebookController {

    private static final int MAX_NAME = 200;
    private static final String DEFAULT_FORMAT = "csv";
    private static final int MAX_CATEGORIES = 50;

    private final GradebookTableService table;
    private final GradebookSetupService setup;
    private final GradeEditService edits;
    private final GradebookExportService exports;

    GradebookController(GradebookTableService table, GradebookSetupService setup, GradeEditService edits,
                        GradebookExportService exports) {
        this.table = table;
        this.setup = setup;
        this.edits = edits;
        this.exports = exports;
    }

    @GetMapping("/courses/{courseId}/gradebook")
    GradebookViews.Gradebook gradebook(@PathVariable UUID courseId, @RequestParam(required = false) UUID groupId) {
        return table.gradebook(courseId, groupId);
    }

    @GetMapping("/courses/{courseId}/gradebook/setup")
    GradebookViews.Setup getSetup(@PathVariable UUID courseId) {
        return setup.setup(courseId);
    }

    @PutMapping("/courses/{courseId}/gradebook/setup")
    GradebookViews.Setup putSetup(@PathVariable UUID courseId, @Valid @RequestBody SetupRequest request) {
        return setup.update(courseId, new SetupCommand(request.aggregation(), request.categories(), request.items(),
                request.scaleId()));
    }

    @PostMapping("/courses/{courseId}/gradebook/manual-items")
    @ResponseStatus(HttpStatus.CREATED)
    GradebookViews.SetupItem addManualItem(@PathVariable UUID courseId, @Valid @RequestBody ManualItemRequest request) {
        return setup.addManualItem(courseId, request.name(), request.maxScore(), request.categoryId());
    }

    @PutMapping("/courses/{courseId}/gradebook/cells")
    GradebookViews.Cell upsertCell(@PathVariable UUID courseId, @Valid @RequestBody CellRequest request) {
        return edits.upsertCell(courseId, request.gradeItemId(), request.userId(), request.score());
    }

    @PatchMapping("/grades/{gradeId}")
    GradebookViews.Cell patchGrade(@PathVariable UUID gradeId, @RequestHeader(value = IfMatch.HEADER, required = false) String ifMatch,
                                   @RequestBody GradePatchRequest request) {
        return edits.override(gradeId, request.score(), request.locked(), IfMatch.resolve(ifMatch, request.version()));
    }

    @GetMapping("/grades/{gradeId}/history")
    List<GradebookViews.HistoryEntry> history(@PathVariable UUID gradeId) {
        return table.history(gradeId);
    }

    @GetMapping("/courses/{courseId}/gradebook/export")
    ResponseEntity<StreamingResponseBody> export(@PathVariable UUID courseId,
                                                 @RequestParam(defaultValue = DEFAULT_FORMAT) String format) {
        ExportFile file = exports.export(courseId, format);
        ContentDisposition disposition = ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build();
        StreamingResponseBody body = out -> file.body().writeTo(out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(body);
    }

    record SetupRequest(@NotBlank String aggregation, @Size(max = MAX_CATEGORIES) List<@Valid @NotNull CategoryInput> categories,
                        List<@Valid @NotNull ItemInput> items, UUID scaleId) {
    }

    record ManualItemRequest(@NotBlank @Size(max = MAX_NAME) String name, @NotNull BigDecimal maxScore, UUID categoryId) {
    }

    record CellRequest(@NotNull UUID gradeItemId, @NotNull UUID userId, BigDecimal score) {
    }

    record GradePatchRequest(BigDecimal score, Boolean locked, Long version) {
    }
}
