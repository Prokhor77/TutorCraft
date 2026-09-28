'use client';
import { Check } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { SubscriptionPayments } from '@/components/billing/subscription-payments';
import { SubscriptionPlans } from '@/components/billing/subscription-plans';
import { SubscriptionStatusPanel } from '@/components/billing/subscription-status';
import { SettingsTabs } from '@/components/settings/settings-tabs';
import { Alert } from '@/components/ui/alert';
import { ErrorState } from '@/components/ui/error-state';
import { PageHeader, Panel } from '@/components/ui/page-header';
import { SkeletonList } from '@/components/ui/skeleton';
import { LANDING } from '@/content/landing';
import { localize } from '@/content/localize';
import { toast } from '@/components/ui/toast';
import { usePurchaseSubscription, useSubscription } from '@/features/billing/use-subscription';

/** School subscription (settings): status, term purchase (fake provider → instant activation), payment history. */
export default function SubscriptionSettingsPage() {
  const t = useTranslations('subscription');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const subscription = useSubscription();
  const purchase = usePurchaseSubscription();

  const header = (
    <PageHeader title={t('title')} description={t('description')}>
      <SettingsTabs />
    </PageHeader>
  );

  if (!subscription.data)
    return (
      <>
        {header}
        {subscription.isError ? (
          <ErrorState
            title={t('loadError')}
            retryLabel={tCommon('retry')}
            onRetry={() => void subscription.refetch()}
          />
        ) : (
          <SkeletonList label={tCommon('loading')} />
        )}
      </>
    );

  const data = subscription.data;
  return (
    <>
      {header}
      <SubscriptionStatusPanel subscription={data} />
      {data.canManage ? null : (
        <Alert tone="info" className="mb-4 md:mb-gutter" title={t('ownerOnly')} />
      )}
      <SubscriptionPlans
        subscription={data}
        pendingTerm={purchase.isPending ? (purchase.variables ?? null) : null}
        onPurchase={(term) =>
          purchase.mutate(term, {
            onSuccess: () => toast({ tone: 'success', title: t('purchased') }),
          })
        }
      />
      <Panel title={t('featuresTitle')} className="mt-4 md:mt-gutter">
        <ul className="grid grid-cols-1 gap-x-8 gap-y-2.5 sm:grid-cols-2">
          {/* Same list as the landing pricing: one source of truth for what a subscription includes. */}
          {LANDING.pricing.features.map((feature) => (
            <li key={feature.ru} className="flex items-start gap-2 text-sm">
              <Check className="mt-0.5 size-4 shrink-0 text-success" aria-hidden />
              {localize(feature, locale)}
            </li>
          ))}
        </ul>
      </Panel>
      <SubscriptionPayments payments={data.payments} />
    </>
  );
}
