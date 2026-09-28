package com.tutorcraft.core.assessment.assignment.web;

import com.tutorcraft.core.assessment.assignment.application.StudentSubmissionService;
import com.tutorcraft.core.assessment.assignment.application.StudentSubmissionService.DraftCommand;
import com.tutorcraft.core.assessment.assignment.application.SubmissionView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Сдача задания студентом (контракт §8). */
@RestController
@RequestMapping("/api/v1/items/{itemId}/my-submission")
class StudentSubmissionController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final int MAX_FILES = 20;

    private final StudentSubmissionService submissions;

    StudentSubmissionController(StudentSubmissionService submissions) {
        this.submissions = submissions;
    }

    @GetMapping
    SubmissionView mySubmission(@PathVariable UUID itemId) {
        return submissions.mySubmission(itemId);
    }

    @PutMapping("/draft")
    SubmissionView saveDraft(@PathVariable UUID itemId, @Valid @RequestBody DraftRequest request) {
        return submissions.saveDraft(itemId, new DraftCommand(request.text(), request.fileIds()));
    }

    @PostMapping("/submit")
    SubmissionView submit(@PathVariable UUID itemId, @RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey) {
        return submissions.submit(itemId, idempotencyKey);
    }

    /** Отсутствующее поле не меняет сохранённое значение; пустой список/документ — очищает. */
    record DraftRequest(Map<String, Object> text, @Size(max = MAX_FILES) List<@NotNull UUID> fileIds) {
    }
}
