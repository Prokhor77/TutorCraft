package com.tutorcraft.core.enrollment.web;

import com.tutorcraft.core.enrollment.application.GroupService;
import com.tutorcraft.core.enrollment.application.GroupView;
import com.tutorcraft.core.enrollment.domain.CourseGroup;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Группы курса (контракт §6, FR-ENROL-05). */
@RestController
@RequestMapping("/api/v1")
class GroupsController {

    private static final int MAX_STRATEGY_LENGTH = 16;
    private static final int MAX_PREFIX_LENGTH = 80;

    private final GroupService groups;

    GroupsController(GroupService groups) {
        this.groups = groups;
    }

    @GetMapping("/courses/{courseId}/groups")
    List<GroupView> list(@PathVariable UUID courseId) {
        return groups.list(courseId);
    }

    @PostMapping("/courses/{courseId}/groups")
    @ResponseStatus(HttpStatus.CREATED)
    GroupView create(@PathVariable UUID courseId, @Valid @RequestBody GroupNameRequest request) {
        return groups.create(courseId, request.name());
    }

    @PatchMapping("/groups/{id}")
    GroupView rename(@PathVariable UUID id, @Valid @RequestBody GroupNameRequest request) {
        return groups.rename(id, request.name());
    }

    @DeleteMapping("/groups/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        groups.delete(id);
    }

    @PutMapping("/groups/{id}/members")
    GroupView replaceMembers(@PathVariable UUID id, @Valid @RequestBody MembersRequest request) {
        return groups.replaceMembers(id, request.userIds());
    }

    @PostMapping("/courses/{courseId}/groups/auto")
    @ResponseStatus(HttpStatus.CREATED)
    List<GroupView> auto(@PathVariable UUID courseId, @Valid @RequestBody AutoGroupsRequest request) {
        return groups.autoCreate(courseId, request.strategy(), request.value(), request.prefix());
    }

    record GroupNameRequest(@NotBlank @Size(max = CourseGroup.MAX_NAME) String name) {
    }

    record MembersRequest(@NotNull @Size(max = GroupService.MAX_MEMBERS) List<UUID> userIds) {
    }

    record AutoGroupsRequest(@NotBlank @Size(max = MAX_STRATEGY_LENGTH) String strategy, @NotNull Integer value,
                             @Size(max = MAX_PREFIX_LENGTH) String prefix) {
    }
}
