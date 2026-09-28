import { useTranslations } from 'next-intl';
import { Badge } from '@/components/ui/badge';
import type { Visibility } from '@/lib/api/schemas/common';

const TONES = { published: 'success', hidden: 'neutral', scheduled: 'warning' } as const;

/** Stitch status chip for course / module / item visibility: Опубликовано · Черновик · Запланировано. */
export function StatusChip({
  visibility,
  className,
}: {
  visibility: Visibility;
  className?: string;
}) {
  const t = useTranslations('visibilityStatus');
  return (
    <Badge tone={TONES[visibility]} dot className={className}>
      {t(visibility)}
    </Badge>
  );
}
