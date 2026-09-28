'use client';
import { Eye } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { InlineEdit } from '@/components/ui/inline-edit';
import { SavedIndicator } from '@/components/layout/saved-indicator';
import { useCourseContext } from '@/features/courses/course-context';
import { useUpdateCourse } from '@/features/courses/use-courses';
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

/**
 * Course headline on the builder (inline rename, UX-04): short-name and status chips, saved indicator
 * («Сохранено N мин назад»). Section tabs and publish actions live in the shell header.
 */
export function CourseHeader() {
  const t = useTranslations('course');
  const { course, editMode } = useCourseContext();
  const update = useUpdateCourse(course.id);

  return (
    <header className="mb-5 flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
      <div className="flex min-w-0 flex-col gap-2">
        <div className="flex flex-wrap items-center gap-2">
          {course.shortName ? <Badge tone="primary">{course.shortName}</Badge> : null}
          <StatusChip visibility={course.visibility} />
        </div>
        <h1 className="text-2xl md:text-3xl">
          <InlineEdit
            value={course.title}
            label={t('renameCourse')}
            disabled={!editMode}
            onSave={(title) => update.mutate({ version: course.version, patch: { title } })}
          />
        </h1>
      </div>
      {editMode ? <SavedIndicator className="shrink-0" /> : null}
    </header>
  );
}
