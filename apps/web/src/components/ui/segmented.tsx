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
      className={cn('inline-flex rounded-md bg-surface-muted p-1', className)}
    >
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          role="radio"
          aria-checked={value === option.value}
          onClick={() => onChange(option.value)}
          className={cn(
            'h-8 rounded-sm px-3 text-sm font-medium text-text-muted transition-colors duration-fast focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring',
            value === option.value && 'bg-surface text-text shadow-sm',
          )}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
