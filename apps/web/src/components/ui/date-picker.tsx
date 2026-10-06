'use client';
import { CalendarDays, ChevronLeft, ChevronRight } from 'lucide-react';
import { useLocale } from 'next-intl';
import { forwardRef, useState, type ButtonHTMLAttributes } from 'react';
import { DayPicker, type ChevronProps } from 'react-day-picker';
import { enUS, ru, uz } from 'react-day-picker/locale';
import { DEFAULT_LOCALE, isAppLocale } from '@/i18n/config';
import { cn } from '@/lib/utils/cn';
import { Button } from './button';
import { fieldControlClass } from './input';
import { Popover, PopoverContent, PopoverTrigger } from './popover';

const MONDAY = 1;
const DAY_PICKER_LOCALES = { ru, en: enUS, uz } as const;
const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;

/** 'YYYY-MM-DD' → local Date (no timezone shift, unlike `new Date('YYYY-MM-DD')`). */
export function parseDateValue(value: string): Date | undefined {
  const match = ISO_DATE.exec(value);
  if (!match) return undefined;
  const [, year, month, day] = match.map(Number);
  return new Date(year ?? 0, (month ?? 1) - 1, day ?? 1);
}

/** Local Date → 'YYYY-MM-DD'. */
export function formatDateValue(date: Date): string {
  const pad = (part: number) => String(part).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function NavChevron({ orientation, className }: ChevronProps) {
  const Icon = orientation === 'left' ? ChevronLeft : ChevronRight;
  return <Icon className={cn('size-4', className)} aria-hidden />;
}

const NAV_BUTTON =
  'inline-flex size-8 items-center justify-center rounded-full text-text-muted transition-colors duration-fast hover:bg-accent/10 hover:text-primary focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20 disabled:pointer-events-none disabled:opacity-40';

/** Stitch look for react-day-picker: rounded day pills, violet selection, soft today ring. */
const CALENDAR_CLASSES = {
  root: 'relative select-none',
  months: 'flex flex-col',
  month: 'flex flex-col gap-3',
  month_caption: 'flex h-8 items-center px-1',
  caption_label: 'block font-heading text-base font-semibold first-letter:uppercase',
  nav: 'absolute right-0 top-0 flex items-center gap-1',
  button_previous: NAV_BUTTON,
  button_next: NAV_BUTTON,
  month_grid: 'w-full border-collapse',
  weekdays: 'flex',
  weekday: 'w-10 pb-1 text-center text-label-sm font-medium uppercase text-text-muted',
  week: 'mt-0.5 flex w-full',
  day: 'group/day size-10 p-0 text-center text-sm',
  day_button:
    'inline-flex size-10 items-center justify-center rounded-full font-medium transition-colors duration-fast hover:bg-primary-soft hover:text-primary focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/25',
  selected:
    '[&>button]:bg-primary [&>button]:text-primary-foreground [&>button]:shadow-sm [&>button]:hover:bg-primary/90 [&>button]:hover:text-primary-foreground',
  today:
    '[&>button]:ring-2 [&>button]:ring-inset [&>button]:ring-primary/40 [&>button]:font-semibold',
  outside: 'text-outline [&>button]:font-normal',
  disabled: 'opacity-40',
  hidden: 'invisible',
};

type DatePickerProps = Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'value' | 'onChange'> & {
  /** 'YYYY-MM-DD' or '' */
  value: string;
  onChange: (value: string) => void;
  placeholder: string;
  todayLabel: string;
};

/** Date field: button showing the formatted date + popover month calendar (react-day-picker). */
export const DatePicker = forwardRef<HTMLButtonElement, DatePickerProps>(function DatePicker(
  { value, onChange, placeholder, todayLabel, className, ...props },
  ref,
) {
  const locale = useLocale();
  const [open, setOpen] = useState(false);
  const selected = parseDateValue(value);
  const [month, setMonth] = useState<Date>(selected ?? new Date());
  const label = selected
    ? new Intl.DateTimeFormat(locale, {
        weekday: 'short',
        day: 'numeric',
        month: 'long',
        year: 'numeric',
      }).format(selected)
    : placeholder;

  const pick = (date: Date | undefined) => {
    if (!date) return;
    onChange(formatDateValue(date));
    setOpen(false);
  };
  const onOpenChange = (next: boolean) => {
    if (next) setMonth(selected ?? new Date());
    setOpen(next);
  };

  return (
    <Popover open={open} onOpenChange={onOpenChange}>
      <PopoverTrigger asChild>
        <button
          ref={ref}
          type="button"
          className={cn(
            fieldControlClass,
            'flex h-11 items-center gap-3 text-left',
            !selected && 'text-placeholder',
            className,
          )}
          {...props}
        >
          <CalendarDays className="size-4 shrink-0 text-text-muted" aria-hidden />
          <span className="truncate first-letter:uppercase">{label}</span>
        </button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-auto p-3">
        <DayPicker
          mode="single"
          selected={selected}
          onSelect={pick}
          month={month}
          onMonthChange={setMonth}
          weekStartsOn={MONDAY}
          locale={DAY_PICKER_LOCALES[isAppLocale(locale) ? locale : DEFAULT_LOCALE]}
          showOutsideDays
          autoFocus
          classNames={CALENDAR_CLASSES}
          components={{ Chevron: NavChevron }}
        />
        <div className="mt-2 flex justify-end border-t border-card-border pt-2">
          <Button type="button" variant="ghost" size="sm" onClick={() => pick(new Date())}>
            {todayLabel}
          </Button>
        </div>
      </PopoverContent>
    </Popover>
  );
});
