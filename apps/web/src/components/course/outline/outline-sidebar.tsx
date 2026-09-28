'use client';
import { useTranslations } from 'next-intl';
import { Progress } from '@/components/ui/progress';
import { moduleProgress } from '@/features/courses/outline-moves';
import type { OutlineModule } from '@/lib/api/schemas/courses';

/** Left outline with per-module progress (SPEC §10 «Страница курса»). */
export function OutlineSidebar({
  modules,
  coursePercent,
}: {
  modules: OutlineModule[];
  coursePercent: number | null;
}) {
  const t = useTranslations('course');
  return (
    <nav
      aria-label={t('outline')}
      className="sticky top-[calc(var(--size-header)+1rem)] flex flex-col gap-4 rounded-lg border border-border bg-surface p-4"
    >
      {coursePercent !== null ? (
        <div className="flex flex-col gap-1.5">
          <span className="text-sm font-medium">
            {t('courseProgress', { percent: Math.round(coursePercent) })}
          </span>
          <Progress
            value={coursePercent}
            label={t('courseProgress', { percent: Math.round(coursePercent) })}
            tone={coursePercent >= 100 ? 'success' : 'primary'}
          />
        </div>
      ) : null}
      <ol className="flex flex-col gap-1">
        {modules.map((module) => {
          const { done, total } = moduleProgress(module);
          return (
            <li key={module.id}>
              <a
                href={`#module-${module.id}`}
                className="flex flex-col gap-1 rounded-md px-2 py-1.5 text-sm hover:bg-surface-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
              >
                <span className="truncate">{module.title}</span>
                {total > 0 ? (
                  <Progress
                    value={(done / total) * 100}
                    label={t('moduleProgress', { done, total })}
                    className="h-1"
                    tone={done === total ? 'success' : 'primary'}
                  />
                ) : null}
              </a>
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
