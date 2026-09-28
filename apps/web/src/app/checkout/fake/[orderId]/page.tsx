import { FakeCheckout } from '@/components/public/checkout';

export default async function FakeCheckoutPage({
  params,
}: {
  params: Promise<{ orderId: string }>;
}) {
  const { orderId } = await params;
  return <FakeCheckout orderId={orderId} />;
}
