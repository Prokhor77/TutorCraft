'use client';
import * as CheckboxPrimitive from '@radix-ui/react-checkbox';
import * as SwitchPrimitive from '@radix-ui/react-switch';
import { Check, Minus } from 'lucide-react';
import { forwardRef } from 'react';
import { cn } from '@/lib/utils/cn';

export const Checkbox = forwardRef<
  HTMLButtonElement,
  React.ComponentProps<typeof CheckboxPrimitive.Root>
>(function Checkbox({ className, ...props }, ref) {
  return (
    <CheckboxPrimitive.Root
      ref={ref}
      className={cn(
        'peer size-5 shrink-0 rounded-sm border-[1.5px] border-outline-variant bg-surface transition-colors duration-fast hover:border-accent focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20 disabled:opacity-50 data-[state=checked]:border-primary data-[state=indeterminate]:border-primary data-[state=checked]:bg-primary data-[state=indeterminate]:bg-primary data-[state=checked]:text-primary-foreground data-[state=indeterminate]:text-primary-foreground',
        className,
      )}
      {...props}
    >
      <CheckboxPrimitive.Indicator className="flex animate-pop-in items-center justify-center">
        {props.checked === 'indeterminate' ? (
          <Minus className="size-3.5" />
        ) : (
          <Check className="size-3.5" />
        )}
      </CheckboxPrimitive.Indicator>
    </CheckboxPrimitive.Root>
  );
});

export const Switch = forwardRef<
  HTMLButtonElement,
  React.ComponentProps<typeof SwitchPrimitive.Root>
>(function Switch({ className, ...props }, ref) {
  return (
    <SwitchPrimitive.Root
      ref={ref}
      className={cn(
        'inline-flex h-6 w-11 shrink-0 cursor-pointer items-center rounded-full border-2 border-transparent bg-outline-variant transition-colors duration-fast focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20 disabled:opacity-50 data-[state=checked]:bg-primary',
        className,
      )}
      {...props}
    >
      <SwitchPrimitive.Thumb className="pointer-events-none block size-5 rounded-full bg-surface shadow-md transition-transform duration-base ease-bounce data-[state=checked]:translate-x-5" />
    </SwitchPrimitive.Root>
  );
});

/** Stitch radio: ring with a soft bounce and a violet dot (styles in globals `.tc-radio`). */
export const Radio = forwardRef<
  HTMLInputElement,
  Omit<React.InputHTMLAttributes<HTMLInputElement>, 'type'>
>(function Radio({ className, ...props }, ref) {
  return <input ref={ref} type="radio" className={cn('tc-radio', className)} {...props} />;
});
