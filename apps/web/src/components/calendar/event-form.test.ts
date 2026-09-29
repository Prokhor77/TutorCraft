import { describe, expect, it } from 'vitest';
import type { CalendarEvent } from '@/lib/api/schemas/me';
import {
  emptyFormValues,
  eventFormSchema,
  formValuesOf,
  toLessonInput,
  toNotePatch,
  type EventFormValues,
} from './event-form';

const messages = {
  titleRequired: 'title',
  dateRequired: 'date',
  timeRequired: 'time',
  endBeforeStart: 'order',
  courseRequired: 'course',
  studentsRequired: 'students',
};
const schema = eventFormSchema(messages);
const day = new Date(2026, 9, 5);

function errorsOf(values: EventFormValues): string[] {
  const result = schema.safeParse(values);
  return result.success ? [] : result.error.issues.map((issue) => issue.message);
}

describe('calendar event form', () => {
  it('accepts an all-day note without time', () => {
    const values = { ...emptyFormValues(day, 'note'), title: 'Exam', allDay: true, startTime: '' };
    expect(errorsOf(values)).toEqual([]);
  });

  it('requires a course, a time order and chosen students for lessons', () => {
    const values: EventFormValues = {
      ...emptyFormValues(day, 'lesson'),
      title: 'Lesson',
      startTime: '12:00',
      endTime: '11:00',
      audience: 'students',
    };
    expect(errorsOf(values)).toEqual(['order', 'course', 'students']);
  });

  it('sends only chosen students and nulls for empty links', () => {
    const values: EventFormValues = {
      ...emptyFormValues(day, 'lesson', 'course-1'),
      title: ' Fractions ',
      audience: 'course',
      attendeeIds: ['stale'],
    };
    const input = toLessonInput(values);
    expect(input).toMatchObject({
      title: 'Fractions',
      moduleId: null,
      itemId: null,
      attendeeIds: [],
    });
    expect(new Date(input.startsAt).getHours()).toBe(10);
    expect(new Date(input.endsAt ?? '').getHours()).toBe(11);
  });

  it('clears end and description of a note explicitly', () => {
    const values = {
      ...emptyFormValues(day, 'note'),
      title: 'Call',
      endTime: '',
      description: ' ',
    };
    expect(toNotePatch(values)).toMatchObject({ clearEnd: true, clearDescription: true });
  });

  it('restores an existing lesson for editing', () => {
    const start = new Date(2026, 9, 5, 15, 30);
    const event = {
      id: 'l1',
      title: 'Review',
      startsAt: start.toISOString(),
      endsAt: null,
      courseId: 'c1',
      itemId: null,
      kind: 'lesson',
      description: null,
      allDay: false,
      moduleId: 'm1',
      audience: 'students',
      attendeeIds: ['s1'],
      canEdit: true,
      version: 2,
    } as CalendarEvent;
    expect(formValuesOf(event)).toMatchObject({
      kind: 'lesson',
      date: '2026-10-05',
      startTime: '15:30',
      endTime: '',
      moduleId: 'm1',
      audience: 'students',
      attendeeIds: ['s1'],
    });
  });
});
