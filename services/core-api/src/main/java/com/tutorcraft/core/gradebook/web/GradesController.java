package com.tutorcraft.core.gradebook.web;

import com.tutorcraft.core.gradebook.application.GradebookViews;
import com.tutorcraft.core.gradebook.application.GradingQueueService;
import com.tutorcraft.core.gradebook.application.MyGradesService;
import com.tutorcraft.core.gradebook.application.ScaleService;
import com.tutorcraft.core.gradebook.domain.ScaleLevel;
import com.tutorcraft.core.shared.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** «Мои оценки», очередь проверки и шкалы (контракт §8–9). */
@RestController
@RequestMapping("/api/v1")
class GradesController {

    private static final int MAX_NAME = 100;
    private static final int MAX_LEVELS = 20;

    private final MyGradesService myGrades;
    private final GradingQueueService queue;
    private final ScaleService scales;

    GradesController(MyGradesService myGrades, GradingQueueService queue, ScaleService scales) {
        this.myGrades = myGrades;
        this.queue = queue;
        this.scales = scales;
    }

    @GetMapping("/me/grades")
    GradebookViews.MyGradesOverview overview() {
        return myGrades.overview();
    }

    @GetMapping("/me/grades/{courseId}")
    GradebookViews.MyCourseGrades courseGrades(@PathVariable UUID courseId) {
        return myGrades.courseGrades(courseId);
    }

    @GetMapping("/grading-queue")
    PageResponse<GradebookViews.QueueEntry> gradingQueue(@RequestParam(required = false) UUID courseId,
                                                         @RequestParam(required = false) String type,
                                                         @RequestParam(required = false) String cursor,
                                                         @RequestParam(required = false) Integer limit) {
        return queue.queue(courseId, type, cursor, limit);
    }

    @GetMapping("/scales")
    List<GradebookViews.ScaleView> listScales(@RequestParam(required = false) UUID courseId) {
        return scales.list(courseId);
    }

    @PostMapping("/scales")
    @ResponseStatus(HttpStatus.CREATED)
    GradebookViews.ScaleView createScale(@Valid @RequestBody ScaleRequest request) {
        return scales.create(request.name(), request.levels(), request.courseId());
    }

    record ScaleRequest(@NotBlank @Size(max = MAX_NAME) String name,
                        @NotNull @Size(min = 1, max = MAX_LEVELS) List<@NotNull ScaleLevel> levels, UUID courseId) {
    }
}
