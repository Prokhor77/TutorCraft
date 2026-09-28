'use client';
import { ChevronDown } from 'lucide-react';
import { MathText } from '@/components/math/math-text';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { fieldControlClass, NativeSelect } from '@/components/ui/input';
import { hasMath } from '@/lib/math/inline-math';
import { cn } from '@/lib/utils/cn';

type Props = {
  choices: readonly string[];
  value: string;
  onChange: (value: string) => void;
  placeholder: string;
  ariaLabel: string;
  disabled?: boolean;
  className?: string;
};

/**
 * Matching answer picker. Native <select> (best on mobile) unless a choice contains a formula,
 * which <option> cannot render — then a menu with rendered formulas is used.
 */
export function MatchChoiceSelect({
  choices,
  value,
  onChange,
  placeholder,
  ariaLabel,
  disabled,
  className,
}: Props) {
  if (!choices.some(hasMath)) {
    return (
      <NativeSelect
        aria-label={ariaLabel}
        className={className}
        disabled={disabled}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        <option value="">{placeholder}</option>
        {choices.map((choice) => (
          <option key={choice} value={choice}>
            {choice}
          </option>
        ))}
      </NativeSelect>
    );
  }
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label={ariaLabel}
        disabled={disabled}
        className={cn(
          fieldControlClass,
          'flex items-center justify-between gap-2 text-left',
          className,
        )}
      >
        <span className={cn('min-w-0 truncate', !value && 'text-placeholder')}>
          {value ? <MathText value={value} /> : placeholder}
        </span>
        <ChevronDown className="size-4 shrink-0 text-text-muted" aria-hidden />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start">
        {choices.map((choice) => (
          <DropdownMenuItem key={choice} onSelect={() => onChange(choice)}>
            <MathText value={choice} />
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
