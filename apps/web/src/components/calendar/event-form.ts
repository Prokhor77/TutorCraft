import { z } from 'zod';
import type { LessonInput, NoteInput, NotePatch } from '@/lib/api/endpoints/calendar';
import type { CalendarEvent, LessonAudience } from '@/lib/api/schemas/me';
import {
  combineLocal,
  isTimeBefore,
  toDateInputValue,
  toTimeInputValue,
} from '@/lib/utils/calendar-form';

export const TITLE_MAX = 200;
export const DESCRIPTION_MAX = 4000;
export const DEFAULT_START_TIME = '10:00';
export const DEFAULT_END_TIME = '11:00';

export type EventFormKind = 'note' | 'lesson';

type Messages = {
  titleRequired: string;
  dateRequired: string;
  timeRequired: string;
  endBeforeStart: string;
  courseRequired: string;
  studentsRequired: string;
};

/** Form schema: notes may be all-day; lessons always have a time, a course and an audience. */
export function eventFormSchema(messages: Messages) {
  return z
    .object({
      kind: z.enum(['note', 'lesson']),
      title: z.string().trim().min(1, messages.titleRequired).max(TITLE_MAX),
      description: z.string().max(DESCRIPTION_MAX),
      date: z.string().min(1, messages.dateRequired),
      allDay: z.boolean(),
      startTime: z.string(),
      endTime: z.string(),
      courseId: z.string(),
      moduleId: z.string(),
      itemId: z.string(),
      audience: z.enum(['course', 'students']),
      attendeeIds: z.array(z.string()),
    })
    .superRefine((values, context) => {
      const timed = values.kind === 'lesson' || !values.allDay;
      const issue = (path: string, message: string) =>
        context.addIssue({ code: z.ZodIssueCode.custom, path: [path], message });
      if (timed && !values.startTime) issue('startTime', messages.timeRequired);
      if (timed && isTimeBefore(values.endTime, values.startTime)) {
        issue('endTime', messages.endBeforeStart);
      }
      if (values.kind !== 'lesson') return;
      if (!values.courseId) issue('courseId', messages.courseRequired);
      if (values.audience === 'students' && values.attendeeIds.length === 0) {
        issue('attendeeIds', messages.studentsRequired);
      }
    });
}

export type EventFormValues = z.infer<ReturnType<typeof eventFormSchema>>;

/** Server field errors shown inline; time errors (startsAt/endsAt) surface via the global toast. */
export const SERVER_FIELDS = [
  'title',
  'description',
  'courseId',
  'moduleId',
  'itemId',
  'attendeeIds',
] as const;

export function emptyFormValues(date: Date, kind: EventFormKind, courseId = ''): EventFormValues {
  return {
    kind,
    title: '',
    description: '',
    date: toDateInputValue(date),
    allDay: false,
    startTime: DEFAULT_START_TIME,
    endTime: DEFAULT_END_TIME,
    courseId,
    moduleId: '',
    itemId: '',
    audience: 'course',
    attendeeIds: [],
  };
}

/** Existing note or lesson → form values (local date/time of the viewer). */
export function formValuesOf(event: CalendarEvent): EventFormValues {
  const start = new Date(event.startsAt);
  const audience: LessonAudience = event.audience ?? 'course';
  return {
    kind: event.kind === 'lesson' ? 'lesson' : 'note',
    title: event.title,
    description: event.description ?? '',
    date: toDateInputValue(start),
    allDay: event.allDay,
    startTime: event.allDay ? DEFAULT_START_TIME : toTimeInputValue(start),
    endTime: event.endsAt && !event.allDay ? toTimeInputValue(new Date(event.endsAt)) : '',
    courseId: event.courseId ?? '',
    moduleId: event.moduleId ?? '',
    itemId: event.itemId ?? '',
    audience,
    attendeeIds: audience === 'students' ? event.attendeeIds : [],
  };
}

function times(values: EventFormValues): { startsAt: string; endsAt: string | null } {
  const allDay = values.kind === 'note' && values.allDay;
  const startsAt = combineLocal(values.date, allDay ? '00:00' : values.startTime) ?? '';
  const endsAt = allDay ? null : combineLocal(values.date, values.endTime);
  return { startsAt, endsAt };
}

const textOrNull = (text: string) => (text.trim() ? text.trim() : null);

export function toNoteInput(values: EventFormValues): NoteInput {
  return {
    title: values.title.trim(),
    description: textOrNull(values.description),
    allDay: values.allDay,
    ...times(values),
  };
}

/** PATCH body: empty end/description are cleared explicitly (null means "unchanged" on the server). */
export function toNotePatch(values: EventFormValues): NotePatch {
  const input = toNoteInput(values);
  return {
    ...input,
    endsAt: input.endsAt ?? undefined,
    description: input.description ?? undefined,
    clearEnd: input.endsAt === null,
    clearDescription: input.description === null,
  };
}

export function toLessonInput(values: EventFormValues): LessonInput {
  return {
    title: values.title.trim(),
    description: textOrNull(values.description),
    ...times(values),
    moduleId: values.moduleId || null,
    itemId: values.itemId || null,
    attendeeIds: values.audience === 'students' ? values.attendeeIds : [],
  };
}
