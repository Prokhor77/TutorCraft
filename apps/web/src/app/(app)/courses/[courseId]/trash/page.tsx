'use client';
import { ArchiveRestore, ArrowLeft, Layers, Trash2 } from 'lucide-react';
import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { ItemTypeIcon } from '@/components/course/item-meta';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { SkeletonList } from '@/components/ui/skeleton';
import { Breadcrumbs, PageHeader, Panel } from '@/components/ui/page-header';
import { ROUTES } from '@/features/auth/routes';
import { useCourseContext } from '@/features/courses/course-context';
import { useOutlineMutations, useTrash } from '@/features/courses/use-outline';
import { formatRelative } from '@/lib/utils/format';

/** FR-COURSE-07: trash with restore (30 days) — Stitch header card + a panel of pill rows. */
export default function TrashPage() {
  const t = useTranslations('trash');
  const tShell = useTranslations('shell');
  const tWorkspace = useTranslations('workspace');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const { course } = useCourseContext();
  const trash = useTrash(course.id);
  const { restore } = useOutlineMutations(course.id);
  const count = trash.data?.length ?? 0;

  return (
    <section className="flex flex-col gap-4">
      <PageHeader
        className="mb-0"
        breadcrumbs={
          <Breadcrumbs
            label={tShell('breadcrumbs')}
            items={[
              { label: tShell('myCourses'), href: ROUTES.courses },
              { label: course.title, href: ROUTES.course(course.id) },
              { label: t('title') },
            ]}
          />
        }
        title={t('title')}
        meta={trash.data ? <Badge tone="neutral">{t('count', { count })}</Badge> : null}
        description={t('retention')}
        actions={
          <Button asChild variant="secondary" size="sm">
            <Link href={ROUTES.course(course.id)}>
              <ArrowLeft aria-hidden /> {tWorkspace('backToStructure')}
            </Link>
          </Button>
        }
      />
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
      {count > 0 ? (
        <Panel>
          <ul className="flex flex-col gap-2">
            {trash.data?.map((entry) => (
              <li
                key={entry.id}
                className="flex flex-wrap items-center gap-3 rounded-md bg-surface-muted/60 px-4 py-3 sm:flex-nowrap sm:rounded-full sm:py-2"
              >
                {entry.itemType ? (
                  <ItemTypeIcon type={entry.itemType} className="size-9 rounded-full" />
                ) : (
                  <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-primary-soft text-primary">
                    <Layers className="size-4" aria-hidden />
                  </span>
                )}
                <span className="flex w-[calc(100%-3rem)] min-w-0 flex-col sm:w-auto sm:flex-1">
                  <span className="truncate text-sm font-semibold">{entry.title}</span>
                  <span className="text-xs text-text-muted">
                    {t(`kinds.${entry.kind}`)} ·{' '}
                    {t('deletedAt', { when: formatRelative(entry.deletedAt, locale) })}
                  </span>
                </span>
                <Button
                  size="sm"
                  variant="secondary"
                  className="ml-12 sm:ml-0"
                  loading={restore.isPending && restore.variables?.id === entry.id}
                  onClick={() => restore.mutate({ id: entry.id, kind: entry.kind })}
                >
                  <ArchiveRestore aria-hidden /> {t('restore')}
                </Button>
              </li>
            ))}
          </ul>
        </Panel>
      ) : null}
    </section>
  );
}
