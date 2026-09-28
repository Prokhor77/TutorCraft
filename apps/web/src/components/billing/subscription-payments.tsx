'use client';
import { useLocale, useTranslations } from 'next-intl';
import { Panel } from '@/components/ui/page-header';
import { useMe } from '@/features/auth/use-auth';
import type { Subscription } from '@/lib/api/schemas/billing';
import { formatDate } from '@/lib/utils/format';
import { formatMoney } from '@/lib/utils/money';

/** Recent subscription payments (owner only). */
export function SubscriptionPayments({ payments }: { payments: Subscription['payments'] }) {
  const t = useTranslations('subscription');
  const locale = useLocale();
  const me = useMe();
  if (payments.length === 0) return null;
  const date = (iso: string) => formatDate(iso, locale, me?.timezone);
  return (
    <Panel title={t('paymentsTitle')} className="mt-4 md:mt-gutter">
      <ul className="flex flex-col divide-y divide-border">
        {payments.map((payment) => (
          <li
            key={payment.id}
            className="flex flex-wrap items-center justify-between gap-2 py-3 text-sm"
          >
            <span className="flex flex-col">
              <span className="font-semibold">{t(`terms.${payment.term}`)}</span>
              <span className="text-text-muted">
                {t('period', { from: date(payment.periodStart), to: date(payment.periodEnd) })}
              </span>
            </span>
            <span className="font-semibold">{formatMoney(payment.amount, locale)}</span>
          </li>
        ))}
      </ul>
    </Panel>
  );
}
