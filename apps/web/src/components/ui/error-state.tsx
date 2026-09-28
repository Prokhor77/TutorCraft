import { AlertTriangle } from 'lucide-react';
import { Button } from './button';

export function ErrorState({
  title,
  description,
  retryLabel,
  onRetry,
}: {
  title: string;
  description?: string;
  retryLabel?: string;
  onRetry?: () => void;
}) {
  return (
    <div
      role="alert"
      className="flex flex-col items-center gap-3 rounded-md border border-danger/30 bg-danger-soft/40 px-6 py-8 text-center"
    >
      <AlertTriangle className="size-6 text-danger" aria-hidden />
      <div className="flex flex-col gap-1">
        <h3 className="text-base font-semibold">{title}</h3>
        {description ? <p className="text-sm text-text-muted">{description}</p> : null}
      </div>
      {onRetry && retryLabel ? (
        <Button variant="secondary" size="sm" onClick={onRetry}>
          {retryLabel}
        </Button>
      ) : null}
    </div>
  );
}
