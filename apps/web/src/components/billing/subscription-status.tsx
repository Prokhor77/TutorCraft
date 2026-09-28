'use client';
import { CalendarClock } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { Badge, type BadgeTone } from '@/components/ui/badge';
import { Panel } from '@/components/ui/page-header';
import { useMe } from '@/features/auth/use-auth';
import { daysLeft } from '@/features/billing/subscription';
import type { Subscription, SubscriptionStatus } from '@/lib/api/schemas/billing';
import { formatDate } from '@/lib/utils/format';

const STATUS_TONES: Record<SubscriptionStatus, BadgeTone> = {
  trial: 'info',
  active: 'success',
  expired: 'danger',
};

/** Current state: trial / active / expired, access end date and what the read-only mode means. */
export function SubscriptionStatusPanel({ subscription }: { subscription: Subscription }) {
  const t = useTranslations('subscription');
  const locale = useLocale();
  const me = useMe();
  const date = formatDate(subscription.accessUntil, locale, me?.timezone);
  const expired = subscription.status === 'expired';
  return (
    <Panel className="mb-4 sm:flex-row sm:items-center sm:justify-between md:mb-gutter">
      <div className="flex items-start gap-3 sm:items-center">
        <span className="flex size-11 shrink-0 items-center justify-center rounded-full bg-primary-soft text-primary">
          <CalendarClock className="size-5" aria-hidden />
        </span>
        <div className="flex min-w-0 flex-col gap-1">
          <span className="flex flex-wrap items-center gap-2">
            <h2 className="text-lg">{t('statusTitle')}</h2>
            <Badge tone={STATUS_TONES[subscription.status]} dot>
              {t(`status.${subscription.status}`)}
            </Badge>
          </span>
          <span className="text-sm text-text-muted">
            {expired
              ? t('expiredHint', { date })
              : t('accessUntil', { date, days: daysLeft(subscription.accessUntil) })}
          </span>
        </div>
      </div>
    </Panel>
  );
}
