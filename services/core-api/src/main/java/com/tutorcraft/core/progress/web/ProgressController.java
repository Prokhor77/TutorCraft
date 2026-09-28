package com.tutorcraft.core.progress.web;

import com.tutorcraft.core.progress.application.CompletionQueryService;
import com.tutorcraft.core.progress.application.CompletionQueryService.MyCompletion;
import com.tutorcraft.core.progress.application.ManualCompletionService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Выполнение и отчёт о прогрессе (контракт §5 /items/{id}/complete, §12). */
@RestController
@RequestMapping("/api/v1")
class ProgressController {

    private static final String CSV_FORMAT = "csv";
    private static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);
    private static final String CSV_FILE_NAME = "attachment; filename=\"progress.csv\"";

    private final CompletionQueryService queries;
    private final ManualCompletionService manual;

    ProgressController(CompletionQueryService queries, ManualCompletionService manual) {
        this.queries = queries;
        this.manual = manual;
    }

    @GetMapping("/courses/{courseId}/completion/me")
    MyCompletion mine(@PathVariable UUID courseId) {
        return queries.mine(courseId);
    }

    @GetMapping("/courses/{courseId}/reports/progress")
    ResponseEntity<?> report(@PathVariable UUID courseId, @RequestParam(required = false) UUID groupId,
                             @RequestParam(required = false) String format) {
        if (CSV_FORMAT.equalsIgnoreCase(format)) {
            return ResponseEntity.ok().contentType(TEXT_CSV).header(HttpHeaders.CONTENT_DISPOSITION, CSV_FILE_NAME)
                    .body(queries.reportCsv(courseId, groupId).getBytes(StandardCharsets.UTF_8));
        }
        return ResponseEntity.ok(queries.report(courseId, groupId));
    }

    @PostMapping("/items/{itemId}/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void complete(@PathVariable UUID itemId) {
        manual.mark(itemId, true);
    }

    @DeleteMapping("/items/{itemId}/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void uncomplete(@PathVariable UUID itemId) {
        manual.mark(itemId, false);
    }
}
