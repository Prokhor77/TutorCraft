import * as LabelPrimitive from '@radix-ui/react-label';
import { cloneElement, isValidElement, useId, type ReactElement, type ReactNode } from 'react';
import { cn } from '@/lib/utils/cn';

export function Label({ className, ...props }: React.ComponentProps<typeof LabelPrimitive.Root>) {
  return (
    <LabelPrimitive.Root className={cn('text-sm font-medium text-text', className)} {...props} />
  );
}

type FieldProps = {
  label: ReactNode;
  hint?: ReactNode;
  error?: string;
  required?: boolean;
  className?: string;
  /** `caps` = Stitch auth labels (uppercase label-md). */
  labelVariant?: 'default' | 'caps';
  /** Content on the label row's right (e.g. «Забыли пароль?»). */
  labelAside?: ReactNode;
  children: ReactElement<Record<string, unknown>>;
};

/** Label + control + hint + error with correct aria wiring (NFR-A11Y-01). */
export function Field({
  label,
  hint,
  error,
  required,
  className,
  labelVariant = 'default',
  labelAside,
  children,
}: FieldProps) {
  const generatedId = useId();
  const controlId =
    (isValidElement(children) && (children.props.id as string | undefined)) || generatedId;
  const hintId = hint ? `${controlId}-hint` : undefined;
  const errorId = error ? `${controlId}-error` : undefined;
  const describedBy = [hintId, errorId].filter(Boolean).join(' ') || undefined;
  return (
    <div className={cn('flex flex-col gap-1.5', className)}>
      <div className="flex items-baseline justify-between gap-2">
        <Label
          htmlFor={controlId}
          className={cn(labelVariant === 'caps' && 'text-label-md uppercase text-text-muted')}
        >
          {label}
          {required ? (
            <span aria-hidden className="ml-0.5 text-danger">
              *
            </span>
          ) : null}
        </Label>
        {labelAside}
      </div>
      {cloneElement(children, {
        id: controlId,
        'aria-describedby': describedBy,
        'aria-invalid': error ? true : undefined,
        'aria-required': required || undefined,
      })}
      {hint ? (
        <p id={hintId} className="text-xs text-text-muted">
          {hint}
        </p>
      ) : null}
      {error ? (
        <p id={errorId} role="alert" className="text-xs font-medium text-danger">
          {error}
        </p>
      ) : null}
    </div>
  );
}
