'use client';
import { Copy, EyeOff, MoreHorizontal, Trash2 } from 'lucide-react';
import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { Badge } from '@/components/ui/badge';
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
import { formatMoney } from '@/lib/utils/money';

type Props = { course: CourseCardData; onDuplicate?: () => void; onDelete?: () => void };

export function CourseCard({ course, onDuplicate, onDelete }: Props) {
  const t = useTranslations('courses');
  const tRoles = useTranslations('roles');
  const locale = useLocale();
  const manageable = !!(onDuplicate || onDelete);
  return (
    <article className="group relative flex h-full flex-col overflow-hidden rounded-lg border border-border bg-surface shadow-sm transition-shadow focus-within:ring-2 focus-within:ring-focus-ring hover:shadow-md">
      {course.coverUrl ? (
        // eslint-disable-next-line @next/next/no-img-element -- storage URL
        <img src={course.coverUrl} alt="" className="aspect-[16/7] w-full object-cover" />
      ) : (
        <div
          className="aspect-[16/7] w-full bg-gradient-to-br from-primary-soft via-surface-muted to-surface"
          aria-hidden
        />
      )}
      <div className="flex flex-1 flex-col gap-3 p-4">
        <div className="flex items-start gap-2">
          <h2 className="flex-1 text-base font-semibold leading-snug">
            <Link
              href={ROUTES.course(course.id)}
              className="after:absolute after:inset-0 focus-visible:outline-none"
            >
              {course.title}
            </Link>
          </h2>
          {manageable ? (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button
                  variant="ghost"
                  size="icon-sm"
                  className="relative z-10"
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
        <div className="flex flex-wrap gap-1.5">
          {course.role ? <Badge tone="primary">{tRoles(course.role)}</Badge> : null}
          {course.visibility !== 'published' ? (
            <Badge tone="warning">
              <EyeOff aria-hidden /> {t(`visibility.${course.visibility}`)}
            </Badge>
          ) : null}
          {course.price ? <Badge>{formatMoney(course.price, locale)}</Badge> : null}
        </div>
        {course.progressPercent !== null ? (
          <div className="mt-auto flex flex-col gap-1">
            <span className="text-xs text-text-muted">
              {t('progress', { percent: Math.round(course.progressPercent) })}
            </span>
            <Progress
              value={course.progressPercent}
              label={t('progress', { percent: Math.round(course.progressPercent) })}
            />
          </div>
        ) : null}
      </div>
    </article>
  );
}
