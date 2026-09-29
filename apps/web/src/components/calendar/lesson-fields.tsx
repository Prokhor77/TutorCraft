'use client';
import { useTranslations } from 'next-intl';
import { useMemo } from 'react';
import type { UseFormReturn } from 'react-hook-form';
import { Radio } from '@/components/ui/checkbox';
import { Field } from '@/components/ui/field';
import { NativeSelect } from '@/components/ui/input';
import { useOutline } from '@/features/courses/use-outline';
import type { LessonCourse } from '@/lib/api/schemas/me';
import { flattenOutline } from '@/lib/utils/calendar-form';
import type { EventFormValues } from './event-form';
import { StudentPicker } from './student-picker';

const INDENT = ' ';

/** Course → module → course item, and who the lesson is for (whole course or chosen students). */
export function LessonFields({
  form,
  courses,
  courseLocked,
}: {
  form: UseFormReturn<EventFormValues>;
  courses: LessonCourse[];
  /** An existing lesson cannot move to another course. */
  courseLocked: boolean;
}) {
  const t = useTranslations('calendar');
  const { errors } = form.formState;
  const [courseId, moduleId, audience, attendeeIds] = form.watch([
    'courseId',
    'moduleId',
    'audience',
    'attendeeIds',
  ]);
  const outline = useOutline(courseId);
  const options = useMemo(() => flattenOutline(outline.data?.modules ?? []), [outline.data]);
  const items = moduleId
    ? options.items.filter((item) => item.moduleId === moduleId)
    : options.items;

  const onCourseChange = (value: string) => {
    form.setValue('courseId', value, { shouldValidate: true });
    form.setValue('moduleId', '');
    form.setValue('itemId', '');
    form.setValue('attendeeIds', []);
  };
  const onModuleChange = (value: string) => {
    form.setValue('moduleId', value);
    const item = options.items.find((entry) => entry.id === form.getValues('itemId'));
    if (value && item && item.moduleId !== value) form.setValue('itemId', '');
  };

  return (
    <div className="flex flex-col gap-4">
      <Field label={t('course')} error={errors.courseId?.message} required>
        <NativeSelect
          value={courseId}
          disabled={courseLocked}
          onChange={(event) => onCourseChange(event.target.value)}
        >
          <option value="">{t('coursePlaceholder')}</option>
          {courses.map((course) => (
            <option key={course.id} value={course.id}>
              {course.title}
            </option>
          ))}
        </NativeSelect>
      </Field>
      {courseId ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Field label={t('module')} error={errors.moduleId?.message}>
            <NativeSelect
              value={moduleId}
              disabled={outline.isLoading}
              onChange={(event) => onModuleChange(event.target.value)}
            >
              <option value="">{t('notSpecified')}</option>
              {options.modules.map((module) => (
                <option key={module.id} value={module.id}>
                  {INDENT.repeat(module.depth)}
                  {module.title}
                </option>
              ))}
            </NativeSelect>
          </Field>
          <Field label={t('courseItem')} error={errors.itemId?.message}>
            <NativeSelect disabled={outline.isLoading} {...form.register('itemId')}>
              <option value="">{t('notSpecified')}</option>
              {items.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.title}
                </option>
              ))}
            </NativeSelect>
          </Field>
        </div>
      ) : null}
      <fieldset className="flex flex-col gap-2">
        <legend className="mb-1.5 text-sm font-medium">{t('audience')}</legend>
        <label className="flex items-center gap-2 text-sm">
          <Radio value="course" {...form.register('audience')} />
          {t('audienceCourse')}
        </label>
        <label className="flex items-center gap-2 text-sm">
          <Radio value="students" {...form.register('audience')} />
          {t('audienceStudents')}
        </label>
      </fieldset>
      {audience === 'students' && courseId ? (
        <StudentPicker
          courseId={courseId}
          value={attendeeIds}
          onChange={(ids) => form.setValue('attendeeIds', ids, { shouldValidate: true })}
          error={errors.attendeeIds?.message}
        />
      ) : null}
    </div>
  );
}
