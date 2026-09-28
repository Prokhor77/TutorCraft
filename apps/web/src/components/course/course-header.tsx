'use client';
import { Eye, EyeOff, Trash2 } from 'lucide-react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Switch } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/field';
import { InlineEdit } from '@/components/ui/inline-edit';
import { ROUTES } from '@/features/auth/routes';
import { useCourseContext } from '@/features/courses/course-context';
import { useUpdateCourse } from '@/features/courses/use-courses';
import { PERMISSIONS, type Permission } from '@/lib/access/permissions';
import { cn } from '@/lib/utils/cn';
import { useUiStore } from '@/stores/ui-store';
import { CourseSettingsSheet } from './course-settings-sheet';

type Tab = { href: string; labelKey: string; permission?: Permission };

export function CourseHeader() {
  const t = useTranslations('course');
  const pathname = usePathname();
  const { course, can, editMode } = useCourseContext();
  const update = useUpdateCourse(course.id);
  const viewAsStudent = useUiStore((state) => state.viewAsStudent);
  const setViewAsStudent = useUiStore((state) => state.setViewAsStudent);
  const canEdit = can(PERMISSIONS.courseEdit);

  const tabs: Tab[] = [
    { href: ROUTES.course(course.id), labelKey: 'tabContent' },
    {
      href: ROUTES.courseParticipants(course.id),
      labelKey: 'tabParticipants',
      permission: PERMISSIONS.enrollmentView,
    },
    {
      href: ROUTES.courseGradebook(course.id),
      labelKey: 'tabGradebook',
      permission: PERMISSIONS.gradeViewAll,
    },
    {
      href: ROUTES.courseQuestionBank(course.id),
      labelKey: 'tabQuestionBank',
      permission: PERMISSIONS.qbankManage,
    },
    {
      href: ROUTES.courseGrades(course.id),
      labelKey: 'tabMyGrades',
      permission: PERMISSIONS.gradeViewOwn,
    },
  ];
  const visibleTabs = tabs.filter((tab) => !tab.permission || can(tab.permission));

  return (
    <header className="mb-6 flex flex-col gap-4">
      <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
        <div className="flex min-w-0 flex-col gap-2">
          <h1 className="text-2xl md:text-3xl">
            <InlineEdit
              value={course.title}
              label={t('renameCourse')}
              disabled={!editMode}
              onSave={(title) => update.mutate({ version: course.version, patch: { title } })}
            />
          </h1>
          <div className="flex flex-wrap gap-1.5">
            {course.shortName ? <Badge>{course.shortName}</Badge> : null}
            {course.visibility !== 'published' ? (
              <Badge tone="warning">
                <EyeOff aria-hidden /> {t(`visibility.${course.visibility}`)}
              </Badge>
            ) : null}
          </div>
        </div>
        {canEdit ? (
          <div className="flex flex-wrap items-center gap-3">
            <div className="flex items-center gap-2">
              <Switch
                id="view-as-student"
                checked={viewAsStudent}
                onCheckedChange={setViewAsStudent}
              />
              <Label htmlFor="view-as-student" className="flex items-center gap-1.5">
                <Eye className="size-4" aria-hidden /> {t('viewAsStudent')}
              </Label>
            </div>
            {editMode ? (
              <>
                <Button asChild variant="ghost" size="sm">
                  <Link href={ROUTES.courseTrash(course.id)}>
                    <Trash2 aria-hidden /> {t('trash')}
                  </Link>
                </Button>
                <CourseSettingsSheet />
              </>
            ) : null}
          </div>
        ) : null}
      </div>
      {viewAsStudent ? (
        <p className="rounded-md bg-info-soft px-3 py-2 text-sm text-info">{t('previewBanner')}</p>
      ) : null}
      <nav
        aria-label={t('sections')}
        className="-mx-1 flex gap-1 overflow-x-auto border-b border-border px-1"
      >
        {visibleTabs.map((tab) => {
          const active =
            tab.href === ROUTES.course(course.id)
              ? pathname === tab.href || pathname.includes('/items/')
              : pathname.startsWith(tab.href);
          return (
            <Link
              key={tab.href}
              href={tab.href}
              aria-current={active ? 'page' : undefined}
              className={cn(
                '-mb-px whitespace-nowrap border-b-2 px-3 py-2 text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring',
                active
                  ? 'border-primary text-primary'
                  : 'border-transparent text-text-muted hover:text-text',
              )}
            >
              {t(tab.labelKey)}
            </Link>
          );
        })}
      </nav>
    </header>
  );
}
