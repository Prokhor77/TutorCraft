package com.tutorcraft.core.communication.calendar.application;

import com.tutorcraft.core.communication.calendar.application.CalendarRepository.IcalOwner;
import com.tutorcraft.core.communication.calendar.application.CalendarRepository.PersonalEvent;
import com.tutorcraft.core.communication.calendar.domain.CalendarEvent;
import com.tutorcraft.core.communication.calendar.domain.CalendarEvent.Details;
import com.tutorcraft.core.communication.calendar.domain.CalendarEvent.Kind;
import com.tutorcraft.core.communication.calendar.domain.CalendarRules;
import com.tutorcraft.core.communication.calendar.domain.IcsWriter;
import com.tutorcraft.core.identity.UsersApi;
import com.tutorcraft.core.identity.UsersApi.UserRef;
import com.tutorcraft.core.shared.config.AppProperties;
import com.tutorcraft.core.shared.domain.Ids;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.domain.Validator;
import com.tutorcraft.core.shared.i18n.Messages;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import com.tutorcraft.core.shared.security.TokenHasher;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Календарь (FR-DASH-03): даты активностей курсов, занятия репетиторов и личные заметки; iCal-подписка по секретной
 * ссылке с перевыпуском.
 */
@Service
public class CalendarService {

    private static final Logger log = LoggerFactory.getLogger(CalendarService.class);
    static final Duration MAX_RANGE = Duration.ofDays(366);
    static final Duration ICAL_PAST = Duration.ofDays(30);
    static final Duration ICAL_FUTURE = Duration.ofDays(365);
    private static final String NOT_FOUND = "calendar.event_not_found";
    private static final String ICAL_NOT_FOUND = "calendar.ical_not_found";
    private static final String ICAL_PATH = "/api/v1/calendar/ical/";
    private static final String ICAL_EXTENSION = ".ics";
    private static final String KIND_MESSAGE_PREFIX = "calendar.kind.";
    private static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("ru");

    private final CurrentUserProvider currentUser;
    private final CalendarRepository calendar;
    private final UserCoursesResolver userCourses;
    private final CourseEventsReader courseEvents;
    private final LessonEventsReader lessonEvents;
    private final UsersApi users;
    private final Messages messages;
    private final Clock clock;
    private final String publicBaseUrl;

    CalendarService(CurrentUserProvider currentUser, CalendarRepository calendar, UserCoursesResolver userCourses,
                    CourseEventsReader courseEvents, LessonEventsReader lessonEvents, UsersApi users, Messages messages,
                    Clock clock, AppProperties properties) {
        this.currentUser = currentUser;
        this.calendar = calendar;
        this.userCourses = userCourses;
        this.courseEvents = courseEvents;
        this.lessonEvents = lessonEvents;
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
    public CalendarEvent createPersonal(PersonalEventDraft draft) {
        CurrentUser user = currentUser.require();
        CalendarRules.validate(draft.title(), draft.description(), draft.startsAt(), draft.endsAt()).throwIfInvalid();
        Instant now = clock.instant();
        PersonalEvent event = new PersonalEvent(Ids.newId(), user.tenantId(), user.userId(), draft.title().strip(),
                CalendarRules.normalizeText(draft.description()), Boolean.TRUE.equals(draft.allDay()), draft.startsAt(),
                draft.endsAt(), now, now);
        calendar.insertEvent(event);
        return toEvent(event);
    }

    /** Частичное изменение: null — поле не меняется; clearEnd/clearDescription — очистить поле. */
    @Transactional
    public CalendarEvent updatePersonal(UUID eventId, PersonalEventDraft draft) {
        CurrentUser user = currentUser.require();
        PersonalEvent current = requireEvent(user, eventId);
        PersonalEvent updated = merge(current, draft, clock.instant());
        CalendarRules.validate(updated.title(), updated.description(), updated.startsAt(), updated.endsAt())
                .throwIfInvalid();
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
        Optional<UserRef> user = users.find(owner.tenantId(), owner.userId());
        Locale locale = user.map(ref -> Locale.forLanguageTag(ref.locale())).orElse(DEFAULT_LOCALE);
        List<CalendarEvent> events = merged(owner.tenantId(), owner.userId(), now.minus(ICAL_PAST), now.plus(ICAL_FUTURE));
        return IcsWriter.write(messages.get(locale, "calendar.ical.name"), events, now,
                event -> summary(locale, event), zoneOf(user));
    }

    private List<CalendarEvent> merged(UUID tenantId, UUID userId, Instant from, Instant to) {
        UserCourses courses = userCourses.resolve(tenantId, userId);
        List<CalendarEvent> events = new ArrayList<>(courseEvents.events(tenantId, courses, from, to));
        events.addAll(lessonEvents.events(tenantId, userId, courses, from, to));
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

    private static PersonalEvent merge(PersonalEvent current, PersonalEventDraft draft, Instant now) {
        String description = draft.clearDescription() ? null
                : draft.description() == null ? current.description() : CalendarRules.normalizeText(draft.description());
        return new PersonalEvent(current.id(), current.tenantId(), current.userId(),
                draft.title() == null ? current.title() : draft.title().strip(), description,
                draft.allDay() == null ? current.allDay() : draft.allDay(),
                draft.startsAt() == null ? current.startsAt() : draft.startsAt(),
                draft.clearEnd() ? null : draft.endsAt() == null ? current.endsAt() : draft.endsAt(),
                current.createdAt(), now);
    }

    /** Часовой пояс пользователя для событий «весь день» в iCal; некорректный или пустой — UTC. */
    private static ZoneId zoneOf(Optional<UserRef> user) {
        String timezone = user.map(UserRef::timezone).orElse(null);
        if (timezone == null || timezone.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException invalid) {
            log.warn("Unknown user timezone '{}' in iCal export, falling back to UTC", timezone);
            return ZoneOffset.UTC;
        }
    }

    private static CalendarEvent toEvent(PersonalEvent event) {
        return new CalendarEvent(event.id(), event.title(), event.startsAt(), event.endsAt(), null, null, Kind.PERSONAL,
                null, Details.personal(event.description(), event.allDay()));
    }
}
