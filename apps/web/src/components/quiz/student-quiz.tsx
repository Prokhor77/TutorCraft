'use client';
import {
  Award,
  CalendarClock,
  ClipboardCheck,
  PlayCircle,
  Repeat,
  Target,
  Timer,
  type LucideIcon,
} from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import { BlockRenderer } from '@/components/blockdoc/block-renderer';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Panel } from '@/components/ui/page-header';
import { ROUTES } from '@/features/auth/routes';
import { useStartAttempt } from '@/features/quiz/use-quiz';
import type { ItemDetail } from '@/lib/api/schemas/courses';
import { formatDateTime } from '@/lib/utils/format';
import { SECONDS_PER_MINUTE } from '@/lib/utils/time';

type Fact = { icon: LucideIcon; label: string; value: string | number };

/** Quiz intro for students (Stitch panel language): rules as tinted tiles + start/continue (POST /items/{id}/attempts). */
export function StudentQuiz({ item }: { item: ItemDetail }) {
  const t = useTranslations('quiz');
  const locale = useLocale();
  const router = useRouter();
  const start = useStartAttempt(item.id);
  if (item.settings.kind !== 'quiz') return null;
  const settings = item.settings;
  const closed = settings.closeAt !== null && new Date(settings.closeAt).getTime() < Date.now();
  const notOpen = settings.openAt !== null && new Date(settings.openAt).getTime() > Date.now();
  const facts: Fact[] = [
    {
      icon: Timer,
      label: t('timeLimit'),
      value: settings.timeLimitSec
        ? t('minutes', { count: Math.round(settings.timeLimitSec / SECONDS_PER_MINUTE) })
        : t('noLimit'),
    },
    { icon: Repeat, label: t('attempts'), value: settings.maxAttempts ?? t('unlimited') },
    {
      icon: Award,
      label: t('gradingMethod'),
      value: t(`gradingMethods.${settings.gradingMethod}`),
    },
    ...(settings.passPercent !== null
      ? [{ icon: Target, label: t('passPercent'), value: `${settings.passPercent}%` }]
      : []),
    ...(settings.closeAt
      ? [
          {
            icon: CalendarClock,
            label: t('closesAt'),
            value: formatDateTime(settings.closeAt, locale),
          },
        ]
      : []),
  ];
  const hasContent = !!item.content && item.content.blocks.length > 0;
  return (
    <div className="flex flex-col gap-gutter">
      {hasContent ? (
        <section className="rounded-lg border border-card-border bg-surface p-5 shadow-sm sm:p-8">
          <BlockRenderer doc={item.content} />
        </section>
      ) : null}
      <Panel
        title={
          <span className="flex items-center gap-2.5">
            <span className="flex size-9 items-center justify-center rounded-full bg-primary-soft text-primary">
              <ClipboardCheck className="size-[1.125rem]" aria-hidden />
            </span>
            {t('rulesTitle')}
          </span>
        }
        actions={
          closed ? (
            <Badge tone="danger" dot>
              {t('closed')}
            </Badge>
          ) : notOpen ? (
            <Badge tone="warning" dot>
              {t('notOpenYet')}
            </Badge>
          ) : (
            <Badge tone="success" dot>
              {t('openNow')}
            </Badge>
          )
        }
      >
        <dl className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-3">
          {facts.map((fact) => (
            <div
              key={fact.label}
              className="flex items-center gap-3 rounded bg-surface-muted px-4 py-3.5"
            >
              <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-surface text-primary shadow-sm">
                <fact.icon className="size-4" aria-hidden />
              </span>
              <div className="flex min-w-0 flex-col">
                <dt className="text-label-md uppercase text-text-muted">{fact.label}</dt>
                <dd className="truncate font-heading text-base font-semibold">{fact.value}</dd>
              </div>
            </div>
          ))}
        </dl>
        {settings.timeLimitSec ? (
          <p className="flex items-start gap-2.5 rounded bg-warning-soft px-4 py-3 text-sm text-warning">
            <Timer className="mt-0.5 size-4 shrink-0" aria-hidden />
            {t('timerWarning')}
          </p>
        ) : null}
        <div className="flex flex-col gap-3 border-t border-border pt-4 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-sm text-text-muted">{t('autosaveHint')}</p>
          <Button
            size="lg"
            className="w-full sm:w-auto"
            disabled={closed || notOpen}
            loading={start.isPending}
            onClick={() =>
              start.mutate(undefined, {
                onSuccess: (attempt) =>
                  router.push(ROUTES.attempt(item.courseId, item.id, attempt.id)),
              })
            }
          >
            <PlayCircle aria-hidden />{' '}
            {closed ? t('closed') : notOpen ? t('notOpenYet') : t('start')}
          </Button>
        </div>
      </Panel>
    </div>
  );
}
