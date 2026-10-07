import type { Metadata } from 'next';
import { JoinInvite } from '@/components/public/join-invite';
import { PublicCardLayout } from '@/components/public/public-card-layout';
import { robots } from '@/lib/seo/metadata';

/** Invite links carry a secret token: never index them. */
export function generateMetadata(): Metadata {
  return { robots: robots('private') };
}

export default async function JoinPage({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  return (
    <PublicCardLayout>
      <JoinInvite token={token} />
    </PublicCardLayout>
  );
}
