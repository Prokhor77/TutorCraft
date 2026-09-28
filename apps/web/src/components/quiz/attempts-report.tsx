'use client';
import { RefreshCw, UserCog } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogFooter, DialogTrigger } from '@/components/ui/dialog';
import { EmptyState } from '@/components/ui/empty-state';
import { Field } from '@/components/ui/field';
import { DateTimeInput, Input, NativeSelect } from '@/components/ui/input';
import { LoadMore } from '@/components/ui/load-more';
import { SkeletonList } from '@/components/ui/skeleton';
import { Table, TableContainer, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { toast } from '@/components/ui/toast';
import { useCourseContext } from '@/features/courses/course-context';
import { useEnrollments } from '@/features/enrollment/use-enrollment';
import { useQuizAttempts, useQuizOverride, useRegrade } from '@/features/quiz/use-quiz';
import { PERMISSIONS } from '@/lib/access/permissions';
import { flattenPages } from '@/lib/api/pagination';
import { formatDateTime, formatScore } from '@/lib/utils/format';
import { fromDateTimeLocalValue, SECONDS_PER_MINUTE } from '@/lib/utils/time';

function OverrideDialog({ itemId }: { itemId: string }) {
  const t = useTranslations('quizReport');
  const tCommon = useTranslations('common');
  const { course } = useCourseContext();
  const students = useEnrollments(course.id, { role: 'student' });
  const override = useQuizOverride(itemId);
  const [open, setOpen] = useState(false);
  const [userId, setUserId] = useState('');
  const [minutes, setMinutes] = useState('');
  const [attempts, setAttempts] = useState('');
  const [closeAt, setCloseAt] = useState('');
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="secondary" size="sm">
          <UserCog aria-hidden /> {t('override')}
        </Button>
      </DialogTrigger>
      <DialogContent
        title={t('overrideTitle')}
        description={t('overrideHint')}
        closeLabel={tCommon('close')}
      >
        <form
          className="flex flex-col gap-4"
          onSubmit={(event) => {
            event.preventDefault();
            if (!userId) return;
            override.mutate(
              {
                userId,
                timeLimitSec: minutes ? Number(minutes) * SECONDS_PER_MINUTE : undefined,
                maxAttempts: attempts ? Number(attempts) : undefined,
                closeAt: fromDateTimeLocalValue(closeAt) ?? undefined,
              },
              {
                onSuccess: () => {
                  setOpen(false);
                  toast({ tone: 'success', title: t('overrideSaved') });
                },
              },
            );
          }}
        >
          <Field label={t('student')} required>
            <NativeSelect value={userId} onChange={(event) => setUserId(event.target.value)}>
              <option value="">{t('chooseStudent')}</option>
              {flattenPages(students.data?.pages).map((enrollment) => (
                <option key={enrollment.user.id} value={enrollment.user.id}>
                  {enrollment.user.firstName} {enrollment.user.lastName}
                </option>
              ))}
            </NativeSelect>
          </Field>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field label={t('timeLimitMinutes')}>
              <Input
                type="number"
                min={1}
                value={minutes}
                onChange={(event) => setMinutes(event.target.value)}
              />
            </Field>
            <Field label={t('maxAttempts')}>
              <Input
                type="number"
                min={1}
                value={attempts}
                onChange={(event) => setAttempts(event.target.value)}
              />
            </Field>
          </div>
          <Field label={t('closeAt')}>
            <DateTimeInput value={closeAt} onChange={(event) => setCloseAt(event.target.value)} />
          </Field>
          <DialogFooter>
            <Button type="submit" loading={override.isPending}>
              {tCommon('save')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

/** FR-QUIZ-07: attempts table, regrade all after a key fix (AC-5), overrides (FR-QUIZ-06). */
export function AttemptsReport({ itemId }: { itemId: string }) {
  const t = useTranslations('quizReport');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const { can } = useCourseContext();
  const attempts = useQuizAttempts(itemId, can(PERMISSIONS.quizViewReports));
  const regrade = useRegrade(itemId);
  const rows = flattenPages(attempts.data?.pages);
  return (
    <div className="flex flex-col gap-4">
      {can(PERMISSIONS.quizManage) ? (
        <div className="flex flex-wrap gap-2">
          <Button
            variant="secondary"
            size="sm"
            loading={regrade.isPending}
            onClick={() =>
              regrade.mutate(undefined, {
                onSuccess: ({ regraded }) =>
                  toast({ tone: 'success', title: t('regraded', { count: regraded }) }),
              })
            }
          >
            <RefreshCw aria-hidden /> {t('regrade')}
          </Button>
          <OverrideDialog itemId={itemId} />
        </div>
      ) : null}
      {attempts.isLoading ? <SkeletonList label={tCommon('loading')} /> : null}
      {attempts.isSuccess && rows.length === 0 ? (
        <EmptyState icon={RefreshCw} title={t('emptyTitle')} description={t('emptyText')} />
      ) : null}
      {rows.length > 0 ? (
        <TableContainer>
          <Table>
            <THead>
              <tr>
                <TH>{t('student')}</TH>
                <TH>{t('attempt')}</TH>
                <TH>{t('state')}</TH>
                <TH>{t('started')}</TH>
                <TH>{t('finished')}</TH>
                <TH>{t('score')}</TH>
              </tr>
            </THead>
            <TBody>
              {rows.map((row) => (
                <TR key={row.id}>
                  <TD className="font-medium">{row.userName}</TD>
                  <TD>{row.number}</TD>
                  <TD>{t(`states.${row.state}`)}</TD>
                  <TD>{formatDateTime(row.startedAt, locale)}</TD>
                  <TD>{row.finishedAt ? formatDateTime(row.finishedAt, locale) : '—'}</TD>
                  <TD>{formatScore(row.score, row.maxScore, locale)}</TD>
                </TR>
              ))}
            </TBody>
          </Table>
        </TableContainer>
      ) : null}
      <LoadMore
        hasMore={!!attempts.hasNextPage}
        loading={attempts.isFetchingNextPage}
        onClick={() => void attempts.fetchNextPage()}
        label={tCommon('loadMore')}
      />
    </div>
  );
}
