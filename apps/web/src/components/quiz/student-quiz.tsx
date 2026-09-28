'use client';
import { PlayCircle } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import { BlockRenderer } from '@/components/blockdoc/block-renderer';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { ROUTES } from '@/features/auth/routes';
import { useStartAttempt } from '@/features/quiz/use-quiz';
import type { ItemDetail } from '@/lib/api/schemas/courses';
import { formatDateTime } from '@/lib/utils/format';
import { SECONDS_PER_MINUTE } from '@/lib/utils/time';

/** Quiz intro for students: rules summary + start/continue (POST /items/{id}/attempts). */
export function StudentQuiz({ item }: { item: ItemDetail }) {
  const t = useTranslations('quiz');
  const locale = useLocale();
  const router = useRouter();
  const start = useStartAttempt(item.id);
  if (item.settings.kind !== 'quiz') return null;
  const settings = item.settings;
  const closed = settings.closeAt !== null && new Date(settings.closeAt).getTime() < Date.now();
  const notOpen = settings.openAt !== null && new Date(settings.openAt).getTime() > Date.now();
  const facts = [
    {
      label: t('timeLimit'),
      value: settings.timeLimitSec
        ? t('minutes', { count: Math.round(settings.timeLimitSec / SECONDS_PER_MINUTE) })
        : t('noLimit'),
    },
    { label: t('attempts'), value: settings.maxAttempts ?? t('unlimited') },
    { label: t('gradingMethod'), value: t(`gradingMethods.${settings.gradingMethod}`) },
    ...(settings.passPercent !== null
      ? [{ label: t('passPercent'), value: `${settings.passPercent}%` }]
      : []),
    ...(settings.closeAt
      ? [{ label: t('closesAt'), value: formatDateTime(settings.closeAt, locale) }]
      : []),
  ];
  return (
    <div className="flex flex-col gap-6">
      <BlockRenderer doc={item.content} />
      <Card className="flex flex-col gap-4 p-5">
        <dl className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          {facts.map((fact) => (
            <div key={fact.label} className="flex flex-col">
              <dt className="text-xs text-text-muted">{fact.label}</dt>
              <dd className="font-medium">{fact.value}</dd>
            </div>
          ))}
        </dl>
        {settings.timeLimitSec ? (
          <p className="text-sm text-text-muted">{t('timerWarning')}</p>
        ) : null}
        <Button
          size="lg"
          className="self-start"
          disabled={closed || notOpen}
          loading={start.isPending}
          onClick={() =>
            start.mutate(undefined, {
              onSuccess: (attempt) =>
                router.push(ROUTES.attempt(item.courseId, item.id, attempt.id)),
            })
          }
        >
          <PlayCircle aria-hidden /> {closed ? t('closed') : notOpen ? t('notOpenYet') : t('start')}
        </Button>
      </Card>
    </div>
  );
}
