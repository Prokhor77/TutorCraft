package com.tutorcraft.core.courses.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutorcraft.core.courses.application.CourseCommands.ModulePatch;
import com.tutorcraft.core.courses.application.DuplicationService;
import com.tutorcraft.core.courses.application.ModuleService;
import com.tutorcraft.core.courses.application.OutlineView.ModuleView;
import com.tutorcraft.core.courses.domain.CourseTexts;
import com.tutorcraft.core.shared.api.IfMatch;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Модули курса (контракт §5; restore и duplicate — FR-COURSE-06/07). */
@RestController
@RequestMapping("/api/v1")
class ModulesController {

    private final ModuleService modules;
    private final DuplicationService duplication;
    private final ObjectMapper mapper;

    ModulesController(ModuleService modules, DuplicationService duplication, ObjectMapper mapper) {
        this.modules = modules;
        this.duplication = duplication;
        this.mapper = mapper;
    }

    @PostMapping("/courses/{courseId}/modules")
    @ResponseStatus(HttpStatus.CREATED)
    ModuleView create(@PathVariable UUID courseId, @Valid @RequestBody CreateModuleRequest request) {
        return modules.create(courseId, request.title(), request.parentId());
    }

    @PatchMapping("/modules/{id}")
    ModuleView update(@PathVariable UUID id, @RequestHeader(value = IfMatch.HEADER, required = false) String ifMatch,
                      @RequestBody JsonNode body) {
        PatchBody patch = PatchBody.of(body, mapper);
        long version = IfMatch.resolve(ifMatch, patch.version());
        return modules.update(id, new ModulePatch(patch.text("title"), patch.text("visibility"), patch.instant("publishAt"),
                patch.object("conditions")), version);
    }

    @DeleteMapping("/modules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        modules.delete(id);
    }

    @PostMapping("/modules/{id}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void restore(@PathVariable UUID id) {
        modules.restore(id);
    }

    @PostMapping("/modules/{id}/move")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void move(@PathVariable UUID id, @Valid @RequestBody MoveModuleRequest request) {
        modules.move(id, request.position(), request.parentId());
    }

    @PostMapping("/modules/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    ModuleView duplicate(@PathVariable UUID id) {
        return duplication.duplicateModule(id);
    }

    record CreateModuleRequest(@NotBlank @Size(max = CourseTexts.MAX_TITLE) String title, UUID parentId) {
    }

    /** parentId == null — модуль верхнего уровня. */
    record MoveModuleRequest(@NotNull @PositiveOrZero Integer position, UUID parentId) {
    }
}
