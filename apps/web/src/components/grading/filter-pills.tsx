'use client';
import { cn } from '@/lib/utils/cn';

export type FilterPillOption<T extends string> = { value: T; label: string; count?: number };

/**
 * Stitch pill filter (toggle-button group, `aria-pressed`). `track` renders the header-card tab row (filled active
 * pill with a count bubble on a tinted track); `chips` renders the compact queue-panel filter chips.
 */
export function FilterPills<T extends string>({
  value,
  onChange,
  options,
  label,
  variant = 'track',
  className,
}: {
  value: T;
  onChange: (value: T) => void;
  options: FilterPillOption<T>[];
  label: string;
  variant?: 'track' | 'chips';
  className?: string;
}) {
  const track = variant === 'track';
  return (
    <div
      role="group"
      aria-label={label}
      className={cn(
        'scrollbar-none flex max-w-full items-center overflow-x-auto',
        track ? 'gap-1 rounded-full bg-surface-muted p-1 shadow-inner' : 'flex-wrap gap-1.5',
        className,
      )}
    >
      {options.map((option) => {
        const active = option.value === value;
        return (
          <button
            key={option.value}
            type="button"
            aria-pressed={active}
            onClick={() => onChange(option.value)}
            className={cn(
              'inline-flex shrink-0 items-center gap-1.5 whitespace-nowrap rounded-full transition-colors duration-fast focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20',
              track ? 'h-8 px-3 text-label-lg sm:px-4' : 'h-7 px-3 text-label-md',
              active
                ? 'bg-primary text-primary-foreground shadow-sm'
                : track
                  ? 'text-text-muted hover:bg-surface-container hover:text-text'
                  : 'bg-surface-muted text-text-muted hover:text-primary',
            )}
          >
            {option.label}
            {option.count !== undefined ? (
              <span
                className={cn(
                  'min-w-5 rounded-full px-1.5 text-center text-label-sm leading-5',
                  active
                    ? 'bg-primary-foreground text-primary'
                    : 'bg-surface-container text-text-muted',
                )}
              >
                {option.count}
              </span>
            ) : null}
          </button>
        );
      })}
    </div>
  );
}
