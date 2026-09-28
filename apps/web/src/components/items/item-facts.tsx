'use client';
import { useLocale, useTranslations } from 'next-intl';
import type { ReactNode } from 'react';
import { Panel } from '@/components/ui/page-header';
import type { ItemDetail } from '@/lib/api/schemas/courses';
import { formatDateTime } from '@/lib/utils/format';
import { SECONDS_PER_MINUTE } from '@/lib/utils/time';

type Fact = { key: string; label: string; value: ReactNode };

function useFacts(item: ItemDetail, author: boolean): Fact[] {
  const t = useTranslations('itemSettings');
  const tView = useTranslations('itemView');
  const locale = useLocale();
  const facts: Fact[] = [];
  const unlimited = tView('unlimited');
  const settings = item.settings;
  if (settings.kind === 'assignment') {
    if (settings.dueAt)
      facts.push({ key: 'due', label: t('dueAt'), value: formatDateTime(settings.dueAt, locale) });
    facts.push({
      key: 'submission',
      label: t('submissionType'),
      value: t(`submissionTypes.${settings.submissionType}`),
    });
    facts.push({ key: 'max', label: t('maxScore'), value: settings.maxScore });
    facts.push({
      key: 'attempts',
      label: t('maxAttempts'),
      value: settings.maxAttempts ?? unlimited,
    });
  }
  if (settings.kind === 'quiz') {
    if (settings.closeAt)
      facts.push({
        key: 'close',
        label: t('closeAt'),
        value: formatDateTime(settings.closeAt, locale),
      });
    facts.push({
      key: 'time',
      label: t('timeLimitMinutes'),
      value:
        settings.timeLimitSec === null
          ? unlimited
          : Math.round(settings.timeLimitSec / SECONDS_PER_MINUTE),
    });
    facts.push({
      key: 'attempts',
      label: t('maxAttempts'),
      value: settings.maxAttempts ?? unlimited,
    });
    if (settings.passPercent !== null)
      facts.push({ key: 'pass', label: t('passPercent'), value: settings.passPercent });
    facts.push({ key: 'max', label: t('maxScore'), value: settings.maxScore });
  }
  if (settings.kind === 'forum')
    facts.push({
      key: 'forum',
      label: t('forumType'),
      value: t(`forumTypes.${settings.forumType}`),
    });
  if (author) {
    facts.push({
      key: 'visibility',
      label: t('visibility'),
      value: t(`visibilityOptions.${item.visibility}`),
    });
    facts.push({
      key: 'completion',
      label: t('completionMode'),
      value: t(`completionModes.${item.completionRule.mode}`),
    });
    facts.push({
      key: 'conditions',
      label: t('conditions'),
      value: tView('conditionsCount', { count: item.conditions?.conditions.length ?? 0 }),
    });
  }
  return facts;
}

/**
 * Stitch side panel «Параметры»: the item's real key settings as label/value rows (deadline, attempts, scoring;
 * authors also see visibility, completion rule and access conditions). Renders nothing when there is nothing to show.
 */
export function ItemFacts({
  item,
  author,
  footer,
}: {
  item: ItemDetail;
  author: boolean;
  footer?: ReactNode;
}) {
  const t = useTranslations('itemView');
  const facts = useFacts(item, author);
  if (facts.length === 0 && !footer) return null;
  return (
    <Panel as="aside" title={t('factsTitle')} className="gap-3">
      {facts.length > 0 ? (
        <dl className="flex flex-col divide-y divide-outline-variant/40">
          {facts.map((fact) => (
            <div key={fact.key} className="flex items-baseline justify-between gap-3 py-2.5">
              <dt className="text-sm text-text-muted">{fact.label}</dt>
              <dd className="text-right text-sm font-semibold">{fact.value}</dd>
            </div>
          ))}
        </dl>
      ) : null}
      {footer}
    </Panel>
  );
}

/** Whether {@link ItemFacts} has anything to render for this item/audience (drives the two-column layout). */
export function hasItemFacts(item: ItemDetail, author: boolean): boolean {
  return author || ['assignment', 'quiz', 'forum'].includes(item.settings.kind);
}
