'use client';
import { MailOpen, ShoppingCart, UserPlus } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { loginUrlWithNext, ROUTES } from '@/features/auth/routes';
import { useSessionBootstrap } from '@/features/auth/use-auth';
import { clearOrderIntent, useCreateOrder, useSelfEnrol } from '@/features/billing/use-billing';
import type { Money } from '@/lib/api/schemas/common';
import { formatMoney } from '@/lib/utils/money';
import { useAuthStore } from '@/stores/auth-store';

type Props = { courseId: string; price: Money | null; selfEnrolEnabled: boolean };

/** Hybrid CTA (ADR-002): paid courses → order + redirect to provider; free self-enrol → join. */
export function CourseCta({ courseId, price, selfEnrolEnabled }: Props) {
  const t = useTranslations('storefront');
  const locale = useLocale();
  const router = useRouter();
  useSessionBootstrap();
  const status = useAuthStore((state) => state.status);
  const createOrder = useCreateOrder();
  const selfEnrol = useSelfEnrol();
  const [code, setCode] = useState('');

  const requireLogin = () => {
    if (status === 'authenticated') return false;
    router.push(loginUrlWithNext(window.location.pathname));
    return true;
  };

  const buy = () => {
    if (requireLogin()) return;
    createOrder.mutate(courseId, {
      onSuccess: (order) => {
        if (order.confirmationUrl) return window.location.assign(order.confirmationUrl);
        clearOrderIntent(courseId);
        router.push(
          order.status === 'paid'
            ? ROUTES.course(courseId)
            : `${ROUTES.checkoutReturn}?orderId=${order.orderId}`,
        );
      },
    });
  };

  const enrol = () => {
    if (requireLogin()) return;
    selfEnrol.mutate(
      { courseId, code: code || undefined },
      { onSuccess: () => router.push(ROUTES.course(courseId)) },
    );
  };

  if (price) {
    return (
      <Button
        size="lg"
        className="h-14 w-full text-base"
        loading={createOrder.isPending}
        onClick={buy}
      >
        <ShoppingCart aria-hidden /> {t('buyFor', { price: formatMoney(price, locale) })}
      </Button>
    );
  }
  if (!selfEnrolEnabled) {
    return (
      <p className="flex items-start gap-3 rounded-md bg-surface-muted p-4 text-sm text-text-muted">
        <MailOpen className="mt-0.5 size-4 shrink-0 text-primary" aria-hidden />
        {t('byInvitation')}
      </p>
    );
  }
  return (
    <div className="flex flex-col gap-3">
      <Input
        aria-label={t('enrolCode')}
        placeholder={t('enrolCodeOptional')}
        value={code}
        onChange={(event) => setCode(event.target.value)}
      />
      <Button
        size="lg"
        className="h-14 w-full text-base"
        loading={selfEnrol.isPending}
        onClick={enrol}
      >
        <UserPlus aria-hidden /> {t('enrolFree')}
      </Button>
    </div>
  );
}
