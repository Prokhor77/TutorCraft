package com.tutorcraft.core.communication.calendar.application;

import com.tutorcraft.core.communication.calendar.application.CalendarRepository.IcalOwner;
import com.tutorcraft.core.communication.calendar.application.CalendarRepository.PersonalEvent;
import com.tutorcraft.core.communication.calendar.domain.CalendarEvent;
import com.tutorcraft.core.communication.calendar.domain.CalendarEvent.Kind;
import com.tutorcraft.core.communication.calendar.domain.IcsWriter;
import com.tutorcraft.core.identity.UsersApi;
import com.tutorcraft.core.shared.config.AppProperties;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.domain.Validator;
import com.tutorcraft.core.shared.i18n.Messages;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import com.tutorcraft.core.shared.security.TokenHasher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Календарь (FR-DASH-03): события курсов и личные события, iCal-подписка по секретной ссылке с перевыпуском. */
@Service
public class CalendarService {

    static final Duration MAX_RANGE = Duration.ofDays(366);
    static final Duration ICAL_PAST = Duration.ofDays(30);
    static final Duration ICAL_FUTURE = Duration.ofDays(365);
    private static final String NOT_FOUND = "calendar.event_not_found";
    private static final String ICAL_NOT_FOUND = "calendar.ical_not_found";
    private static final String ICAL_PATH = "/api/v1/calendar/ical/";
    private static final String ICAL_EXTENSION = ".ics";
    private static final String KIND_MESSAGE_PREFIX = "calendar.kind.";
    private static final int MAX_TITLE = 200;
    private static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("ru");

    private final CurrentUserProvider currentUser;
    private final CalendarRepository calendar;
    private final CourseEventsReader courseEvents;
    private final UsersApi users;
    private final Messages messages;
    private final Clock clock;
    private final String publicBaseUrl;

    CalendarService(CurrentUserProvider currentUser, CalendarRepository calendar, CourseEventsReader courseEvents,
                    UsersApi users, Messages messages, Clock clock, AppProperties properties) {
        this.currentUser = currentUser;
        this.calendar = calendar;
        this.courseEvents = courseEvents;
        this.users = users;
        this.messages = messages;
        this.clock = clock;
        this.publicBaseUrl = properties.publicBaseUrl();
    }

    @Transactional(readOnly = true)
    public List<CalendarEvent> events(Instant from, Instant to) {
        CurrentUser user = currentUser.require();
        new Validator()
                .check(from != null && to != null && from.isBefore(to), "to", "invalid_range", "'to' must be after 'from'")
                .check(from == null || to == null || !Duration.between(from, to).minus(MAX_RANGE).isPositive(), "to",
                        "range_too_long", "Range must not exceed 366 days")
                .throwIfInvalid();
        return merged(user.tenantId(), user.userId(), from, to);
    }

    @Transactional
    public CalendarEvent createPersonal(String title, Instant startsAt, Instant endsAt) {
        CurrentUser user = currentUser.require();
        validatePersonal(title, startsAt, endsAt);
        Instant now = clock.instant();
        PersonalEvent event = new PersonalEvent(Ids.newId(), user.tenantId(), user.userId(), title.trim(), startsAt, endsAt,
                now, now);
        calendar.insertEvent(event);
        return toEvent(event);
    }

    /** Частичное изменение: null — поле не меняется; clearEnd — убрать время окончания. */
    @Transactional
    public CalendarEvent updatePersonal(UUID eventId, String title, Instant startsAt, Instant endsAt, boolean clearEnd) {
        CurrentUser user = currentUser.require();
        PersonalEvent current = requireEvent(user, eventId);
        PersonalEvent updated = new PersonalEvent(current.id(), current.tenantId(), current.userId(),
                title == null ? current.title() : title.trim(), startsAt == null ? current.startsAt() : startsAt,
                clearEnd ? null : endsAt == null ? current.endsAt() : endsAt, current.createdAt(), clock.instant());
        validatePersonal(updated.title(), updated.startsAt(), updated.endsAt());
        calendar.updateEvent(updated);
        return toEvent(updated);
    }

    @Transactional
    public void deletePersonal(UUID eventId) {
        CurrentUser user = currentUser.require();
        requireEvent(user, eventId);
        calendar.deleteEvent(user.tenantId(), user.userId(), eventId);
    }

    /** Новая секретная ссылка; предыдущая перестаёт работать. Токен показывается только в ответе. */
    @Transactional
    public String reissueIcalUrl() {
        CurrentUser user = currentUser.require();
        String token = TokenHasher.newToken();
        calendar.replaceIcalToken(user.tenantId(), user.userId(), TokenHasher.sha256(token), clock.instant());
        return publicBaseUrl + ICAL_PATH + token + ICAL_EXTENSION;
    }

    @Transactional(readOnly = true)
    public String ical(String token) {
        IcalOwner owner = calendar.findIcalOwner(TokenHasher.sha256(token))
                .orElseThrow(() -> new NotFoundException(ICAL_NOT_FOUND, "Calendar not found"));
        Instant now = clock.instant();
        Locale locale = users.find(owner.tenantId(), owner.userId()).map(user -> Locale.forLanguageTag(user.locale()))
                .orElse(DEFAULT_LOCALE);
        List<CalendarEvent> events = merged(owner.tenantId(), owner.userId(), now.minus(ICAL_PAST), now.plus(ICAL_FUTURE));
        return IcsWriter.write(messages.get(locale, "calendar.ical.name"), events, now,
                event -> summary(locale, event));
    }

    private List<CalendarEvent> merged(UUID tenantId, UUID userId, Instant from, Instant to) {
        List<CalendarEvent> events = new ArrayList<>(courseEvents.events(tenantId, userId, from, to));
        calendar.events(tenantId, userId, from, to).forEach(event -> events.add(toEvent(event)));
        events.sort(Comparator.comparing(CalendarEvent::startsAt).thenComparing(CalendarEvent::id));
        return events;
    }

    private String summary(Locale locale, CalendarEvent event) {
        if (event.kind() == Kind.PERSONAL) {
            return event.title();
        }
        return messages.get(locale, KIND_MESSAGE_PREFIX + event.kind().key(), event.title());
    }

    private PersonalEvent requireEvent(CurrentUser user, UUID eventId) {
        return calendar.findEvent(user.tenantId(), user.userId(), eventId)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND, "Event not found"));
    }

    private static void validatePersonal(String title, Instant startsAt, Instant endsAt) {
        new Validator().notBlank(title, "title").maxLength(title, MAX_TITLE, "title")
                .check(startsAt != null, "startsAt", "required", "Start time is required")
                .check(endsAt == null || startsAt == null || !endsAt.isBefore(startsAt), "endsAt", "before_start",
                        "End must not be before start")
                .throwIfInvalid();
    }

    private static CalendarEvent toEvent(PersonalEvent event) {
        return new CalendarEvent(event.id(), event.title(), event.startsAt(), event.endsAt(), null, null, Kind.PERSONAL, null);
    }
}
