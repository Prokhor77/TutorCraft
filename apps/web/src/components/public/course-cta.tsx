'use client';
import { MailOpen, UserPlus } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { loginUrlWithNext, ROUTES } from '@/features/auth/routes';
import { useSessionBootstrap } from '@/features/auth/use-auth';
import { useSelfEnrol } from '@/features/enrollment/use-self-enrol';
import { useAuthStore } from '@/stores/auth-store';

type Props = { courseId: string; selfEnrolEnabled: boolean };

/** Course landing CTA: self-enrol (optionally by code) or «by invitation» — courses are free for students (ADR-012). */
export function CourseCta({ courseId, selfEnrolEnabled }: Props) {
  const t = useTranslations('storefront');
  const router = useRouter();
  useSessionBootstrap();
  const status = useAuthStore((state) => state.status);
  const selfEnrol = useSelfEnrol();
  const [code, setCode] = useState('');

  const requireLogin = () => {
    if (status === 'authenticated') return false;
    router.push(loginUrlWithNext(window.location.pathname));
    return true;
  };

  const enrol = () => {
    if (requireLogin()) return;
    selfEnrol.mutate(
      { courseId, code: code || undefined },
      { onSuccess: () => router.push(ROUTES.course(courseId)) },
    );
  };

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
        <UserPlus aria-hidden /> {t('enrol')}
      </Button>
    </div>
  );
}
