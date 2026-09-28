'use client';
import { Search, UserMinus, UserX, Users } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { useDeferredValue, useState } from 'react';
import { GroupsPanel } from '@/components/participants/groups-panel';
import { EnrolUsersDialog } from '@/components/participants/enrol-users-dialog';
import { InviteDialog } from '@/components/participants/invite-dialog';
import { ProgressReport } from '@/components/participants/progress-report';
import { Avatar } from '@/components/ui/avatar';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { EmptyState } from '@/components/ui/empty-state';
import { Input, NativeSelect } from '@/components/ui/input';
import { LoadMore } from '@/components/ui/load-more';
import { SkeletonList } from '@/components/ui/skeleton';
import { Table, TableContainer, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { toast } from '@/components/ui/toast';
import { useCourseContext } from '@/features/courses/course-context';
import {
  useEnrollmentMutations,
  useEnrollments,
  useGroups,
} from '@/features/enrollment/use-enrollment';
import { PERMISSIONS } from '@/lib/access/permissions';
import { flattenPages } from '@/lib/api/pagination';
import { COURSE_ROLES, type CourseRole } from '@/lib/api/schemas/common';
import type { Enrollment } from '@/lib/api/schemas/enrollment';
import { formatRelative } from '@/lib/utils/format';

/** Participants (SPEC §10): search, roles, groups, last access, bulk actions, invite. */
export default function ParticipantsPage() {
  const t = useTranslations('participants');
  const tRoles = useTranslations('roles');
  const tUndo = useTranslations('undo');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const { course, can } = useCourseContext();
  const [query, setQuery] = useState('');
  const [role, setRole] = useState('');
  const [groupId, setGroupId] = useState('');
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const deferredQuery = useDeferredValue(query.trim());
  const enrollments = useEnrollments(course.id, {
    q: deferredQuery || undefined,
    role: role || undefined,
    groupId: groupId || undefined,
  });
  const groups = useGroups(course.id);
  const mutations = useEnrollmentMutations(course.id);
  const manage = can(PERMISSIONS.enrollmentManage);
  const rows = flattenPages(enrollments.data?.pages);
  const groupName = new Map((groups.data ?? []).map((group) => [group.id, group.name]));

  const removeWithUndo = (enrollment: Enrollment) =>
    mutations.remove.mutate(enrollment.id, {
      onSuccess: () =>
        toast({
          title: tUndo('participantRemoved', {
            name: `${enrollment.user.firstName} ${enrollment.user.lastName}`,
          }),
          action: {
            label: tUndo('undo'),
            onClick: () =>
              mutations.enrol.mutate({ userIds: [enrollment.user.id], role: enrollment.role }),
          },
        }),
    });

  const bulk = async (action: (enrollment: Enrollment) => Promise<unknown>) => {
    const targets = rows.filter((row) => selected.has(row.id));
    const results = await Promise.allSettled(targets.map(action));
    const failed = results.filter((result) => result.status === 'rejected').length;
    toast({
      tone: failed ? 'error' : 'success',
      title: t('bulkResult', { succeeded: targets.length - failed, failed }),
    });
    setSelected(new Set());
  };

  return (
    <Tabs defaultValue="people">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <TabsList>
          <TabsTrigger value="people">{t('tabPeople')}</TabsTrigger>
          {can(PERMISSIONS.groupManage) ? (
            <TabsTrigger value="groups">{t('tabGroups')}</TabsTrigger>
          ) : null}
          {can(PERMISSIONS.completionViewAll) ? (
            <TabsTrigger value="progress">{t('tabProgress')}</TabsTrigger>
          ) : null}
        </TabsList>
        {manage ? (
          <div className="flex flex-wrap gap-2">
            <EnrolUsersDialog courseId={course.id} />
            <InviteDialog courseId={course.id} />
          </div>
        ) : null}
      </div>
      <TabsContent value="people" className="flex flex-col gap-4">
        <div className="flex flex-col gap-2 md:flex-row">
          <div className="relative flex-1">
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
            aria-label={t('role')}
            value={role}
            onChange={(event) => setRole(event.target.value)}
            className="md:w-44"
          >
            <option value="">{t('allRoles')}</option>
            {COURSE_ROLES.map((option) => (
              <option key={option} value={option}>
                {tRoles(option)}
              </option>
            ))}
          </NativeSelect>
          <NativeSelect
            aria-label={t('group')}
            value={groupId}
            onChange={(event) => setGroupId(event.target.value)}
            className="md:w-44"
          >
            <option value="">{t('allGroups')}</option>
            {groups.data?.map((group) => (
              <option key={group.id} value={group.id}>
                {group.name}
              </option>
            ))}
          </NativeSelect>
        </div>
        {manage && selected.size > 0 ? (
          <div
            role="region"
            aria-label={t('bulkActions')}
            className="flex flex-wrap items-center gap-2 rounded-md border border-border bg-surface p-2"
          >
            <span className="text-sm font-medium">{t('selected', { count: selected.size })}</span>
            <NativeSelect
              aria-label={t('setRole')}
              defaultValue=""
              className="h-8 w-44 text-xs"
              onChange={(event) =>
                event.target.value &&
                void bulk((row) =>
                  mutations.update.mutateAsync({
                    id: row.id,
                    patch: { role: event.target.value as CourseRole },
                  }),
                )
              }
            >
              <option value="">{t('setRole')}</option>
              {COURSE_ROLES.map((option) => (
                <option key={option} value={option}>
                  {tRoles(option)}
                </option>
              ))}
            </NativeSelect>
            <Button
              size="sm"
              variant="secondary"
              onClick={() =>
                void bulk((row) =>
                  mutations.update.mutateAsync({ id: row.id, patch: { status: 'suspended' } }),
                )
              }
            >
              <UserX aria-hidden /> {t('suspend')}
            </Button>
            <Button
              size="sm"
              variant="secondary"
              onClick={() => void bulk((row) => mutations.remove.mutateAsync(row.id))}
            >
              <UserMinus aria-hidden /> {t('remove')}
            </Button>
          </div>
        ) : null}
        {enrollments.isLoading ? <SkeletonList label={tCommon('loading')} /> : null}
        {enrollments.isSuccess && rows.length === 0 ? (
          <EmptyState
            icon={Users}
            title={t('emptyTitle')}
            description={t('emptyText')}
            action={manage ? <InviteDialog courseId={course.id} /> : null}
          />
        ) : null}
        {rows.length > 0 ? (
          <TableContainer>
            <Table>
              <THead>
                <tr>
                  {manage ? (
                    <TH className="w-10">
                      <Checkbox
                        aria-label={t('selectAll')}
                        checked={
                          selected.size === rows.length
                            ? true
                            : selected.size > 0
                              ? 'indeterminate'
                              : false
                        }
                        onCheckedChange={(checked) =>
                          setSelected(checked ? new Set(rows.map((row) => row.id)) : new Set())
                        }
                      />
                    </TH>
                  ) : null}
                  <TH>{t('name')}</TH>
                  <TH>{t('role')}</TH>
                  <TH>{t('groups')}</TH>
                  <TH>{t('status')}</TH>
                  <TH>{t('lastAccess')}</TH>
                  {manage ? (
                    <TH className="w-10">
                      <span className="sr-only">{t('actions')}</span>
                    </TH>
                  ) : null}
                </tr>
              </THead>
              <TBody>
                {rows.map((row) => {
                  const name = `${row.user.firstName} ${row.user.lastName}`;
                  return (
                    <TR key={row.id}>
                      {manage ? (
                        <TD>
                          <Checkbox
                            aria-label={t('selectPerson', { name })}
                            checked={selected.has(row.id)}
                            onCheckedChange={(checked) =>
                              setSelected((current) => {
                                const next = new Set(current);
                                if (checked) next.add(row.id);
                                else next.delete(row.id);
                                return next;
                              })
                            }
                          />
                        </TD>
                      ) : null}
                      <TD>
                        <span className="flex items-center gap-2">
                          <Avatar name={name} src={row.user.avatarUrl} size="sm" />
                          <span className="flex flex-col">
                            <span className="font-medium">{name}</span>
                            <span className="text-xs text-text-muted">{row.user.email}</span>
                          </span>
                        </span>
                      </TD>
                      <TD>
                        {manage ? (
                          <NativeSelect
                            aria-label={t('roleOf', { name })}
                            value={row.role}
                            className="h-8 w-36 text-xs"
                            onChange={(event) =>
                              mutations.update.mutate({
                                id: row.id,
                                patch: { role: event.target.value as CourseRole },
                              })
                            }
                          >
                            {COURSE_ROLES.map((option) => (
                              <option key={option} value={option}>
                                {tRoles(option)}
                              </option>
                            ))}
                          </NativeSelect>
                        ) : (
                          tRoles(row.role)
                        )}
                      </TD>
                      <TD className="text-xs">
                        {row.groupIds
                          .map((id) => groupName.get(id))
                          .filter(Boolean)
                          .join(', ') || '—'}
                      </TD>
                      <TD>
                        <Badge
                          tone={
                            row.status === 'active'
                              ? 'success'
                              : row.status === 'suspended'
                                ? 'warning'
                                : 'neutral'
                          }
                        >
                          {t(`statuses.${row.status}`)}
                        </Badge>
                      </TD>
                      <TD className="text-xs text-text-muted">
                        {row.lastAccessAt ? formatRelative(row.lastAccessAt, locale) : t('never')}
                      </TD>
                      {manage ? (
                        <TD>
                          <Button
                            variant="ghost"
                            size="icon-sm"
                            aria-label={t('removePerson', { name })}
                            onClick={() => removeWithUndo(row)}
                          >
                            <UserMinus aria-hidden />
                          </Button>
                        </TD>
                      ) : null}
                    </TR>
                  );
                })}
              </TBody>
            </Table>
          </TableContainer>
        ) : null}
        <LoadMore
          hasMore={!!enrollments.hasNextPage}
          loading={enrollments.isFetchingNextPage}
          onClick={() => void enrollments.fetchNextPage()}
          label={tCommon('loadMore')}
        />
      </TabsContent>
      <TabsContent value="groups">
        <GroupsPanel courseId={course.id} />
      </TabsContent>
      <TabsContent value="progress">
        <ProgressReport courseId={course.id} />
      </TabsContent>
    </Tabs>
  );
}
