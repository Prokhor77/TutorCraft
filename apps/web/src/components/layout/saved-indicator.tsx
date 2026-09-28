'use client';
import { useIsMutating } from '@tanstack/react-query';
import { CloudCheck, Loader2 } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { useEffect, useState } from 'react';
import { formatRelative } from '@/lib/utils/format';
import { cn } from '@/lib/utils/cn';
import { useSaveStore } from '@/stores/save-store';

const REFRESH_MS = 30_000;

/** Header «Сохранено N мин назад» (Stitch): reflects real server writes of this tab, hidden until the first one. */
export function SavedIndicator({ className }: { className?: string }) {
  const t = useTranslations('shell');
  const locale = useLocale();
  const saving = useIsMutating() > 0;
  const lastSavedAt = useSaveStore((state) => state.lastSavedAt);
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), REFRESH_MS);
    return () => clearInterval(timer);
  }, []);
  if (!saving && !lastSavedAt) return null;
  return (
    <span
      role="status"
      aria-live="polite"
      className={cn(
        'inline-flex items-center gap-1.5 whitespace-nowrap text-xs text-text-muted',
        className,
      )}
    >
      {saving ? (
        <>
          <Loader2 className="size-4 animate-spin" aria-hidden /> {t('saving')}
        </>
      ) : lastSavedAt ? (
        <>
          <CloudCheck className="size-4 text-success" aria-hidden />
          {t('savedAgo', {
            ago: formatRelative(
              new Date(Math.min(lastSavedAt, now.getTime())).toISOString(),
              locale,
              now,
            ),
          })}
        </>
      ) : null}
    </span>
  );
}
