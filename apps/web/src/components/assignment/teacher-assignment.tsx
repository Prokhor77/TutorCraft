'use client';
import { CalendarPlus, ClipboardCheck, Megaphone } from 'lucide-react';
import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { useState } from 'react';
import { StatusBadge } from '@/components/course/item-meta';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogFooter, DialogTrigger } from '@/components/ui/dialog';
import { EmptyState } from '@/components/ui/empty-state';
import { Field } from '@/components/ui/field';
import { DateTimeInput, NativeSelect } from '@/components/ui/input';
import { LoadMore } from '@/components/ui/load-more';
import { Panel } from '@/components/ui/page-header';
import { Segmented } from '@/components/ui/segmented';
import { SkeletonList } from '@/components/ui/skeleton';
import { Table, TableContainer, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { ROUTES } from '@/features/auth/routes';
import {
  useGrantExtension,
  usePublishGrades,
  useSubmissions,
} from '@/features/assessment/use-assessment';
import { useCourseContext } from '@/features/courses/course-context';
import { useEnrollments } from '@/features/enrollment/use-enrollment';
import { PERMISSIONS } from '@/lib/access/permissions';
import { flattenPages } from '@/lib/api/pagination';
import type { ItemDetail } from '@/lib/api/schemas/courses';
import { formatDateTime } from '@/lib/utils/format';
import { fromDateTimeLocalValue } from '@/lib/utils/time';

type StatusFilter = 'all' | 'submitted' | 'graded';

function ExtensionDialog({ item }: { item: ItemDetail }) {
  const t = useTranslations('assignment');
  const tCommon = useTranslations('common');
  const { course } = useCourseContext();
  const students = useEnrollments(course.id, { role: 'student' });
  const grant = useGrantExtension(item.id);
  const [open, setOpen] = useState(false);
  const [userId, setUserId] = useState('');
  const [dueAt, setDueAt] = useState('');
  const people = flattenPages(students.data?.pages);
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="secondary" size="sm">
          <CalendarPlus aria-hidden /> {t('extension')}
        </Button>
      </DialogTrigger>
      <DialogContent
        title={t('extensionTitle')}
        description={t('extensionHint')}
        closeLabel={tCommon('close')}
      >
        <form
          className="flex flex-col gap-4"
          onSubmit={(event) => {
            event.preventDefault();
            const due = fromDateTimeLocalValue(dueAt);
            if (!userId || !due) return;
            grant.mutate({ userId, dueAt: due }, { onSuccess: () => setOpen(false) });
          }}
        >
          <Field label={t('student')} required>
            <NativeSelect value={userId} onChange={(event) => setUserId(event.target.value)}>
              <option value="">{t('chooseStudent')}</option>
              {people.map((enrollment) => (
                <option key={enrollment.user.id} value={enrollment.user.id}>
                  {enrollment.user.firstName} {enrollment.user.lastName}
                </option>
              ))}
            </NativeSelect>
          </Field>
          <Field label={t('newDueAt')} required>
            <DateTimeInput value={dueAt} onChange={(event) => setDueAt(event.target.value)} />
          </Field>
          <DialogFooter>
            <Button type="submit" loading={grant.isPending}>
              {t('grant')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

/** Teacher view of an assignment: submissions, publish grades (FR-ASSIGN-07), extensions (FR-ASSIGN-03). */
export function AssignmentSubmissions({ item }: { item: ItemDetail }) {
  const t = useTranslations('assignment');
  const tWorkspace = useTranslations('workspace');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const { course, can } = useCourseContext();
  const [filter, setFilter] = useState<StatusFilter>('all');
  const submissions = useSubmissions(item.id, filter === 'all' ? undefined : filter);
  const publish = usePublishGrades(item.id);
  const rows = flattenPages(submissions.data?.pages);
  const reviewHref = `${ROUTES.gradingReview}?courseId=${course.id}&itemId=${item.id}`;

  return (
    <Panel title={tWorkspace('submissionsTitle')}>
      <div className="flex flex-wrap items-center gap-2">
        <Segmented<StatusFilter>
          label={t('filter')}
          value={filter}
          onChange={setFilter}
          options={[
            { value: 'all', label: t('filterAll') },
            { value: 'submitted', label: t('filterToGrade') },
            { value: 'graded', label: t('filterGraded') },
          ]}
        />
        <div className="ml-auto flex flex-wrap gap-2">
          {can(PERMISSIONS.submissionGrade) ? (
            <Button asChild size="sm">
              <Link href={reviewHref}>
                <ClipboardCheck aria-hidden /> {t('startGrading')}
              </Link>
            </Button>
          ) : null}
          {can(PERMISSIONS.gradePublish) ? (
            <Button
              variant="secondary"
              size="sm"
              loading={publish.isPending}
              onClick={() => publish.mutate()}
            >
              <Megaphone aria-hidden /> {t('publishAll')}
            </Button>
          ) : null}
          {can(PERMISSIONS.courseEdit) ? <ExtensionDialog item={item} /> : null}
        </div>
      </div>
      {submissions.isLoading ? <SkeletonList label={tCommon('loading')} /> : null}
      {submissions.isSuccess && rows.length === 0 ? (
        <EmptyState
          icon={ClipboardCheck}
          title={t('noSubmissions')}
          description={t('noSubmissionsHint')}
        />
      ) : null}
      {rows.length > 0 ? (
        <TableContainer className="rounded-md shadow-none">
          <Table>
            <THead>
              <tr>
                <TH>{t('student')}</TH>
                <TH>{t('status')}</TH>
                <TH>{t('submittedAt')}</TH>
                <TH>{t('score')}</TH>
              </tr>
            </THead>
            <TBody>
              {rows.map((row) => (
                <TR key={row.id}>
                  <TD>
                    <Link
                      href={`${reviewHref}&submissionId=${row.id}`}
                      className="font-medium hover:underline"
                    >
                      {row.userName}
                    </Link>
                  </TD>
                  <TD>
                    <span className="flex items-center gap-1.5">
                      <StatusBadge status={row.status} />
                      {row.late ? <span className="text-xs text-warning">{t('late')}</span> : null}
                    </span>
                  </TD>
                  <TD>{row.submittedAt ? formatDateTime(row.submittedAt, locale) : '—'}</TD>
                  <TD>{row.score ?? '—'}</TD>
                </TR>
              ))}
            </TBody>
          </Table>
        </TableContainer>
      ) : null}
      <LoadMore
        hasMore={!!submissions.hasNextPage}
        loading={submissions.isFetchingNextPage}
        onClick={() => void submissions.fetchNextPage()}
        label={tCommon('loadMore')}
      />
    </Panel>
  );
}
