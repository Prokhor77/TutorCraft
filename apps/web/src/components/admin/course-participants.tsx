'use client';
import { Search, Users } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { useDeferredValue, useState } from 'react';
import { AdminTablePanel, PersonCell } from '@/components/admin/admin-panel';
import { Badge } from '@/components/ui/badge';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { Input, NativeSelect } from '@/components/ui/input';
import { LoadMore } from '@/components/ui/load-more';
import { Progress } from '@/components/ui/progress';
import { SkeletonList } from '@/components/ui/skeleton';
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { useEnrollments, useGroups } from '@/features/enrollment/use-enrollment';
import { useProgressReport } from '@/features/gradebook/use-gradebook';
import { flattenPages } from '@/lib/api/pagination';
import { COURSE_ROLES } from '@/lib/api/schemas/common';
import { formatPercent, formatRelative, fullName } from '@/lib/utils/format';

const STATUS_TONES = { active: 'success', suspended: 'warning', completed: 'primary' } as const;

/**
 * Who takes part in the course — students, teachers, assistants — with their status, groups, progress and last
 * visit. Read-only: the administrator does not enrol or remove anyone from here.
 */
export function CourseParticipants({ courseId }: { courseId: string }) {
  const t = useTranslations('admin.course.participants');
  const tParticipants = useTranslations('participants');
  const tRoles = useTranslations('roles');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const [query, setQuery] = useState('');
  const [role, setRole] = useState('');
  const deferredQuery = useDeferredValue(query.trim());
  const enrollments = useEnrollments(courseId, {
    q: deferredQuery || undefined,
    role: role || undefined,
  });
  const groups = useGroups(courseId);
  const report = useProgressReport(courseId);
  const rows = flattenPages(enrollments.data?.pages);
  const groupName = new Map((groups.data ?? []).map((group) => [group.id, group.name]));
  const progressByUser = new Map((report.data?.rows ?? []).map((row) => [row.userId, row.percent]));
  const filtered = !!deferredQuery || !!role;

  return (
    <AdminTablePanel
      title={t('title')}
      count={
        rows.length > 0 ? (
          <Badge tone="primary">{t('shownCount', { count: rows.length })}</Badge>
        ) : null
      }
      description={t('hint')}
      toolbar={
        <div className="flex flex-col gap-2 md:flex-row">
          <div className="relative md:max-w-md md:flex-1">
            <Search
              className="pointer-events-none absolute left-4 top-1/2 size-4 -translate-y-1/2 text-text-muted"
              aria-hidden
            />
            <Input
              type="search"
              aria-label={tParticipants('search')}
              placeholder={tParticipants('search')}
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              className="rounded-full border-transparent bg-surface-muted pl-10"
            />
          </div>
          <NativeSelect
            aria-label={tParticipants('role')}
            value={role}
            onChange={(event) => setRole(event.target.value)}
            className="h-10 rounded-full border-transparent bg-surface-muted md:w-56"
          >
            <option value="">{tParticipants('allRoles')}</option>
            {COURSE_ROLES.map((value) => (
              <option key={value} value={value}>
                {tRoles(value)}
              </option>
            ))}
          </NativeSelect>
        </div>
      }
      footer={
        <LoadMore
          hasMore={!!enrollments.hasNextPage}
          loading={enrollments.isFetchingNextPage}
          onClick={() => void enrollments.fetchNextPage()}
          label={tCommon('loadMore')}
        />
      }
    >
      {enrollments.isLoading ? (
        <div className="p-5 sm:p-6">
          <SkeletonList label={tCommon('loading')} />
        </div>
      ) : null}
      {enrollments.isError ? (
        <div className="p-5 sm:p-6">
          <ErrorState
            title={t('loadError')}
            retryLabel={tCommon('retry')}
            onRetry={() => void enrollments.refetch()}
          />
        </div>
      ) : null}
      {enrollments.isSuccess && rows.length === 0 ? (
        <div className="p-5 sm:p-6">
          <EmptyState
            icon={Users}
            title={filtered ? t('nothingFound') : t('emptyTitle')}
            description={filtered ? t('nothingFoundText') : t('emptyText')}
          />
        </div>
      ) : null}
      {rows.length > 0 ? (
        <Table>
          <THead>
            <tr>
              <TH>{tParticipants('name')}</TH>
              <TH>{tParticipants('role')}</TH>
              <TH className="hidden sm:table-cell">{tParticipants('status')}</TH>
              <TH className="hidden lg:table-cell">{tParticipants('groups')}</TH>
              <TH className="hidden min-w-40 md:table-cell">{t('progress')}</TH>
              <TH className="hidden md:table-cell">{tParticipants('lastAccess')}</TH>
            </tr>
          </THead>
          <TBody>
            {rows.map((enrollment) => {
              const name = fullName(enrollment.user);
              const percent = progressByUser.get(enrollment.user.id);
              const groupNames = enrollment.groupIds
                .map((id) => groupName.get(id))
                .filter(Boolean)
                .join(', ');
              return (
                <TR key={enrollment.id}>
                  <TD className="min-w-48">
                    <PersonCell name={name} secondary={enrollment.user.email} />
                  </TD>
                  <TD className="whitespace-nowrap text-sm">{tRoles(enrollment.role)}</TD>
                  <TD className="hidden sm:table-cell">
                    <Badge tone={STATUS_TONES[enrollment.status]}>
                      {tParticipants(`statuses.${enrollment.status}`)}
                    </Badge>
                  </TD>
                  <TD className="hidden text-sm text-text-muted lg:table-cell">
                    {groupNames || '—'}
                  </TD>
                  <TD className="hidden md:table-cell">
                    {enrollment.role === 'student' && percent !== undefined ? (
                      <span className="flex flex-col gap-1">
                        <span className="text-xs font-semibold tabular-nums">
                          {formatPercent(percent, locale)}
                        </span>
                        <Progress value={percent} label={t('progressOf', { name })} />
                      </span>
                    ) : (
                      <span className="text-text-muted">—</span>
                    )}
                  </TD>
                  <TD className="hidden whitespace-nowrap text-xs text-text-muted md:table-cell">
                    {enrollment.lastAccessAt
                      ? formatRelative(enrollment.lastAccessAt, locale)
                      : tParticipants('never')}
                  </TD>
                </TR>
              );
            })}
          </TBody>
        </Table>
      ) : null}
    </AdminTablePanel>
  );
}
