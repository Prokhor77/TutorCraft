'use client';
import { zodResolver } from '@hookform/resolvers/zod';
import { useTranslations } from 'next-intl';
import { useEffect } from 'react';
import { Controller, useForm, type UseFormReturn } from 'react-hook-form';
import { Button } from '@/components/ui/button';
import { Switch } from '@/components/ui/checkbox';
import { DatePicker } from '@/components/ui/date-picker';
import { Dialog, DialogContent, DialogFooter } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Input, Textarea } from '@/components/ui/input';
import { Segmented } from '@/components/ui/segmented';
import { TimePicker } from '@/components/ui/time-picker';
import { toast } from '@/components/ui/toast';
import {
  useCreateLesson,
  useCreateNote,
  useUpdateLesson,
  useUpdateNote,
} from '@/features/calendar/use-calendar';
import { applyServerFieldErrors } from '@/features/forms/server-errors';
import type { CalendarEvent, LessonCourse } from '@/lib/api/schemas/me';
import {
  DESCRIPTION_MAX,
  emptyFormValues,
  eventFormSchema,
  formValuesOf,
  SERVER_FIELDS,
  TITLE_MAX,
  toLessonInput,
  toNoteInput,
  toNotePatch,
  type EventFormKind,
  type EventFormValues,
} from './event-form';
import { LessonFields } from './lesson-fields';

/** What the dialog edits: a new event on a day, or an existing note/lesson. */
export type EventDraftTarget =
  { date: Date; courseId?: string; event?: undefined } | { event: CalendarEvent };

function useSaveEvent(target: EventDraftTarget) {
  const createNote = useCreateNote();
  const updateNote = useUpdateNote();
  const createLesson = useCreateLesson();
  const updateLesson = useUpdateLesson();
  const pending =
    createNote.isPending ||
    updateNote.isPending ||
    createLesson.isPending ||
    updateLesson.isPending;

  const save = (values: EventFormValues) => {
    const existing = target.event;
    if (values.kind === 'note') {
      return existing
        ? updateNote.mutateAsync({ id: existing.id, patch: toNotePatch(values) })
        : createNote.mutateAsync(toNoteInput(values));
    }
    const input = toLessonInput(values);
    return existing
      ? updateLesson.mutateAsync({
          courseId: values.courseId,
          lessonId: existing.id,
          version: existing.version ?? 0,
          input,
        })
      : createLesson.mutateAsync({ courseId: values.courseId, input });
  };
  return { save, pending };
}

function initialValues(target: EventDraftTarget): EventFormValues {
  return target.event
    ? formValuesOf(target.event)
    : emptyFormValues(target.date, 'note', target.courseId);
}

