import { ArrowRight, BookOpen, Layers } from 'lucide-react';
import Link from 'next/link';
import { getTranslations } from 'next-intl/server';
import { Avatar } from '@/components/ui/avatar';
import { Badge } from '@/components/ui/badge';
import { ROUTES } from '@/features/auth/routes';
import type { PublicCourse } from '@/lib/api/schemas/courses';
import { cn } from '@/lib/utils/cn';
import { formatMoney } from '@/lib/utils/money';

/** Decorative stand-in when a course has no cover: soft violet gradient with a book chip. */
export function CoverPlaceholder({ className }: { className?: string }) {
  return (
    <div
      aria-hidden
      className={cn(
        'relative flex items-center justify-center overflow-hidden bg-gradient-to-br from-primary-soft via-surface-muted to-success-soft',
        className,
      )}
    >
      <span className="absolute -right-6 -top-8 size-32 rounded-full bg-surface/50" />
      <span className="absolute -bottom-10 left-6 size-24 rounded-full bg-accent/10" />
      <span className="relative flex size-16 items-center justify-center rounded-full bg-surface text-primary shadow-md">
        <BookOpen className="size-7" strokeWidth={1.75} />
      </span>
    </div>
  );
}

/** Storefront course card in the landing style: 2rem white card, inset cover, price chip, real counts only. */
export async function StorefrontCourseCard({
  course,
  tenantSlug,
  locale,
}: {
  course: PublicCourse;
  tenantSlug: string;
  locale: string;
}) {
  const t = await getTranslations('storefront');
  const itemCount = course.modules.reduce((sum, module) => sum + module.itemCount, 0);
  return (
    <Link
      href={ROUTES.courseLanding(tenantSlug, course.slug)}
      className="lift group flex h-full flex-col gap-4 rounded-lg border border-card-border bg-surface p-3 shadow-sm transition-[box-shadow,border-color,transform] duration-fast hover:border-card-border-hover hover:shadow-md focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/25"
    >
      <div className="relative overflow-hidden rounded-md">
        {course.coverUrl ? (
          // eslint-disable-next-line @next/next/no-img-element -- storage URL
          <img src={course.coverUrl} alt="" className="aspect-video w-full object-cover" />
        ) : (
          <CoverPlaceholder className="aspect-video w-full" />
        )}
        <Badge
          tone={course.price ? 'primary' : 'success'}
          className="absolute left-3 top-3 px-3 py-1 shadow-sm"
        >
          {course.price ? formatMoney(course.price, locale) : t('free')}
        </Badge>
      </div>
      <div className="flex flex-1 flex-col gap-3 px-3 pb-3">
        <h3 className="text-lg group-hover:text-primary">{course.title}</h3>
        <div className="flex items-center gap-2 text-sm text-text-muted">
          <Avatar name={course.teacher.name} src={course.teacher.avatarUrl} size="sm" />
          <span className="truncate">{course.teacher.name}</span>
        </div>
        <div className="mt-auto flex flex-wrap items-center justify-between gap-2 border-t border-border pt-3">
          <span className="flex items-center gap-1.5 text-label-md text-text-muted">
            <Layers className="size-4 text-primary" aria-hidden />
            {t('modulesCount', { count: course.modules.length })} ·{' '}
            {t('itemsCount', { count: itemCount })}
          </span>
          <span className="flex items-center gap-1 text-label-lg text-primary">
            {t('openCourse')}
            <ArrowRight
              className="size-4 transition-transform duration-fast group-hover:translate-x-0.5"
              aria-hidden
            />
          </span>
        </div>
      </div>
    </Link>
  );
}
