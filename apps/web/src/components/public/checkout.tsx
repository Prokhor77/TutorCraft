'use client';
import { CheckCircle2, CreditCard, FileSearch, Hourglass, XCircle } from 'lucide-react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Spinner } from '@/components/ui/spinner';
import { ROUTES } from '@/features/auth/routes';
import { useSessionBootstrap } from '@/features/auth/use-auth';
import { clearOrderIntent, useFakePay, useOrder } from '@/features/billing/use-billing';
import { formatMoney } from '@/lib/utils/money';
import { useAuthStore } from '@/stores/auth-store';
import { StatusIllustration, StatusMessage } from './status-card';

function useAuthenticated(): boolean {
  useSessionBootstrap();
  return useAuthStore((state) => state.status === 'authenticated');
}

/** Dev-only fake payment page (PAYMENT_PROVIDER=fake). */
export function FakeCheckout({ orderId }: { orderId: string }) {
  const t = useTranslations('checkout');
  const locale = useLocale();
  const router = useRouter();
  const authenticated = useAuthenticated();
  const order = useOrder(authenticated ? orderId : null);
  const pay = useFakePay();

  if (!authenticated || order.isLoading) {
    return (
      <div className="flex justify-center py-6">
        <Spinner label={t('loading')} />
      </div>
    );
  }
  if (!order.data) {
    return (
      <StatusMessage
        icon={FileSearch}
        tone="danger"
        role="alert"
        title={t('orderNotFound')}
        actions={
          <Button asChild variant="secondary" size="lg">
            <Link href={ROUTES.home}>{t('goHome')}</Link>
          </Button>
        }
      />
    );
  }
  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col items-center gap-4 text-center">
        <StatusIllustration icon={CreditCard} />
        <h1 className="text-2xl">{t('fakeTitle')}</h1>
      </div>
      <Alert tone="warning">{t('fakeHint')}</Alert>
      <section aria-labelledby="order-summary-title" className="rounded-md bg-surface-muted p-5">
        <h2 id="order-summary-title" className="mb-3 text-label-md uppercase text-text-muted">
          {t('orderSummary')}
        </h2>
        <dl className="flex flex-col gap-3 text-sm">
          <div className="flex items-start justify-between gap-4">
            <dt className="text-text-muted">{t('course')}</dt>
            <dd className="text-right font-semibold">{order.data.courseTitle}</dd>
          </div>
          <div className="flex items-baseline justify-between gap-4 border-t border-border pt-3">
            <dt className="text-text-muted">{t('amount')}</dt>
            <dd className="font-heading text-2xl font-bold text-primary">
              {formatMoney(order.data.amount, locale)}
            </dd>
          </div>
        </dl>
      </section>
      <Button
        size="lg"
        className="h-14 w-full text-base"
        loading={pay.isPending}
        onClick={() =>
          pay.mutate(orderId, {
            onSuccess: () => router.replace(`${ROUTES.checkoutReturn}?orderId=${orderId}`),
          })
        }
      >
        <CreditCard aria-hidden /> {t('pay')}
      </Button>
    </div>
  );
}

/** Return page after the provider: polls the order until a terminal state (webhook may lag). */
export function CheckoutReturn() {
  const t = useTranslations('checkout');
  const params = useSearchParams();
  const orderId = params.get('orderId');
  const courseId = params.get('courseId');
  const authenticated = useAuthenticated();
  const order = useOrder(authenticated ? orderId : null, true);
  const targetCourseId = order.data?.courseId ?? courseId;

  if (targetCourseId && order.data?.status === 'paid') clearOrderIntent(targetCourseId);
  if (orderId && (!order.data || order.data.status === 'pending')) {
    return (
      <div className="flex flex-col items-center gap-4 text-center">
        <StatusIllustration icon={Hourglass} tone="warning" />
        <Spinner label={t('waiting')} />
        <p className="text-sm text-text-muted">{t('waiting')}</p>
      </div>
    );
  }
  const failed = order.data && order.data.status !== 'paid';
  return (
    <StatusMessage
      icon={failed ? XCircle : CheckCircle2}
      tone={failed ? 'danger' : 'success'}
      role={failed ? 'alert' : 'status'}
      title={failed ? t('failedTitle') : t('successTitle')}
      description={failed ? t('failedHint') : t('successHint')}
      actions={
        targetCourseId && !failed ? (
          <Button asChild size="lg" className="h-14 px-8 text-base">
            <Link href={ROUTES.course(targetCourseId)}>{t('goToCourse')}</Link>
          </Button>
        ) : (
          <Button asChild variant="secondary" size="lg">
            <Link href={ROUTES.home}>{t('goHome')}</Link>
          </Button>
        )
      }
    />
  );
}
