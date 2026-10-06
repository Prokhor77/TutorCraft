'use client';
import * as ProgressPrimitive from '@radix-ui/react-progress';
import { cn } from '@/lib/utils/cn';

const PERCENT_MAX = 100;

export function Progress({
  value,
  label,
  className,
  tone = 'primary',
}: {
  value: number;
  label: string;
  className?: string;
  tone?: 'primary' | 'success' | 'danger';
}) {
  const clamped = Math.min(PERCENT_MAX, Math.max(0, value));
  return (
    <ProgressPrimitive.Root
      value={clamped}
      aria-label={label}
      className={cn('relative h-2 w-full overflow-hidden rounded-full bg-surface-muted', className)}
    >
      <ProgressPrimitive.Indicator
        className={cn(
          'h-full rounded-full transition-transform duration-base',
          tone === 'success'
            ? 'bg-success'
            : tone === 'danger'
              ? 'bg-danger'
              : 'bg-gradient-to-r from-primary to-accent',
        )}
        style={{ transform: `translateX(-${PERCENT_MAX - clamped}%)` }}
      />
    </ProgressPrimitive.Root>
  );
}
