import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';
import { Suspense } from 'react';
import { AcceptInvitationForm } from '@/components/auth/password-forms';

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('auth');
  return { title: t('acceptInviteTitle') };
}

export default function Page() {
  return (
    <Suspense>
      <AcceptInvitationForm />
    </Suspense>
  );
}
