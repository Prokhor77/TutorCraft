import { Suspense } from 'react';
import { CheckoutReturn } from '@/components/public/checkout';

export default function CheckoutReturnPage() {
  return (
    <Suspense>
      <CheckoutReturn />
    </Suspense>
  );
}
