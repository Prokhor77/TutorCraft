'use client';
import { Check } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { monthlyPriceMinor, termSavingPercent } from '@/features/billing/subscription';
import type { Subscription, SubscriptionTerm } from '@/lib/api/schemas/billing';
import { cn } from '@/lib/utils/cn';
import { formatMoney } from '@/lib/utils/money';

/** The longest term is highlighted as the best value. */
const HIGHLIGHTED_TERM: SubscriptionTerm = 'year';

/** Term cards: same product, different duration. Buying is enabled only for `canManage`. */
export function SubscriptionPlans({
  subscription,
  pendingTerm,
  onPurchase,
}: {
  subscription: Subscription;
  pendingTerm: SubscriptionTerm | null;
  onPurchase: (term: SubscriptionTerm) => void;
}) {
  const t = useTranslations('subscription');
  const locale = useLocale();
  const { terms, canManage } = subscription;
  return (
    <ul className="grid grid-cols-1 gap-4 md:grid-cols-3 md:gap-gutter">
      {terms.map((term) => {
        const saving = termSavingPercent(term, terms);
        const highlighted = term.term === HIGHLIGHTED_TERM;
        return (
          <li
            key={term.term}
            className={cn(
              'flex flex-col gap-4 rounded-lg border bg-surface p-5 shadow-sm',
              highlighted ? 'border-accent ring-2 ring-primary' : 'border-card-border',
            )}
          >
            <div className="flex items-center justify-between gap-2">
              <h3 className="text-lg">{t(`terms.${term.term}`)}</h3>
              {saving > 0 ? <Badge tone="success">{t('saving', { percent: saving })}</Badge> : null}
            </div>
            <div className="flex flex-1 flex-col gap-1">
              <p className="font-heading text-3xl font-bold tracking-tight">
                {formatMoney(term.price, locale)}
              </p>
              <p className="text-sm text-text-muted">
                {t('perMonth', {
                  amount: formatMoney(
                    { amountMinor: monthlyPriceMinor(term), currency: term.price.currency },
                    locale,
                  ),
                })}
              </p>
            </div>
            <Button
              variant={highlighted ? 'primary' : 'secondary'}
              className="w-full"
              disabled={!canManage || (pendingTerm !== null && pendingTerm !== term.term)}
              loading={pendingTerm === term.term}
              onClick={() => onPurchase(term.term)}
            >
              <Check aria-hidden /> {t('buy')}
            </Button>
          </li>
        );
      })}
    </ul>
  );
}
