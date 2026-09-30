'use client';
import { Clock, X } from 'lucide-react';
import { forwardRef, useCallback, useMemo, useState, type ButtonHTMLAttributes } from 'react';
import { cn } from '@/lib/utils/cn';
import { MINUTES_PER_HOUR } from '@/lib/utils/time';
import { fieldControlClass } from './input';
import { Popover, PopoverContent, PopoverTrigger } from './popover';

const DEFAULT_STEP_MINUTES = 15;
const MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR;

const pad = (part: number) => String(part).padStart(2, '0');

/** All 'HH:mm' slots of a day with the given step; an off-grid current value is kept in order. */
export function timeSlots(stepMinutes: number, current = ''): string[] {
  const slots: string[] = [];
  for (let minute = 0; minute < MINUTES_PER_DAY; minute += stepMinutes) {
    slots.push(`${pad(Math.floor(minute / MINUTES_PER_HOUR))}:${pad(minute % MINUTES_PER_HOUR)}`);
  }
  if (current && !slots.includes(current)) slots.push(current);
  return slots.sort();
}

type TimePickerProps = Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'value' | 'onChange'> & {
  /** 'HH:mm' or '' */
  value: string;
  onChange: (value: string) => void;
  placeholder: string;
  /** Shown as the first option when the time is optional (e.g. «Без окончания»). */
  clearLabel?: string;
  stepMinutes?: number;
};

/** Time field: button + popover grid of time chips, scrolled to the current value. */
export const TimePicker = forwardRef<HTMLButtonElement, TimePickerProps>(function TimePicker(
  {
    value,
    onChange,
    placeholder,
    clearLabel,
    stepMinutes = DEFAULT_STEP_MINUTES,
    className,
    ...props
  },
  ref,
) {
  const [open, setOpen] = useState(false);
  const slots = useMemo(() => timeSlots(stepMinutes, value), [stepMinutes, value]);
  /** Centres the selected chip inside the list without scrolling the dialog behind it. */
  const scrollToSelected = useCallback((node: HTMLButtonElement | null) => {
    const list = node?.parentElement;
    if (!node || !list) return;
    list.scrollTop = node.offsetTop - list.clientHeight / 2 + node.clientHeight / 2;
  }, []);

  const pick = (next: string) => {
    onChange(next);
    setOpen(false);
  };

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <button
          ref={ref}
          type="button"
          className={cn(
            fieldControlClass,
            'flex h-11 items-center gap-3 text-left tabular-nums',
            !value && 'text-placeholder',
            className,
          )}
          {...props}
        >
          <Clock className="size-4 shrink-0 text-text-muted" aria-hidden />
          <span className="truncate">{value || placeholder}</span>
        </button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-64 p-2">
        {clearLabel ? (
          <button
            type="button"
            onClick={() => pick('')}
            className="mb-1 flex w-full items-center gap-2 rounded px-3 py-2 text-sm text-text-muted hover:bg-accent/10 hover:text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring"
          >
            <X className="size-4" aria-hidden /> {clearLabel}
          </button>
        ) : null}
        <div
          role="listbox"
          aria-label={placeholder}
          className="relative grid max-h-64 grid-cols-4 gap-1 overflow-y-auto p-0.5"
        >
          {slots.map((slot) => {
            const selected = slot === value;
            return (
              <button
                key={slot}
                ref={selected ? scrollToSelected : undefined}
                type="button"
                role="option"
                aria-selected={selected}
                onClick={() => pick(slot)}
                className={cn(
                  'rounded-full py-1.5 text-xs font-medium tabular-nums transition-colors duration-fast hover:bg-primary-soft hover:text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring',
                  slot.endsWith(':00') ? 'text-text' : 'text-text-muted',
                  selected &&
                    'bg-primary text-primary-foreground shadow-sm hover:bg-primary/90 hover:text-primary-foreground',
                )}
              >
                {slot}
              </button>
            );
          })}
        </div>
      </PopoverContent>
    </Popover>
  );
});
