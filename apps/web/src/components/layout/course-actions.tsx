'use client';
import { CheckCircle2, Eye, MoreHorizontal, Rocket, Settings, Trash2 } from 'lucide-react';
import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import { CourseSettingsSheet } from '@/components/course/course-settings-sheet';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { toast } from '@/components/ui/toast';
import { ROUTES } from '@/features/auth/routes';
import { useCourseContext } from '@/features/courses/course-context';
import { useUpdateCourse } from '@/features/courses/use-courses';
import { PERMISSIONS } from '@/lib/access/permissions';
import { useUiStore } from '@/stores/ui-store';

/** Header actions inside a course (Stitch): «Предпросмотр», «Опубликовать курс», settings & trash. */
export function CourseActions() {
  const t = useTranslations('shell');
  const { course, can } = useCourseContext();
  const update = useUpdateCourse(course.id);
  const viewAsStudent = useUiStore((state) => state.viewAsStudent);
  const setViewAsStudent = useUiStore((state) => state.setViewAsStudent);
  const [settingsOpen, setSettingsOpen] = useState(false);
  if (!can(PERMISSIONS.courseEdit)) return null;
  const published = course.visibility === 'published';

  return (
    <div className="flex items-center gap-1.5">
      <Button
        variant={viewAsStudent ? 'soft' : 'ghost'}
        size="sm"
        aria-pressed={viewAsStudent}
        onClick={() => setViewAsStudent(!viewAsStudent)}
      >
        <Eye aria-hidden />
        <span className="hidden xl:inline">{t('preview')}</span>
        <span className="sr-only xl:hidden">{t('preview')}</span>
      </Button>
      {published ? (
        <Badge tone="success" className="hidden sm:inline-flex">
          <CheckCircle2 aria-hidden /> {t('published')}
        </Badge>
      ) : can(PERMISSIONS.coursePublish) ? (
        <Button
          size="sm"
          loading={update.isPending}
          onClick={() =>
            update.mutate(
              { version: course.version, patch: { visibility: 'published', publishAt: null } },
              { onSuccess: () => toast({ tone: 'success', title: t('coursePublished') }) },
            )
          }
        >
          <Rocket aria-hidden />
          <span className="hidden lg:inline">{t('publish')}</span>
          <span className="sr-only lg:hidden">{t('publish')}</span>
        </Button>
      ) : null}
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="ghost" size="icon-sm" aria-label={t('more')}>
            <MoreHorizontal aria-hidden />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem onSelect={() => setSettingsOpen(true)}>
            <Settings aria-hidden /> {t('settings')}
          </DropdownMenuItem>
          <DropdownMenuItem asChild>
            <Link href={ROUTES.courseTrash(course.id)}>
              <Trash2 aria-hidden /> {t('trash')}
            </Link>
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
      <CourseSettingsSheet open={settingsOpen} onOpenChange={setSettingsOpen} withTrigger={false} />
    </div>
  );
}
