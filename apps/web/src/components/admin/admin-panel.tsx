'use client';
import type { ReactNode } from 'react';
import { Avatar } from '@/components/ui/avatar';
import { cn } from '@/lib/utils/cn';

/**
 * Stitch table panel for the admin area: a white 2rem card with a headline-sm title, optional count chip, description,
 * pill actions, a filter toolbar row and an edge-to-edge table body (the table's own cells carry the 24 px inset).
 */
export function AdminTablePanel({
  title,
  count,
  description,
  actions,
  toolbar,
  children,
  footer,
  className,
}: {
  title: ReactNode;
  count?: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  toolbar?: ReactNode;
  children?: ReactNode;
  footer?: ReactNode;
  className?: string;
}) {
  return (
    <section
      className={cn(
        'flex min-w-0 flex-col overflow-hidden rounded-lg border border-card-border bg-surface shadow-sm',
        className,
      )}
    >
      <div className="flex flex-col gap-4 p-5 sm:p-6">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="flex min-w-0 flex-col gap-0.5">
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="text-lg">{title}</h2>
              {count}
            </div>
            {description ? <p className="text-xs text-text-muted">{description}</p> : null}
          </div>
          {actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
        </div>
        {toolbar}
      </div>
      {children ? (
        <div className="min-w-0 overflow-x-auto border-t border-border empty:hidden">
          {children}
        </div>
      ) : null}
      {footer ? <div className="flex justify-center p-4 empty:hidden">{footer}</div> : null}
    </section>
  );
}

/** Stitch filter chips («Все · Ждут · Проверено»): a radiogroup of pills, the active one filled primary. */
export function FilterChips<T extends string>({
  value,
  onChange,
  options,
  label,
  className,
}: {
  value: T;
  onChange: (value: T) => void;
  options: { value: T; label: string }[];
  label: string;
  className?: string;
}) {
  return (
    <div
      role="radiogroup"
      aria-label={label}
      className={cn('scrollbar-none flex max-w-full gap-1.5 overflow-x-auto', className)}
    >
      {options.map((option) => {
        const active = option.value === value;
        return (
          <button
            key={option.value}
            type="button"
            role="radio"
            aria-checked={active}
            onClick={() => onChange(option.value)}
            className={cn(
              'h-8 shrink-0 whitespace-nowrap rounded-full px-3.5 text-label-md transition-colors duration-fast focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20',
              active
                ? 'bg-primary text-primary-foreground shadow-sm'
                : 'bg-surface-muted text-text-muted hover:bg-surface-container hover:text-primary',
            )}
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}

/** Avatar + name + secondary line cell used by the users / orders / audit tables. */
export function PersonCell({
  name,
  secondary,
  extra,
}: {
  name: string;
  secondary?: ReactNode;
  extra?: ReactNode;
}) {
  return (
    <span className="flex min-w-0 items-center gap-3">
      <Avatar name={name} size="md" />
      <span className="flex min-w-0 flex-col">
        <span className="truncate font-semibold text-text">{name}</span>
        {secondary ? <span className="truncate text-xs text-text-muted">{secondary}</span> : null}
        {extra}
      </span>
    </span>
  );
}
