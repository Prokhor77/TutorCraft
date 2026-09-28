package com.tutorcraft.core.enrollment.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutorcraft.core.enrollment.application.EnrollmentService;
import com.tutorcraft.core.enrollment.application.EnrollmentService.EnrollmentPatch;
import com.tutorcraft.core.enrollment.application.EnrollmentView;
import com.tutorcraft.core.enrollment.application.JoinService;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import com.tutorcraft.core.shared.domain.ValidationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Записи на курс и самозапись (контракт §6). */
@RestController
@RequestMapping("/api/v1")
class EnrollmentsController {

    private static final int MAX_ROLE_LENGTH = 32;
    private static final int MAX_CODE_LENGTH = 64;
    private static final String STARTS_AT = "startsAt";
    private static final String ENDS_AT = "endsAt";

    private final EnrollmentService enrollments;
    private final JoinService join;
    private final ObjectMapper mapper;

    EnrollmentsController(EnrollmentService enrollments, JoinService join, ObjectMapper mapper) {
        this.enrollments = enrollments;
        this.join = join;
        this.mapper = mapper;
    }

    @GetMapping("/courses/{courseId}/enrollments")
    PageResponse<EnrollmentView> list(@PathVariable UUID courseId, @RequestParam(required = false) String q,
                                      @RequestParam(required = false) String role,
                                      @RequestParam(required = false) UUID groupId,
                                      @RequestParam(required = false) String cursor,
                                      @RequestParam(required = false) Integer limit) {
        return enrollments.list(courseId, q, role, groupId, PageQuery.of(cursor, limit));
    }

    @PostMapping("/courses/{courseId}/enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    CreatedResponse enrol(@PathVariable UUID courseId, @Valid @RequestBody EnrolRequest request) {
        return new CreatedResponse(enrollments.enrol(courseId, request.userIds(), request.role(), request.startsAt(),
                request.endsAt()));
    }

    @PatchMapping("/enrollments/{id}")
    EnrollmentView update(@PathVariable UUID id, @RequestBody JsonNode body) {
        if (body == null || !body.isObject()) {
            throw ValidationException.single("body", "invalid", "Request body must be a JSON object");
        }
        return enrollments.update(id, new EnrollmentPatch(text(body, "role"), text(body, "status"), body.has(STARTS_AT),
                instant(body, STARTS_AT), body.has(ENDS_AT), instant(body, ENDS_AT)));
    }

    @DeleteMapping("/enrollments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        enrollments.delete(id);
    }

    @PostMapping("/courses/{courseId}/self-enrol")
    EnrollmentView selfEnrol(@PathVariable UUID courseId, @Valid @RequestBody(required = false) SelfEnrolRequest request) {
        return join.selfEnrol(courseId, request == null ? null : request.code());
    }

    private static String text(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw ValidationException.single(field, "invalid", "Expected a string");
        }
        return value.asText();
    }

    private Instant instant(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return mapper.treeToValue(value, Instant.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw ValidationException.single(field, "invalid", "Expected an ISO-8601 instant");
        }
    }

    record EnrolRequest(@NotEmpty @Size(max = EnrollmentService.MAX_BULK_USERS) List<UUID> userIds,
                        @NotBlank @Size(max = MAX_ROLE_LENGTH) String role, Instant startsAt, Instant endsAt) {
    }

    record CreatedResponse(int created) {
    }

    record SelfEnrolRequest(@Size(max = MAX_CODE_LENGTH) String code) {
    }
}
