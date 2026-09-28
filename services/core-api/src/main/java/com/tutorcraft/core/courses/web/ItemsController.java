package com.tutorcraft.core.courses.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutorcraft.core.courses.application.CourseCommands.CreateItem;
import com.tutorcraft.core.courses.application.CourseCommands.ItemPatch;
import com.tutorcraft.core.courses.application.CourseStructureQueries;
import com.tutorcraft.core.courses.application.DuplicationService;
import com.tutorcraft.core.courses.application.ItemCommandService;
import com.tutorcraft.core.courses.application.ItemView;
import com.tutorcraft.core.courses.domain.CourseTexts;
import com.tutorcraft.core.shared.api.IfMatch;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Элементы курса (контракт §5; отметка выполнения /complete — модуль progress). */
@RestController
@RequestMapping("/api/v1")
class ItemsController {

    private static final int MAX_TYPE_LENGTH = 32;

    private final ItemCommandService commands;
    private final CourseStructureQueries queries;
    private final DuplicationService duplication;
    private final ObjectMapper mapper;

    ItemsController(ItemCommandService commands, CourseStructureQueries queries, DuplicationService duplication,
                    ObjectMapper mapper) {
        this.commands = commands;
        this.queries = queries;
        this.duplication = duplication;
        this.mapper = mapper;
    }

    @PostMapping("/modules/{moduleId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    ItemView create(@PathVariable UUID moduleId, @Valid @RequestBody CreateItemRequest request) {
        return commands.create(moduleId, new CreateItem(request.type(), request.title(), request.settings()));
    }

    @GetMapping("/items/{id}")
    ItemView get(@PathVariable UUID id) {
        return queries.item(id);
    }

    @PatchMapping("/items/{id}")
    ItemView update(@PathVariable UUID id, @RequestHeader(value = IfMatch.HEADER, required = false) String ifMatch,
                    @RequestBody JsonNode body) {
        PatchBody patch = PatchBody.of(body, mapper);
        long version = IfMatch.resolve(ifMatch, patch.version());
        return commands.update(id, new ItemPatch(patch.text("title"), patch.text("visibility"), patch.instant("publishAt"),
                patch.object("settings"), patch.raw("content"), patch.object("completionRule"), patch.object("conditions")),
                version);
    }

    @DeleteMapping("/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        commands.delete(id);
    }

    @PostMapping("/items/{id}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void restore(@PathVariable UUID id) {
        commands.restore(id);
    }

    @PostMapping("/items/{id}/move")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void move(@PathVariable UUID id, @Valid @RequestBody MoveItemRequest request) {
        commands.move(id, request.moduleId(), request.position());
    }

    @PostMapping("/items/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    ItemView duplicate(@PathVariable UUID id) {
        return duplication.duplicateItem(id);
    }

    /** UX-02: обязательны только type и title. */
    record CreateItemRequest(@NotBlank @Size(max = MAX_TYPE_LENGTH) String type,
                             @NotBlank @Size(max = CourseTexts.MAX_TITLE) String title, Map<String, Object> settings) {
    }

    record MoveItemRequest(@NotNull UUID moduleId, @NotNull @PositiveOrZero Integer position) {
    }
}
