import { cn } from '@/lib/utils/cn';

export function Kbd({ className, ...props }: React.HTMLAttributes<HTMLElement>) {
  return (
    <kbd
      className={cn(
        'inline-flex h-5 min-w-5 items-center justify-center rounded-sm border border-border bg-surface-muted px-1.5 font-sans text-label-sm uppercase text-text-muted',
        className,
      )}
      {...props}
    />
  );
}