/** Create/edit a personal note or a lesson for course students. */
export function EventFormDialog({
  target,
  courses,
  onClose,
}: {
  target: EventDraftTarget | null;
  courses: LessonCourse[];
  onClose: () => void;
}) {
  const t = useTranslations('calendar');
  const tCommon = useTranslations('common');
  const form = useForm<EventFormValues>({
    resolver: zodResolver(
      eventFormSchema({
        titleRequired: t('errors.titleRequired'),
        dateRequired: t('errors.dateRequired'),
        timeRequired: t('errors.timeRequired'),
        endBeforeStart: t('errors.endBeforeStart'),
        courseRequired: t('errors.courseRequired'),
        studentsRequired: t('errors.studentsRequired'),
      }),
    ),
    defaultValues: emptyFormValues(new Date(), 'note'),
  });
  const { save, pending } = useSaveEvent(target ?? { date: new Date() });

  useEffect(() => {
    if (target) form.reset(initialValues(target));
  }, [target, form]);

  const kind = form.watch('kind');
  const editing = Boolean(target?.event);
  const title = editing ? t(kind === 'lesson' ? 'editLesson' : 'editNote') : t('newEvent');

  const onSubmit = form.handleSubmit(async (values) => {
    try {
      await save(values);
      toast({
        tone: 'success',
        title: t(values.kind === 'lesson' && !editing ? 'lessonScheduled' : 'saved'),
      });
      onClose();
    } catch (error) {
      applyServerFieldErrors(error, form.setError, SERVER_FIELDS);
    }
  });

  return (
    <Dialog open={target !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent title={title} closeLabel={tCommon('close')} className="max-w-xl">
        <form noValidate onSubmit={onSubmit} className="flex flex-col gap-4">
          {!editing && courses.length > 0 ? (
            <Segmented<EventFormKind>
              label={t('type')}
              value={kind}
              onChange={(value) => form.setValue('kind', value)}
              options={[
                { value: 'note', label: t('typeNote') },
                { value: 'lesson', label: t('typeLesson') },
              ]}
            />
          ) : null}
          <Field label={t('fieldTitle')} error={form.formState.errors.title?.message} required>
            <Input
              autoFocus
              maxLength={TITLE_MAX}
              placeholder={t(kind === 'lesson' ? 'titlePlaceholderLesson' : 'titlePlaceholderNote')}
              {...form.register('title')}
            />
          </Field>
          <TimeFields form={form} />
          {kind === 'lesson' ? (
            <LessonFields form={form} courses={courses} courseLocked={editing} />
          ) : null}
          <Field label={t('description')} error={form.formState.errors.description?.message}>
            <Textarea
              maxLength={DESCRIPTION_MAX}
              placeholder={t(
                kind === 'lesson' ? 'descriptionPlaceholderLesson' : 'descriptionPlaceholderNote',
              )}
              {...form.register('description')}
            />
          </Field>
          <DialogFooter>
            <Button type="button" variant="ghost" onClick={onClose}>
              {tCommon('cancel')}
            </Button>
            <Button type="submit" loading={pending}>
              {editing ? tCommon('save') : tCommon('create')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

/** Date, «all day» (notes only) and start/end time — popover pickers instead of native inputs. */
function TimeFields({ form }: { form: UseFormReturn<EventFormValues> }) {
  const t = useTranslations('calendar');
  const { errors } = form.formState;
  const [kind, allDay] = form.watch(['kind', 'allDay']);
  const timed = kind === 'lesson' || !allDay;
  return (
    <div className="flex flex-col gap-3">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-[1fr_auto] sm:items-end">
        <Controller
          control={form.control}
          name="date"
          render={({ field }) => (
            <Field label={t('date')} error={errors.date?.message} required>
              <DatePicker
                value={field.value}
                onChange={field.onChange}
                onBlur={field.onBlur}
                placeholder={t('datePlaceholder')}
                todayLabel={t('today')}
              />
            </Field>
          )}
        />
        {kind === 'note' ? (
          <label className="flex h-11 items-center gap-2 text-sm">
            <Switch
              checked={allDay}
              onCheckedChange={(checked) =>
                form.setValue('allDay', checked, { shouldValidate: true })
              }
            />
            {t('allDay')}
          </label>
        ) : null}
      </div>
      {timed ? (
        <div className="grid grid-cols-2 gap-4">
          <Controller
            control={form.control}
            name="startTime"
            render={({ field }) => (
              <Field label={t('startTime')} error={errors.startTime?.message} required>
                <TimePicker
                  value={field.value}
                  onChange={field.onChange}
                  onBlur={field.onBlur}
                  placeholder={t('timePlaceholder')}
                />
              </Field>
            )}
          />
          <Controller
            control={form.control}
            name="endTime"
            render={({ field }) => (
              <Field label={t('endTime')} error={errors.endTime?.message}>
                <TimePicker
                  value={field.value}
                  onChange={field.onChange}
                  onBlur={field.onBlur}
                  placeholder={t('noEndTime')}
                  clearLabel={t('noEndTime')}
                />
              </Field>
            )}
          />
        </div>
      ) : null}
    </div>
  );
}
