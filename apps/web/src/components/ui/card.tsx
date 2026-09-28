import { cn } from '@/lib/utils/cn';

type CardProps = React.HTMLAttributes<HTMLDivElement> & { interactive?: boolean };

/** Stitch card: white, radius lg (2rem), L1 shadow + faint violet hairline; `interactive` lifts to L2 on hover/focus. */
export function Card({ className, interactive, ...props }: CardProps) {
  return (
    <div
      className={cn(
        'rounded-lg border border-card-border bg-surface shadow-sm',
        interactive &&
          'transition-[box-shadow,border-color] duration-fast focus-within:border-card-border-hover focus-within:shadow-md hover:border-card-border-hover hover:shadow-md',
        className,
      )}
      {...props}
    />
  );
}

export function CardHeader({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn('flex flex-wrap items-start justify-between gap-3 p-6 pb-3', className)}
      {...props}
    />
  );
}

export function CardTitle({
  className,
  as: Tag = 'h2',
  ...props
}: React.HTMLAttributes<HTMLHeadingElement> & { as?: 'h2' | 'h3' }) {
  return <Tag className={cn('text-lg', className)} {...props} />;
}

export function CardContent({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('p-6 pt-0', className)} {...props} />;
}
