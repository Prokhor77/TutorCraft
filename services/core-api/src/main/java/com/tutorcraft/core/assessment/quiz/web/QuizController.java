package com.tutorcraft.core.assessment.quiz.web;

import com.tutorcraft.core.assessment.quiz.application.AttemptStartService;
import com.tutorcraft.core.assessment.quiz.application.QuizLayoutService;
import com.tutorcraft.core.assessment.quiz.application.QuizOverrideService;
import com.tutorcraft.core.assessment.quiz.application.QuizOverrideService.OverrideRequest;
import com.tutorcraft.core.assessment.quiz.application.QuizRegradeService;
import com.tutorcraft.core.assessment.quiz.application.QuizReportService;
import com.tutorcraft.core.assessment.quiz.application.QuizViews.AttemptSummaryView;
import com.tutorcraft.core.assessment.quiz.application.QuizViews.AttemptView;
import com.tutorcraft.core.assessment.quiz.application.QuizViews.LayoutView;
import com.tutorcraft.core.assessment.quiz.application.QuizViews.OverrideView;
import com.tutorcraft.core.assessment.quiz.application.QuizViews.RegradeView;
import com.tutorcraft.core.assessment.quiz.domain.LayoutSlot;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import com.tutorcraft.core.shared.domain.ValidationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

/** Тест как элемент курса: состав, попытки, отчёт, переоценка, исключения (контракт §10). */
@RestController
@RequestMapping("/api/v1/items/{itemId}")
class QuizController {

    private static final int MAX_SLOTS = 200;

    private final QuizLayoutService layouts;
    private final AttemptStartService starts;
    private final QuizReportService reports;
    private final QuizRegradeService regrades;
    private final QuizOverrideService overrides;

    QuizController(QuizLayoutService layouts, AttemptStartService starts, QuizReportService reports,
                   QuizRegradeService regrades, QuizOverrideService overrides) {
        this.layouts = layouts;
        this.starts = starts;
        this.reports = reports;
        this.regrades = regrades;
        this.overrides = overrides;
    }

    @GetMapping("/quiz/slots")
    LayoutView slots(@PathVariable UUID itemId) {
        return layouts.get(itemId);
    }

    @PutMapping("/quiz/slots")
    LayoutView replaceSlots(@PathVariable UUID itemId, @Valid @RequestBody SlotsRequest request) {
        return layouts.replace(itemId, request.toSlots());
    }

    /** Начать новую или продолжить незавершённую попытку. */
    @PostMapping("/attempts")
    AttemptView startAttempt(@PathVariable UUID itemId) {
        return starts.startOrContinue(itemId);
    }

    @GetMapping("/attempts")
    PageResponse<AttemptSummaryView> attempts(@PathVariable UUID itemId, @RequestParam(required = false) String cursor,
                                              @RequestParam(required = false) Integer limit) {
        return reports.attempts(itemId, PageQuery.of(cursor, limit));
    }

    @PostMapping("/regrade")
    RegradeView regrade(@PathVariable UUID itemId) {
        return regrades.regrade(itemId);
    }

    @PostMapping("/overrides")
    @ResponseStatus(HttpStatus.CREATED)
    OverrideView saveOverride(@PathVariable UUID itemId, @RequestBody OverrideBody body) {
        return overrides.save(itemId, new OverrideRequest(body.userId(), body.groupId(), body.openAt(), body.closeAt(),
                body.timeLimitSec(), body.maxAttempts()));
    }

    @GetMapping("/overrides")
    List<OverrideView> listOverrides(@PathVariable UUID itemId) {
        return overrides.list(itemId);
    }

    @DeleteMapping("/overrides/{overrideId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteOverride(@PathVariable UUID itemId, @PathVariable UUID overrideId) {
        overrides.delete(itemId, overrideId);
    }

    record SlotsRequest(@NotNull @Size(max = MAX_SLOTS) List<SlotBody> slots) {

        List<LayoutSlot> toSlots() {
            List<LayoutSlot> result = new ArrayList<>();
            for (int i = 0; i < slots.size(); i++) {
                result.add(slots.get(i).toSlot("slots[" + i + "]"));
            }
            return result;
        }
    }

    record SlotBody(UUID questionId, RandomBody random, BigDecimal points, Integer page) {

        LayoutSlot toSlot(String field) {
            if ((questionId == null) == (random == null)) {
                throw ValidationException.single(field, "exactly_one", "Specify either questionId or random");
            }
            return random == null ? new LayoutSlot.Fixed(questionId, points, page)
                    : new LayoutSlot.Random(random.categoryId(), random.tag(), random.count() == null ? 0 : random.count(),
                            points, page);
        }
    }

    record RandomBody(UUID categoryId, String tag, Integer count) {
    }

    record OverrideBody(UUID userId, UUID groupId, Instant openAt, Instant closeAt, Integer timeLimitSec, Integer maxAttempts) {
    }
}
