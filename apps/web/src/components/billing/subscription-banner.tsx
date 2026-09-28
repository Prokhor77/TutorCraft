'use client';
import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { Alert } from '@/components/ui/alert';
import { ROUTES } from '@/features/auth/routes';
import { useMe } from '@/features/auth/use-auth';
import { subscriptionNotice } from '@/features/billing/subscription';
import { useSubscription } from '@/features/billing/use-subscription';
import { canCreateCoursesHint } from '@/lib/access/permissions';

/** Staff-only reminder above the page: access ended (read-only mode) or ends within a few days. */
export function SubscriptionBanner() {
  const t = useTranslations('subscription');
  const me = useMe();
  const staff = canCreateCoursesHint(me?.tenantRoles);
  const subscription = useSubscription(staff);
  if (!staff || !subscription.data) return null;
  const notice = subscriptionNotice(subscription.data);
  if (!notice) return null;
  const link = (
    <Link href={ROUTES.subscriptionSettings} className="font-semibold text-primary underline">
      {t('renew')}
    </Link>
  );
  return notice.kind === 'expired' ? (
    <Alert tone="danger" className="mb-4 md:mb-gutter" title={t('bannerExpired')}>
      {t('bannerExpiredHint')} {link}
    </Alert>
  ) : (
    <Alert
      tone="warning"
      className="mb-4 md:mb-gutter"
      title={t('bannerEndingSoon', { days: notice.days })}
    >
      {link}
    </Alert>
  );
}
