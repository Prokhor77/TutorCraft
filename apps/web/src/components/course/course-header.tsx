'use client';
import { CalendarDays, Eye, Layers } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { InlineEdit } from '@/components/ui/inline-edit';
import { Breadcrumbs, PageHeader } from '@/components/ui/page-header';
import { SavedIndicator } from '@/components/layout/saved-indicator';
import { ROUTES } from '@/features/auth/routes';
import { useCourseContext } from '@/features/courses/course-context';
import { flattenModules, useOutline } from '@/features/courses/use-outline';
import { useUpdateCourse } from '@/features/courses/use-courses';
import { formatDate } from '@/lib/utils/format';
import { useUiStore } from '@/stores/ui-store';
import { StatusChip } from './status-chip';

/** «Как студент» preview banner — shown on every course page while the preview is on. */
export function PreviewBanner() {
  const t = useTranslations('course');
  const viewAsStudent = useUiStore((state) => state.viewAsStudent);
  const setViewAsStudent = useUiStore((state) => state.setViewAsStudent);
  if (!viewAsStudent) return null;
  return (
    <div
      role="status"
      className="mb-4 flex flex-wrap items-center justify-between gap-2 rounded-full bg-info-soft px-4 py-2 text-sm text-info"
    >
      <span className="flex items-center gap-2">
        <Eye className="size-4" aria-hidden /> {t('previewBanner')}
      </span>
      <Button variant="ghost" size="sm" onClick={() => setViewAsStudent(false)}>
        {t('exitPreview')}
      </Button>
    </div>
  );
}

/** Real counts from the cached outline: «3 модуля · 12 элементов». */
function useOutlineCounts(courseId: string): { modules: number; items: number } | null {
  const outline = useOutline(courseId);
  if (!outline.data) return null;
  const modules = flattenModules(outline.data.modules);
  return {
    modules: modules.length,
    items: modules.reduce((sum, module) => sum + module.items.length, 0),
  };
}

/**
 * Course headline card on the builder (Stitch «Конструктор курса»): breadcrumbs, inline-renamable title with
 * short-name / status chips, real module & item counts, course dates and the «Сохранено N мин назад» indicator.
 * Section tabs and publish actions live in the shell header.
 */
export function CourseHeader() {
  const t = useTranslations('course');
  const tShell = useTranslations('shell');
  const locale = useLocale();
  const { course, editMode } = useCourseContext();
  const update = useUpdateCourse(course.id);
  const counts = useOutlineCounts(course.id);
  const dates =
    course.startsAt || course.endsAt
      ? [course.startsAt, course.endsAt]
          .map((value) => (value ? formatDate(value, locale) : '…'))
          .join(' — ')
      : null;

  return (
    <PageHeader
      breadcrumbs={
        <Breadcrumbs
          label={tShell('breadcrumbs')}
          items={[{ label: tShell('myCourses'), href: ROUTES.courses }, { label: course.title }]}
        />
      }
      title={
        <InlineEdit
          value={course.title}
          label={t('renameCourse')}
          disabled={!editMode}
          onSave={(title) => update.mutate({ version: course.version, patch: { title } })}
        />
      }
      meta={
        <div className="flex flex-wrap items-center gap-2">
          {course.shortName ? <Badge tone="primary">{course.shortName}</Badge> : null}
          <StatusChip visibility={course.visibility} />
        </div>
      }
      description={
        counts || dates ? (
          <span className="flex flex-wrap items-center gap-x-4 gap-y-1">
            {counts ? (
              <span className="inline-flex items-center gap-1.5">
                <Layers className="size-4" aria-hidden />
                {t('headerCounts', { modules: counts.modules, items: counts.items })}
              </span>
            ) : null}
            {dates ? (
              <span className="inline-flex items-center gap-1.5">
                <CalendarDays className="size-4" aria-hidden />
                {dates}
              </span>
            ) : null}
          </span>
        ) : undefined
      }
      actions={editMode ? <SavedIndicator /> : undefined}
      className="mb-4"
    />
  );
}
