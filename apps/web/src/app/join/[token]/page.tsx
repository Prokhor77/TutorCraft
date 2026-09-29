import { JoinInvite } from '@/components/public/join-invite';
import { PublicCardLayout } from '@/components/public/public-card-layout';

export default async function JoinPage({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  return (
    <PublicCardLayout>
      <JoinInvite token={token} />
    </PublicCardLayout>
  );
}
