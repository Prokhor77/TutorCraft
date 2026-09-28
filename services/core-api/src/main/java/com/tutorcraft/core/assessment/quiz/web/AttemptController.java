package com.tutorcraft.core.assessment.quiz.web;

import com.tutorcraft.core.assessment.quiz.application.AttemptResultService;
import com.tutorcraft.core.assessment.quiz.application.AttemptService;
import com.tutorcraft.core.assessment.quiz.application.EssayGradingService;
import com.tutorcraft.core.assessment.quiz.application.QuizViews.AttemptResultView;
import com.tutorcraft.core.assessment.quiz.application.QuizViews.AttemptView;
import com.tutorcraft.core.assessment.quiz.application.QuizViews.SavedAnswerView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
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

/** Прохождение и проверка попытки (контракт §10). */
@RestController
@RequestMapping("/api/v1/attempts/{attemptId}")
class AttemptController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final AttemptService attempts;
    private final AttemptResultService results;
    private final EssayGradingService essays;

    AttemptController(AttemptService attempts, AttemptResultService results, EssayGradingService essays) {
        this.attempts = attempts;
        this.results = results;
        this.essays = essays;
    }

    @GetMapping
    AttemptView get(@PathVariable UUID attemptId) {
        return attempts.get(attemptId);
    }

    @PutMapping("/answers/{slot}")
    SavedAnswerView saveAnswer(@PathVariable UUID attemptId, @PathVariable int slot, @RequestBody AnswerBody body) {
        return attempts.saveAnswer(attemptId, slot, body.response(), body.flagged());
    }

    @PostMapping("/finish")
    AttemptResultView finish(@PathVariable UUID attemptId, @RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey) {
        return attempts.finish(attemptId, idempotencyKey);
    }

    @GetMapping("/result")
    AttemptResultView result(@PathVariable UUID attemptId) {
        return results.result(attemptId);
    }

    @PostMapping("/answers/{slot}/grade")
    AttemptResultView gradeEssay(@PathVariable UUID attemptId, @PathVariable int slot, @Valid @RequestBody GradeBody body) {
        return essays.grade(attemptId, slot, body.score(), body.comment());
    }

    /** response == null — только пометка «вернуться позже». */
    record AnswerBody(Map<String, Object> response, Boolean flagged) {
    }

    record GradeBody(@NotNull BigDecimal score, String comment) {
    }
}
