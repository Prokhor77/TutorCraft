'use client';
import { CheckCircle2, CreditCard, XCircle } from 'lucide-react';
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

  if (!authenticated || order.isLoading) return <Spinner label={t('loading')} />;
  if (!order.data) return <Alert tone="danger" title={t('orderNotFound')} />;
  return (
    <div className="flex flex-col gap-4">
      <Alert tone="warning" title={t('fakeTitle')}>
        {t('fakeHint')}
      </Alert>
      <dl className="grid grid-cols-[auto_minmax(0,1fr)] gap-x-4 gap-y-2 text-sm">
        <dt className="text-text-muted">{t('course')}</dt>
        <dd className="font-medium">{order.data.courseTitle}</dd>
        <dt className="text-text-muted">{t('amount')}</dt>
        <dd className="font-medium">{formatMoney(order.data.amount, locale)}</dd>
      </dl>
      <Button
        size="lg"
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
      <div className="flex flex-col items-center gap-3 text-center">
        <Spinner label={t('waiting')} />
        <p className="text-sm text-text-muted">{t('waiting')}</p>
      </div>
    );
  }
  const failed = order.data && order.data.status !== 'paid';
  return (
    <div className="flex flex-col items-center gap-4 text-center">
      {failed ? (
        <XCircle className="size-12 text-danger" aria-hidden />
      ) : (
        <CheckCircle2 className="size-12 text-success" aria-hidden />
      )}
      <h1 className="text-2xl">{failed ? t('failedTitle') : t('successTitle')}</h1>
      <p className="text-sm text-text-muted">{failed ? t('failedHint') : t('successHint')}</p>
      {targetCourseId && !failed ? (
        <Button asChild size="lg">
          <Link href={ROUTES.course(targetCourseId)}>{t('goToCourse')}</Link>
        </Button>
      ) : (
        <Button asChild variant="secondary">
          <Link href={ROUTES.home}>{t('goHome')}</Link>
        </Button>
      )}
    </div>
  );
}
