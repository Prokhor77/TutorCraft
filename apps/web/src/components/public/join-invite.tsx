'use client';
import { useMutation } from '@tanstack/react-query';
import { ArrowRight, Link2, Link2Off } from 'lucide-react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { useEffect, useRef } from 'react';
import { Button } from '@/components/ui/button';
import { Spinner } from '@/components/ui/spinner';
import { describeProblem } from '@/features/app/use-problem-toast';
import { loginUrlWithNext, ROUTES } from '@/features/auth/routes';
import { useSessionBootstrap } from '@/features/auth/use-auth';
import { enrollmentApi } from '@/lib/api/endpoints/enrollment';
import { useAuthStore } from '@/stores/auth-store';
import { StatusIllustration, StatusMessage } from './status-card';

function Joining({ label }: { label: string }) {
  return (
    <div className="flex flex-col items-center gap-4 text-center">
      <StatusIllustration icon={Link2} />
      <Spinner label={label} />
      <p className="text-sm text-text-muted">{label}</p>
    </div>
  );
}

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

  if (status === 'unknown' || accept.isPending) return <Joining label={t('joining')} />;
  if (status === 'anonymous') {
    const next = loginUrlWithNext(`/join/${encodeURIComponent(token)}`);
    return (
      <StatusMessage
        icon={Link2}
        title={t('title')}
        description={t('loginRequired')}
        actions={
          <Button asChild size="lg" className="h-14 px-8 text-base">
            <Link href={next}>
              {t('login')} <ArrowRight aria-hidden />
            </Link>
          </Button>
        }
      />
    );
  }
  if (accept.isError)
    return (
      <StatusMessage
        icon={Link2Off}
        tone="danger"
        role="alert"
        title={t('failed')}
        description={describeProblem(accept.error, tErrors).title}
        actions={
          <Button asChild variant="secondary" size="lg">
            <Link href={ROUTES.home}>{tErrors('goHome')}</Link>
          </Button>
        }
      />
    );
  return <Joining label={t('joining')} />;
}
