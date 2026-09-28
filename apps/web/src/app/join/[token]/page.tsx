import { JoinInvite } from '@/components/public/join-invite';
import CheckoutLayout from '../../checkout/layout';

export default async function JoinPage({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  return (
    <CheckoutLayout>
      <JoinInvite token={token} />
    </CheckoutLayout>
  );
}
