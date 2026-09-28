package com.tutorcraft.core.communication.calendar.web;

import com.tutorcraft.core.communication.calendar.application.CalendarService;
import com.tutorcraft.core.communication.calendar.domain.CalendarEvent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

/** Календарь и iCal-подписка (контракт §2, FR-DASH-03). */
@RestController
@RequestMapping("/api/v1")
class CalendarController {

    private static final int MAX_TITLE = 200;
    private static final MediaType TEXT_CALENDAR = MediaType.parseMediaType("text/calendar; charset=UTF-8");

    private final CalendarService calendar;

    CalendarController(CalendarService calendar) {
        this.calendar = calendar;
    }

    @GetMapping("/me/calendar")
    List<EventView> events(@RequestParam Instant from, @RequestParam Instant to) {
        return calendar.events(from, to).stream().map(EventView::of).toList();
    }

    @PostMapping("/me/calendar/events")
    @ResponseStatus(HttpStatus.CREATED)
    EventView create(@Valid @RequestBody CreateEventRequest request) {
        return EventView.of(calendar.createPersonal(request.title(), request.startsAt(), request.endsAt()));
    }

    @PatchMapping("/me/calendar/events/{eventId}")
    EventView update(@PathVariable UUID eventId, @Valid @RequestBody UpdateEventRequest request) {
        return EventView.of(calendar.updatePersonal(eventId, request.title(), request.startsAt(), request.endsAt(),
                Boolean.TRUE.equals(request.clearEnd())));
    }

    @DeleteMapping("/me/calendar/events/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID eventId) {
        calendar.deletePersonal(eventId);
    }

    @PostMapping("/me/calendar/ical-token")
    IcalLink reissueIcal() {
        return new IcalLink(calendar.reissueIcalUrl());
    }

    /** Публичная подписка: доступ по секрету в пути (без Authorization). */
    @GetMapping("/calendar/ical/{token:[A-Za-z0-9_-]+}.ics")
    ResponseEntity<String> ical(@PathVariable String token) {
        return ResponseEntity.ok()
                .contentType(TEXT_CALENDAR)
                .cacheControl(CacheControl.noStore())
                .body(calendar.ical(token));
    }

    record CreateEventRequest(@NotBlank @Size(max = MAX_TITLE) String title, @NotNull Instant startsAt, Instant endsAt) {
    }

    record UpdateEventRequest(@Size(max = MAX_TITLE) String title, Instant startsAt, Instant endsAt, Boolean clearEnd) {
    }

    record IcalLink(String url) {
    }

    /** Контракт CalendarEvent. */
    record EventView(UUID id, String title, Instant startsAt, Instant endsAt, UUID courseId, UUID itemId, String kind) {

        static EventView of(CalendarEvent event) {
            return new EventView(event.id(), event.title(), event.startsAt(), event.endsAt(), event.courseId(), event.itemId(),
                    event.kind().key());
        }
    }
}
