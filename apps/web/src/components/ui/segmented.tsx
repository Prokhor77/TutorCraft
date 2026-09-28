'use client';
import { cn } from '@/lib/utils/cn';

type Option<T extends string> = { value: T; label: string };

/** Segmented control (radio group semantics). */
export function Segmented<T extends string>({
  value,
  onChange,
  options,
  label,
  className,
}: {
  value: T;
  onChange: (value: T) => void;
  options: Option<T>[];
  label: string;
  className?: string;
}) {
  return (
    <div
      role="radiogroup"
      aria-label={label}
      className={cn(
        'inline-flex max-w-full overflow-x-auto scrollbar-none rounded-full bg-surface-muted p-1 shadow-inner',
        className,
      )}
    >
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          role="radio"
          aria-checked={value === option.value}
          onClick={() => onChange(option.value)}
          className={cn(
            'h-8 shrink-0 grow basis-auto whitespace-nowrap rounded-full px-3 text-label-md text-text-muted transition-colors duration-fast hover:text-primary focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20 sm:px-4 sm:text-label-lg',
            value === option.value && 'bg-surface text-primary shadow-sm',
          )}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
