import { forwardRef, type InputHTMLAttributes, type TextareaHTMLAttributes } from 'react';
import { cn } from '@/lib/utils/cn';

export const fieldControlClass =
  'w-full rounded-md border border-border bg-surface px-3 text-sm text-text shadow-sm transition-colors duration-fast placeholder:text-text-muted/80 focus-visible:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring/40 disabled:cursor-not-allowed disabled:opacity-60 aria-[invalid=true]:border-danger';

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(
  function Input({ className, ...props }, ref) {
    return <input ref={ref} className={cn(fieldControlClass, 'h-10', className)} {...props} />;
  },
);

export const Textarea = forwardRef<
  HTMLTextAreaElement,
  TextareaHTMLAttributes<HTMLTextAreaElement>
>(function Textarea({ className, ...props }, ref) {
  return (
    <textarea
      ref={ref}
      className={cn(fieldControlClass, 'min-h-24 py-2 leading-relaxed', className)}
      {...props}
    />
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
      className={cn(fieldControlClass, 'h-10 pr-8', className)}
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
        className={cn(fieldControlClass, 'h-10', className)}
        {...props}
      />
    );
  },
);
