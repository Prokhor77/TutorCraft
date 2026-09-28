package com.tutorcraft.core.assessment.quiz.web;

import com.tutorcraft.core.assessment.quiz.application.QuestionBankService;
import com.tutorcraft.core.assessment.quiz.application.QuestionRepository.QuestionFilter;
import com.tutorcraft.core.assessment.quiz.application.QuizViews.CategoryView;
import com.tutorcraft.core.assessment.quiz.application.QuizViews.PreviewCheckView;
import com.tutorcraft.core.assessment.quiz.application.QuizViews.QuestionSummaryView;
import com.tutorcraft.core.assessment.quiz.application.QuizViews.QuestionVersionView;
import com.tutorcraft.core.assessment.quiz.application.QuizViews.QuestionView;
import com.tutorcraft.core.assessment.quiz.domain.QuestionDraft;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Банк вопросов (контракт §10). */
@RestController
@RequestMapping("/api/v1")
class QuestionBankController {

    private static final int MAX_NAME = 255;
    private static final int MAX_TYPE = 32;

    private final QuestionBankService bank;

    QuestionBankController(QuestionBankService bank) {
        this.bank = bank;
    }

    @GetMapping("/courses/{courseId}/question-bank/categories")
    List<CategoryView> categories(@PathVariable UUID courseId) {
        return bank.categories(courseId);
    }

    @PostMapping("/courses/{courseId}/question-bank/categories")
    @ResponseStatus(HttpStatus.CREATED)
    CategoryView createCategory(@PathVariable UUID courseId, @Valid @RequestBody CategoryRequest request) {
        return bank.createCategory(courseId, request.name(), request.parentId());
    }

    @GetMapping("/courses/{courseId}/questions")
    PageResponse<QuestionSummaryView> list(@PathVariable UUID courseId, @RequestParam(required = false) UUID categoryId,
                                           @RequestParam(required = false) String tag, @RequestParam(required = false) String type,
                                           @RequestParam(required = false) String q, @RequestParam(required = false) String cursor,
                                           @RequestParam(required = false) Integer limit) {
        return bank.list(courseId, new QuestionFilter(categoryId, tag, type, q), PageQuery.of(cursor, limit));
    }

    @PostMapping("/courses/{courseId}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    QuestionView create(@PathVariable UUID courseId, @Valid @RequestBody QuestionInput input) {
        return bank.create(courseId, input.toDraft());
    }

    @GetMapping("/questions/{id}")
    QuestionView get(@PathVariable UUID id) {
        return bank.get(id);
    }

    @PutMapping("/questions/{id}")
    QuestionView update(@PathVariable UUID id, @Valid @RequestBody QuestionInput input) {
        return bank.update(id, input.toDraft());
    }

    @DeleteMapping("/questions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        bank.delete(id);
    }

    @GetMapping("/questions/{id}/versions")
    List<QuestionVersionView> versions(@PathVariable UUID id) {
        return bank.versions(id);
    }

    @PostMapping("/questions/{id}/preview-check")
    PreviewCheckView previewCheck(@PathVariable UUID id, @Valid @RequestBody PreviewRequest request) {
        return bank.previewCheck(id, request.response());
    }

    record CategoryRequest(@NotBlank @Size(max = MAX_NAME) String name, UUID parentId) {
    }

    record QuestionInput(@NotBlank @Size(max = MAX_TYPE) String type, @NotBlank @Size(max = MAX_NAME) String title,
                         @NotNull Map<String, Object> body, @NotNull BigDecimal defaultScore, UUID categoryId,
                         List<String> tags, @NotNull Map<String, Object> data, Map<String, Object> generalFeedback) {

        QuestionDraft toDraft() {
            return QuestionDraft.of(type, title, body, defaultScore, categoryId, tags, data, generalFeedback);
        }
    }

    record PreviewRequest(@NotNull Map<String, Object> response) {
    }
}
