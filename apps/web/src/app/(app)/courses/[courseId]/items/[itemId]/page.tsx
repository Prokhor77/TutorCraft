'use client';
import { useParams } from 'next/navigation';
import { Suspense } from 'react';
import { ItemPage } from '@/components/items/item-page';

export default function ItemRoute() {
  const { itemId } = useParams<{ itemId: string }>();
  return (
    <Suspense>
      <ItemPage key={itemId} itemId={itemId} />
    </Suspense>
  );
}
