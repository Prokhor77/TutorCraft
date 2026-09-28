import { cn } from '@/lib/utils/cn';

export function Kbd({ className, ...props }: React.HTMLAttributes<HTMLElement>) {
  return (
    <kbd
      className={cn(
        'inline-flex h-5 min-w-5 items-center justify-center rounded-xs border border-border bg-surface-muted px-1 font-mono text-[11px] font-medium text-text-muted',
        className,
      )}
      {...props}
    />
  );
}
