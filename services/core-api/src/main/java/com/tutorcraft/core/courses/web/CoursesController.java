package com.tutorcraft.core.courses.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutorcraft.core.courses.application.CourseCardView;
import com.tutorcraft.core.courses.application.CourseCommandService;
import com.tutorcraft.core.courses.application.CourseCommands.CoursePatch;
import com.tutorcraft.core.courses.application.CourseCommands.CreateCourse;
import com.tutorcraft.core.courses.application.CourseQueryService;
import com.tutorcraft.core.courses.application.CourseStructureQueries;
import com.tutorcraft.core.courses.application.CourseView;
import com.tutorcraft.core.courses.application.DuplicationService;
import com.tutorcraft.core.courses.application.OutlineView;
import com.tutorcraft.core.courses.application.TrashEntryView;
import com.tutorcraft.core.courses.application.TrashService;
import com.tutorcraft.core.courses.domain.CourseCompletionRule;
import com.tutorcraft.core.courses.domain.CourseTexts;
import com.tutorcraft.core.courses.domain.SelfEnrolSettings;
import com.tutorcraft.core.shared.api.IfMatch;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Курсы, оглавление, корзина (контракт §5) и цена (§13). */
@RestController
@RequestMapping("/api/v1")
class CoursesController {

    private final CourseQueryService queries;
    private final CourseCommandService commands;
    private final DuplicationService duplication;
    private final CourseStructureQueries structure;
    private final TrashService trash;
    private final ObjectMapper mapper;

    CoursesController(CourseQueryService queries, CourseCommandService commands, DuplicationService duplication,
                      CourseStructureQueries structure, TrashService trash, ObjectMapper mapper) {
        this.queries = queries;
        this.commands = commands;
        this.duplication = duplication;
        this.structure = structure;
        this.trash = trash;
        this.mapper = mapper;
    }

    @GetMapping("/courses")
    PageResponse<CourseCardView> list(@RequestParam(required = false) String q,
                                      @RequestParam(required = false) UUID categoryId,
                                      @RequestParam(defaultValue = "false") boolean mine,
                                      @RequestParam(required = false) String cursor,
                                      @RequestParam(required = false) Integer limit) {
        return queries.list(q, categoryId, mine, PageQuery.of(cursor, limit));
    }

    @PostMapping("/courses")
    @ResponseStatus(HttpStatus.CREATED)
    CourseView create(@Valid @RequestBody CreateCourseRequest request) {
        return commands.create(new CreateCourse(request.title(), request.shortName(), request.categoryId(),
                request.description(), request.startsAt(), request.endsAt(), request.coverFileId()));
    }

    @GetMapping("/courses/{id}")
    CourseView get(@PathVariable UUID id) {
        return queries.get(id);
    }

    @PatchMapping("/courses/{id}")
    CourseView update(@PathVariable UUID id, @RequestHeader(value = IfMatch.HEADER, required = false) String ifMatch,
                      @RequestBody JsonNode body) {
        PatchBody patch = PatchBody.of(body, mapper);
        long version = IfMatch.resolve(ifMatch, patch.version());
        return commands.update(id, toPatch(patch), version);
    }

    @DeleteMapping("/courses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        commands.delete(id);
    }

    @PostMapping("/courses/{id}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void restore(@PathVariable UUID id) {
        commands.restore(id);
    }

    @PostMapping("/courses/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    CourseView duplicate(@PathVariable UUID id) {
        return duplication.duplicateCourse(id);
    }

    @GetMapping("/courses/{id}/outline")
    OutlineView outline(@PathVariable UUID id) {
        return structure.outline(id);
    }

    @GetMapping("/trash")
    List<TrashEntryView> trash(@RequestParam(required = false) UUID courseId) {
        return trash.list(courseId);
    }

    private static CoursePatch toPatch(PatchBody body) {
        return new CoursePatch(body.text("title"), body.text("shortName"), body.uuid("categoryId"), body.raw("description"),
                body.uuid("coverFileId"), body.instant("startsAt"), body.instant("endsAt"), body.text("visibility"),
                body.instant("publishAt"), body.value("selfEnrol", SelfEnrolSettings.class),
                body.value("completionRule", CourseCompletionRule.class), body.text("groupMode"));
    }

    record CreateCourseRequest(@NotBlank @Size(max = CourseTexts.MAX_TITLE) String title,
                               @Size(max = CourseTexts.MAX_SHORT_NAME) String shortName, UUID categoryId,
                               Map<String, Object> description, Instant startsAt, Instant endsAt, UUID coverFileId) {
    }
}
