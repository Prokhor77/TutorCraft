import { AlertTriangle, CheckCircle2, Info, XCircle } from 'lucide-react';
import type { ReactNode } from 'react';
import { cn } from '@/lib/utils/cn';

const tones = {
  info: { icon: Info, className: 'border-info/30 bg-info-soft text-text [&>svg]:text-info' },
  warning: {
    icon: AlertTriangle,
    className: 'border-warning/30 bg-warning-soft text-text [&>svg]:text-warning',
  },
  success: {
    icon: CheckCircle2,
    className: 'border-success/30 bg-success-soft text-text [&>svg]:text-success',
  },
  danger: {
    icon: XCircle,
    className: 'border-danger/30 bg-danger-soft text-text [&>svg]:text-danger',
  },
} as const;

export type AlertTone = keyof typeof tones;

export function Alert({
  tone = 'info',
  title,
  children,
  className,
}: {
  tone?: AlertTone;
  title?: ReactNode;
  children?: ReactNode;
  className?: string;
}) {
  const { icon: Icon, className: toneClassName } = tones[tone];
  return (
    <div
      role={tone === 'danger' || tone === 'warning' ? 'alert' : 'status'}
      className={cn('flex gap-3 rounded-md border p-3 text-sm', toneClassName, className)}
    >
      <Icon className="mt-0.5 size-4 shrink-0" aria-hidden />
      <div className="flex min-w-0 flex-col gap-0.5">
        {title ? <p className="font-semibold">{title}</p> : null}
        {children ? <div className="text-text-muted">{children}</div> : null}
      </div>
    </div>
  );
}
