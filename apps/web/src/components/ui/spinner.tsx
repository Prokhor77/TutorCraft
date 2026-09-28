import { Loader2 } from 'lucide-react';
import { cn } from '@/lib/utils/cn';

export function Spinner({ label, className }: { label: string; className?: string }) {
  return (
    <span
      role="status"
      className={cn('inline-flex items-center gap-2 text-sm text-text-muted', className)}
    >
      <Loader2 className="size-4 animate-spin" aria-hidden />
      <span className="sr-only">{label}</span>
    </span>
  );
}
