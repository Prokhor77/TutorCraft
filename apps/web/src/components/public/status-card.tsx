import type { LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';
import { cn } from '@/lib/utils/cn';

export type StatusTone = 'primary' | 'success' | 'warning' | 'danger';

const TONES: Record<StatusTone, { ring: string; chip: string; dot: string }> = {
  primary: { ring: 'bg-primary-soft', chip: 'text-primary', dot: 'bg-accent' },
  success: { ring: 'bg-success-soft', chip: 'text-success', dot: 'bg-success-accent' },
  warning: { ring: 'bg-warning-soft', chip: 'text-warning', dot: 'bg-warning-accent' },
  danger: { ring: 'bg-danger-soft', chip: 'text-danger', dot: 'bg-danger' },
};

/** Decorative «illustration» icon: soft tinted rings, a white chip with the icon and two floating dots. */
export function StatusIllustration({
  icon: Icon,
  tone = 'primary',
  className,
}: {
  icon: LucideIcon;
  tone?: StatusTone;
  className?: string;
}) {
  const palette = TONES[tone];
  return (
    <div aria-hidden className={cn('relative flex size-28 items-center justify-center', className)}>
      <span className={cn('absolute inset-0 rounded-full opacity-60', palette.ring)} />
      <span className={cn('absolute inset-3 rounded-full', palette.ring)} />
      <span
        className={cn(
          'relative flex size-14 items-center justify-center rounded-full bg-surface shadow-md',
          palette.chip,
        )}
      >
        <Icon className="size-7" strokeWidth={1.75} />
      </span>
      <span className={cn('absolute right-2 top-3 size-2.5 rounded-full', palette.dot)} />
      <span
        className={cn('absolute bottom-4 left-1 size-1.5 rounded-full opacity-70', palette.dot)}
      />
    </div>
  );
}

/** Soft violet/emerald canvas blobs behind centred public cards (decorative). */
export function PublicBlobs() {
  return (
    <>
      <div
        aria-hidden
        className="pointer-events-none absolute -left-24 -top-10 size-96 rounded-full bg-primary-soft opacity-70 blur-3xl"
      />
      <div
        aria-hidden
        className="pointer-events-none absolute -right-24 top-32 size-96 rounded-full bg-success-soft opacity-70 blur-3xl"
      />
    </>
  );
}

/** Centred Stitch card (2rem radius, violet-tinted L2 shadow) used by checkout, invites and system pages. */
export function CenteredCard({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div
      className={cn(
        'relative w-full max-w-md rounded-lg border border-card-border bg-surface p-6 shadow-md sm:p-10',
        className,
      )}
    >
      {children}
    </div>
  );
}

/** Centred status message: illustration, optional eyebrow, headline, text and pill actions. */
export function StatusMessage({
  icon,
  tone,
  eyebrow,
  title,
  description,
  actions,
  titleAs: Title = 'h1',
  role,
}: {
  icon: LucideIcon;
  tone?: StatusTone;
  eyebrow?: string;
  title: string;
  description?: ReactNode;
  actions?: ReactNode;
  titleAs?: 'h1' | 'h2';
  role?: 'alert' | 'status';
}) {
  return (
    <div role={role} className="flex flex-col items-center gap-5 text-center">
      <StatusIllustration icon={icon} tone={tone} />
      <div className="flex flex-col items-center gap-2">
        {eyebrow ? (
          <span className="rounded-full bg-accent/10 px-3 py-1 text-label-md uppercase text-primary">
            {eyebrow}
          </span>
        ) : null}
        <Title className="text-2xl">{title}</Title>
        {description ? <p className="text-sm text-text-muted">{description}</p> : null}
      </div>
      {actions ? (
        <div className="flex w-full flex-col justify-center gap-2 sm:flex-row [&>*]:w-full sm:[&>*]:w-auto">
          {actions}
        </div>
      ) : null}
    </div>
  );
}
