'use client';
import * as AvatarPrimitive from '@radix-ui/react-avatar';
import { cn } from '@/lib/utils/cn';
import { initials } from '@/lib/utils/format';

const sizes = { sm: 'size-7 text-xs', md: 'size-9 text-sm', lg: 'size-14 text-lg' } as const;

export function Avatar({
  name,
  src,
  size = 'md',
  className,
}: {
  name: string;
  src?: string | null;
  size?: keyof typeof sizes;
  className?: string;
}) {
  return (
    <AvatarPrimitive.Root
      className={cn(
        'inline-flex shrink-0 overflow-hidden rounded-full bg-primary-soft',
        sizes[size],
        className,
      )}
    >
      {src ? (
        <AvatarPrimitive.Image src={src} alt={name} className="size-full object-cover" />
      ) : null}
      <AvatarPrimitive.Fallback
        className="flex size-full items-center justify-center font-semibold text-primary"
        aria-label={name}
      >
        {initials(name)}
      </AvatarPrimitive.Fallback>
    </AvatarPrimitive.Root>
  );
}
