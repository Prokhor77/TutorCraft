'use client';
import { useMutation } from '@tanstack/react-query';
import { Link2 } from 'lucide-react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { useEffect, useRef } from 'react';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Spinner } from '@/components/ui/spinner';
import { describeProblem } from '@/features/app/use-problem-toast';
import { loginUrlWithNext, ROUTES } from '@/features/auth/routes';
import { useSessionBootstrap } from '@/features/auth/use-auth';
import { enrollmentApi } from '@/lib/api/endpoints/enrollment';
import { useAuthStore } from '@/stores/auth-store';

/** /join/[token]: course invite link (FR-ENROL-03). Requires login; accepts once, then opens the course. */
export function JoinInvite({ token }: { token: string }) {
  const t = useTranslations('join');
  const tErrors = useTranslations('errors');
  const router = useRouter();
  useSessionBootstrap();
  const status = useAuthStore((state) => state.status);
  const accept = useMutation({
    mutationFn: () => enrollmentApi.acceptInviteLink(token),
    meta: { skipErrorToast: true },
  });
  const started = useRef(false);

  useEffect(() => {
    if (status !== 'authenticated' || started.current) return;
    started.current = true;
    accept.mutate(undefined, {
      onSuccess: ({ courseId }) => router.replace(ROUTES.course(courseId)),
    });
  }, [status, accept, router]);

  if (status === 'unknown' || accept.isPending) return <Spinner label={t('joining')} />;
  if (status === 'anonymous') {
    const next = loginUrlWithNext(`/join/${encodeURIComponent(token)}`);
    return (
      <div className="flex flex-col items-center gap-4 text-center">
        <Link2 className="size-10 text-primary" aria-hidden />
        <h1 className="text-2xl">{t('title')}</h1>
        <p className="text-sm text-text-muted">{t('loginRequired')}</p>
        <Button asChild size="lg" className="w-full">
          <Link href={next}>{t('login')}</Link>
        </Button>
      </div>
    );
  }
  if (accept.isError)
    return (
      <Alert tone="danger" title={t('failed')}>
        {describeProblem(accept.error, tErrors).title}
      </Alert>
    );
  return <Spinner label={t('joining')} />;
}
