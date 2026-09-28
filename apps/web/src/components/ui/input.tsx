'use client';
import { Eye, EyeOff, type LucideIcon } from 'lucide-react';
import { forwardRef, useState, type InputHTMLAttributes, type TextareaHTMLAttributes } from 'react';
import { cn } from '@/lib/utils/cn';

/** Stitch fields: 1rem radius, 1.5px slate border; focus = violet border + 4px soft violet ring. */
export const fieldControlClass =
  'w-full rounded border-[1.5px] border-border bg-surface px-4 text-sm text-text transition-[border-color,box-shadow] duration-fast placeholder:text-placeholder hover:border-outline-variant focus-visible:border-accent focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/15 disabled:cursor-not-allowed disabled:opacity-60 aria-[invalid=true]:border-danger aria-[invalid=true]:focus-visible:ring-danger/15';

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(
  function Input({ className, ...props }, ref) {
    return <input ref={ref} className={cn(fieldControlClass, 'h-11', className)} {...props} />;
  },
);

/** Input with a leading icon (Stitch auth fields: «@», lock). Props/ref go to the input for Field wiring. */
export const IconInput = forwardRef<
  HTMLInputElement,
  InputHTMLAttributes<HTMLInputElement> & { icon: LucideIcon }
>(function IconInput({ icon: Icon, className, ...props }, ref) {
  return (
    <span className="relative block">
      <Icon
        className="pointer-events-none absolute left-4 top-1/2 size-5 -translate-y-1/2 text-text-muted"
        aria-hidden
      />
      <Input ref={ref} className={cn('h-12 pl-12', className)} {...props} />
    </span>
  );
});

/** Password input with a show/hide toggle (aria-pressed); labels come from the caller for i18n. */
export const PasswordInput = forwardRef<
  HTMLInputElement,
  Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> & {
    icon?: LucideIcon;
    showLabel: string;
    hideLabel: string;
  }
>(function PasswordInput({ icon: Icon, showLabel, hideLabel, className, ...props }, ref) {
  const [visible, setVisible] = useState(false);
  const Toggle = visible ? EyeOff : Eye;
  return (
    <span className="relative block">
      {Icon ? (
        <Icon
          className="pointer-events-none absolute left-4 top-1/2 size-5 -translate-y-1/2 text-text-muted"
          aria-hidden
        />
      ) : null}
      <Input
        ref={ref}
        type={visible ? 'text' : 'password'}
        className={cn('h-12 pr-12', Icon && 'pl-12', className)}
        {...props}
      />
      <button
        type="button"
        onClick={() => setVisible((value) => !value)}
        aria-label={visible ? hideLabel : showLabel}
        aria-pressed={visible}
        className="absolute right-2 top-1/2 flex size-9 -translate-y-1/2 items-center justify-center rounded-full text-text-muted hover:bg-accent/10 hover:text-primary focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20"
      >
        <Toggle className="size-5" aria-hidden />
      </button>
    </span>
  );
});

export const Textarea = forwardRef<
  HTMLTextAreaElement,
  TextareaHTMLAttributes<HTMLTextAreaElement>
>(function Textarea({ className, ...props }, ref) {
  return (
    <textarea ref={ref} className={cn(fieldControlClass, 'min-h-24 py-3', className)} {...props} />
  );
});

/** Native select: accessible everywhere incl. mobile pickers; used in forms. */
export const NativeSelect = forwardRef<
  HTMLSelectElement,
  InputHTMLAttributes<HTMLSelectElement> & { children: React.ReactNode }
>(function NativeSelect({ className, children, ...props }, ref) {
  return (
    <select
      ref={ref}
      className={cn(fieldControlClass, 'h-11 pr-9', className)}
      {...(props as React.SelectHTMLAttributes<HTMLSelectElement>)}
    >
      {children}
    </select>
  );
});

/** DateTimePicker (native): value/onChange in ISO via helpers in lib/utils/time. */
export const DateTimeInput = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(
  function DateTimeInput({ className, ...props }, ref) {
    return (
      <input
        ref={ref}
        type="datetime-local"
        className={cn(fieldControlClass, 'h-11', className)}
        {...props}
      />
    );
  },
);
