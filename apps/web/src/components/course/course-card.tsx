'use client';
import { ArrowUpRight, BookOpen, Copy, MoreHorizontal, Trash2 } from 'lucide-react';
import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { Badge } from '@/components/ui/badge';
import { StatusChip } from './status-chip';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Progress } from '@/components/ui/progress';
import { ROUTES } from '@/features/auth/routes';
import type { CourseCard as CourseCardData } from '@/lib/api/schemas/courses';

type Props = { course: CourseCardData; onDuplicate?: () => void; onDelete?: () => void };

const STAFF_ROLES = new Set(['teacher', 'assistant']);

/** Stitch course card: inset rounded cover with a status chip, headline title, role chip, progress. */
export function CourseCard({ course, onDuplicate, onDelete }: Props) {
  const t = useTranslations('courses');
  const tRoles = useTranslations('roles');
  const manageable = !!(onDuplicate || onDelete);
  const staff = course.role !== null && STAFF_ROLES.has(course.role);
  const showStatus = staff || course.visibility !== 'published';
  const percent = course.progressPercent !== null ? Math.round(course.progressPercent) : null;
  return (
    <article className="group relative flex h-full flex-col gap-1 rounded-lg border border-card-border bg-surface p-2 shadow-sm transition-[box-shadow,border-color] duration-fast focus-within:border-card-border-hover focus-within:shadow-md hover:border-card-border-hover hover:shadow-md">
      <div className="relative overflow-hidden rounded-md">
        {course.coverUrl ? (
          // eslint-disable-next-line @next/next/no-img-element -- storage URL
          <img
            src={course.coverUrl}
            alt=""
            className="aspect-[16/8] w-full object-cover transition-transform duration-base group-hover:scale-[1.02]"
          />
        ) : (
          <div
            className="flex aspect-[16/8] w-full items-center justify-center bg-gradient-to-br from-primary-soft via-surface-muted to-success-soft/70 text-primary"
            aria-hidden
          >
            <span className="flex size-14 items-center justify-center rounded-full bg-surface/80 shadow-sm">
              <BookOpen className="size-6" />
            </span>
          </div>
        )}
        {showStatus ? (
          <div className="pointer-events-none absolute inset-x-2 top-2 flex flex-wrap items-start gap-1.5">
            <StatusChip visibility={course.visibility} className="shadow-sm" />
          </div>
        ) : null}
      </div>
      <div className="flex flex-1 flex-col gap-3 px-3 pb-3 pt-2">
        <div className="flex items-start gap-2">
          <div className="flex min-w-0 flex-1 flex-col gap-1">
            {course.shortName ? (
              <span className="truncate text-label-sm uppercase text-text-muted">
                {course.shortName}
              </span>
            ) : null}
            <h2 className="text-lg leading-snug">
              <Link
                href={ROUTES.course(course.id)}
                className="after:absolute after:inset-0 after:rounded-lg focus-visible:outline-none"
              >
                {course.title}
              </Link>
            </h2>
          </div>
          {manageable ? (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button
                  variant="ghost"
                  size="icon-sm"
                  className="relative z-10 -mr-1"
                  aria-label={t('actionsFor', { title: course.title })}
                >
                  <MoreHorizontal aria-hidden />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                {onDuplicate ? (
                  <DropdownMenuItem onSelect={onDuplicate}>
                    <Copy aria-hidden /> {t('duplicate')}
                  </DropdownMenuItem>
                ) : null}
                {onDelete ? (
                  <DropdownMenuItem tone="danger" onSelect={onDelete}>
                    <Trash2 aria-hidden /> {t('delete')}
                  </DropdownMenuItem>
                ) : null}
              </DropdownMenuContent>
            </DropdownMenu>
          ) : null}
        </div>
        {percent !== null ? (
          <div className="mt-auto flex flex-col gap-1.5">
            <div className="flex items-center justify-between gap-2 text-xs">
              {course.role ? <Badge tone="primary">{tRoles(course.role)}</Badge> : <span />}
              <span className="font-semibold text-primary">{t('progress', { percent })}</span>
            </div>
            <Progress value={course.progressPercent ?? 0} label={t('progress', { percent })} />
          </div>
        ) : (
          <div className="mt-auto flex items-center justify-between gap-2">
            {course.role ? <Badge tone="primary">{tRoles(course.role)}</Badge> : <span />}
            <span
              className="flex size-8 items-center justify-center rounded-full bg-surface-muted text-primary transition-colors duration-fast group-hover:bg-primary group-hover:text-primary-foreground"
              aria-hidden
            >
              <ArrowUpRight className="size-4" />
            </span>
          </div>
        )}
      </div>
    </article>
  );
}
