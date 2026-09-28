'use client';
import { Download, Megaphone, Plus, Search, Table2 } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { useState } from 'react';
import { GradeCellView } from '@/components/gradebook/grade-cell';
import { GradebookSetupSheet } from '@/components/gradebook/setup-sheet';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogFooter, DialogTrigger } from '@/components/ui/dialog';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { Field } from '@/components/ui/field';
import { Input, NativeSelect } from '@/components/ui/input';
import { SkeletonList } from '@/components/ui/skeleton';
import { useCourseContext } from '@/features/courses/course-context';
import { useGroups } from '@/features/enrollment/use-enrollment';
import {
  useGradebook,
  useGradebookMutations,
  useGradebookSetup,
  useGradeCellMutation,
} from '@/features/gradebook/use-gradebook';
import { PERMISSIONS } from '@/lib/access/permissions';
import { formatPercent } from '@/lib/utils/format';

function ManualItemDialog({ courseId }: { courseId: string }) {
  const t = useTranslations('gradebook');
  const tCommon = useTranslations('common');
  const { addManualItem } = useGradebookMutations(courseId);
  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  const [maxScore, setMaxScore] = useState('100');
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="secondary" size="sm">
          <Plus aria-hidden /> {t('addColumn')}
        </Button>
      </DialogTrigger>
      <DialogContent title={t('addColumn')} closeLabel={tCommon('close')}>
        <form
          className="flex flex-col gap-4"
          onSubmit={(event) => {
            event.preventDefault();
            if (!name.trim()) return;
            addManualItem.mutate(
              { name: name.trim(), maxScore: Number(maxScore) },
              { onSuccess: () => setOpen(false) },
            );
          }}
        >
          <Field label={t('columnName')} required>
            <Input value={name} onChange={(event) => setName(event.target.value)} autoFocus />
          </Field>
          <Field label={t('maxScore')}>
            <Input
              type="number"
              min={0}
              value={maxScore}
              onChange={(event) => setMaxScore(event.target.value)}
            />
          </Field>
          <DialogFooter>
            <Button type="submit" loading={addManualItem.isPending}>
              {tCommon('create')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

/** Gradebook (FR-GRADE-01..07): sticky header/first column, inline editing, formula preview, export, publish. */
export default function GradebookPage() {
  const t = useTranslations('gradebook');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const { course, can } = useCourseContext();
  const [groupId, setGroupId] = useState('');
  const [query, setQuery] = useState('');
  const book = useGradebook(course.id, groupId || undefined);
  const setup = useGradebookSetup(course.id);
  const groups = useGroups(course.id);
  const cellMutation = useGradeCellMutation(course.id, groupId || undefined);
  const { exportFile, publishItem } = useGradebookMutations(course.id);
  const editable = can(PERMISSIONS.gradeEdit);
  const sourceByGradeItem = new Map(
    (setup.data?.items ?? []).map((item) => [item.gradeItemId, item.sourceItemId]),
  );

  if (book.isLoading) return <SkeletonList label={tCommon('loading')} />;
  if (book.isError || !book.data)
    return (
      <ErrorState
        title={t('loadError')}
        retryLabel={tCommon('retry')}
        onRetry={() => void book.refetch()}
      />
    );
  const rows = book.data.rows.filter((row) =>
    row.userName.toLowerCase().includes(query.trim().toLowerCase()),
  );
  const columns = book.data.columns;

  return (
    <div className="flex flex-col gap-4">
      {setup.data ? (
        <div className="flex flex-col gap-2">
          <p className="text-sm">
            <span className="text-text-muted">{t('formula')}: </span>
            <span className="font-mono">{setup.data.formula}</span>
          </p>
          {setup.data.warnings.map((warning) => (
            <Alert key={warning.code + warning.message} tone="warning" title={warning.message} />
          ))}
        </div>
      ) : null}
      <div className="flex flex-col gap-2 md:flex-row md:items-center">
        <div className="relative md:w-64">
          <Search
            className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-text-muted"
            aria-hidden
          />
          <Input
            type="search"
            aria-label={t('search')}
            placeholder={t('search')}
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            className="pl-9"
          />
        </div>
        <NativeSelect
          aria-label={t('group')}
          value={groupId}
          onChange={(event) => setGroupId(event.target.value)}
          className="md:w-52"
        >
          <option value="">{t('allGroups')}</option>
          {groups.data?.map((group) => (
            <option key={group.id} value={group.id}>
              {group.name}
            </option>
          ))}
        </NativeSelect>
        <div className="flex flex-wrap gap-2 md:ml-auto">
          {can(PERMISSIONS.gradebookConfigure) && setup.data ? (
            <GradebookSetupSheet courseId={course.id} setup={setup.data} />
          ) : null}
          {can(PERMISSIONS.gradebookConfigure) ? <ManualItemDialog courseId={course.id} /> : null}
          {can(PERMISSIONS.gradeExport) ? (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="secondary" size="sm" loading={exportFile.isPending}>
                  <Download aria-hidden /> {t('export')}
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem onSelect={() => exportFile.mutate('csv')}>CSV</DropdownMenuItem>
                <DropdownMenuItem onSelect={() => exportFile.mutate('xlsx')}>XLSX</DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          ) : null}
        </div>
      </div>
      {columns.length === 0 ? (
        <EmptyState icon={Table2} title={t('emptyTitle')} description={t('emptyText')} />
      ) : (
        <div className="max-h-[70dvh] overflow-auto rounded-lg border border-border bg-surface">
          <table className="w-full border-separate border-spacing-0 text-sm">
            <thead>
              <tr>
                <th
                  scope="col"
                  className="sticky left-0 top-0 z-30 min-w-48 border-b border-r border-border bg-surface-muted px-3 py-2 text-left text-xs font-semibold uppercase text-text-muted"
                >
                  {t('student')}
                </th>
                {columns.map((column) => {
                  const sourceItemId = sourceByGradeItem.get(column.gradeItemId);
                  return (
                    <th
                      key={column.gradeItemId}
                      scope="col"
                      className="sticky top-0 z-20 min-w-32 border-b border-border bg-surface-muted px-3 py-2 text-right text-xs font-semibold text-text-muted"
                    >
                      <span className="flex items-center justify-end gap-1">
                        <span className="line-clamp-2">{column.name}</span>
                        {sourceItemId && can(PERMISSIONS.gradePublish) ? (
                          <Button
                            variant="ghost"
                            size="icon-sm"
                            aria-label={t('publishColumn', { name: column.name })}
                            onClick={() => publishItem.mutate(sourceItemId)}
                          >
                            <Megaphone aria-hidden />
                          </Button>
                        ) : null}
                      </span>
                      <span className="block font-normal">/ {column.maxScore}</span>
                    </th>
                  );
                })}
                <th
                  scope="col"
                  className="sticky right-0 top-0 z-20 min-w-24 border-b border-l border-border bg-surface-muted px-3 py-2 text-right text-xs font-semibold uppercase text-text-muted"
                >
                  {t('final')}
                </th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.userId} className="hover:bg-surface-muted/50">
                  <th
                    scope="row"
                    className="sticky left-0 z-10 border-b border-r border-border bg-surface px-3 py-1.5 text-left font-medium"
                  >
                    {row.userName}
                  </th>
                  {columns.map((column) => (
                    <td
                      key={column.gradeItemId}
                      className="border-b border-border px-2 py-1 text-right"
                    >
                      <GradeCellView
                        target={{
                          gradeItemId: column.gradeItemId,
                          userId: row.userId,
                          cell: row.cells[column.gradeItemId],
                        }}
                        maxScore={column.maxScore}
                        label={t('cellLabel', { student: row.userName, column: column.name })}
                        editable={editable}
                        onSave={(score, locked) =>
                          cellMutation.mutate({
                            target: {
                              gradeItemId: column.gradeItemId,
                              userId: row.userId,
                              cell: row.cells[column.gradeItemId],
                            },
                            score,
                            locked,
                          })
                        }
                      />
                    </td>
                  ))}
                  <td className="sticky right-0 border-b border-l border-border bg-surface px-3 py-1.5 text-right font-semibold tabular-nums">
                    {formatPercent(row.finalPercent, locale)}
                    {row.finalLabel ? (
                      <span className="block text-xs font-normal text-text-muted">
                        {row.finalLabel}
                      </span>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <p className="text-xs text-text-muted">{t('legend')}</p>
    </div>
  );
}
