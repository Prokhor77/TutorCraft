'use client';
import { ArchiveRestore, Trash2 } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { ItemTypeIcon } from '@/components/course/item-meta';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { SkeletonList } from '@/components/ui/skeleton';
import { Section } from '@/components/ui/page-header';
import { useCourseContext } from '@/features/courses/course-context';
import { useOutlineMutations, useTrash } from '@/features/courses/use-outline';
import { formatRelative } from '@/lib/utils/format';

/** FR-COURSE-07: trash with restore (30 days). */
export default function TrashPage() {
  const t = useTranslations('trash');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const { course } = useCourseContext();
  const trash = useTrash(course.id);
  const { restore } = useOutlineMutations(course.id);

  return (
    <Section title={t('title')}>
      <p className="text-sm text-text-muted">{t('retention')}</p>
      {trash.isLoading ? <SkeletonList label={tCommon('loading')} /> : null}
      {trash.isError ? (
        <ErrorState
          title={t('loadError')}
          retryLabel={tCommon('retry')}
          onRetry={() => void trash.refetch()}
        />
      ) : null}
      {trash.data?.length === 0 ? (
        <EmptyState icon={Trash2} title={t('emptyTitle')} description={t('emptyText')} />
      ) : null}
      <ul className="flex flex-col gap-2">
        {trash.data?.map((entry) => (
          <li
            key={entry.id}
            className="flex items-center gap-3 rounded-md border border-border bg-surface px-3 py-2.5"
          >
            {entry.itemType ? <ItemTypeIcon type={entry.itemType} /> : null}
            <span className="flex min-w-0 flex-1 flex-col">
              <span className="truncate text-sm font-medium">{entry.title}</span>
              <span className="text-xs text-text-muted">
                {t(`kinds.${entry.kind}`)} ·{' '}
                {t('deletedAt', { when: formatRelative(entry.deletedAt, locale) })}
              </span>
            </span>
            <Button
              size="sm"
              variant="secondary"
              loading={restore.isPending && restore.variables?.id === entry.id}
              onClick={() => restore.mutate({ id: entry.id, kind: entry.kind })}
            >
              <ArchiveRestore aria-hidden /> {t('restore')}
            </Button>
          </li>
        ))}
      </ul>
    </Section>
  );
}
