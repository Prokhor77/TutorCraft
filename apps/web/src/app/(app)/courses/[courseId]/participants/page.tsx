'use client';
import { Search, UserMinus, UserX, Users } from 'lucide-react';
import { ProgressReport } from '@/components/participants/progress-report';
import { useLocale, useTranslations } from 'next-intl';
import { useDeferredValue, useState } from 'react';
import { GroupsPanel } from '@/components/participants/groups-panel';
import { EnrolUsersDialog } from '@/components/participants/enrol-users-dialog';
import { InviteDialog } from '@/components/participants/invite-dialog';
import { Avatar } from '@/components/ui/avatar';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { EmptyState } from '@/components/ui/empty-state';
import { Breadcrumbs, PageHeader } from '@/components/ui/page-header';
import { Progress } from '@/components/ui/progress';
import { Input, NativeSelect } from '@/components/ui/input';
import { LoadMore } from '@/components/ui/load-more';
import { SkeletonList } from '@/components/ui/skeleton';
import { Table, TableContainer, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { toast } from '@/components/ui/toast';
import { ROUTES } from '@/features/auth/routes';
import { useCourseContext } from '@/features/courses/course-context';
import { useProgressReport } from '@/features/gradebook/use-gradebook';
import {
  useEnrollmentMutations,
  useEnrollments,
  useGroups,
} from '@/features/enrollment/use-enrollment';
import { PERMISSIONS } from '@/lib/access/permissions';
import { flattenPages } from '@/lib/api/pagination';
import { COURSE_ROLES, type CourseRole } from '@/lib/api/schemas/common';
import type { Enrollment } from '@/lib/api/schemas/enrollment';
import { formatPercent, formatRelative } from '@/lib/utils/format';

const PERCENT = 100;
const PILL_FIELD = 'h-10 rounded-full border-transparent bg-surface-muted';

/** Participants (SPEC §10): search, roles, groups, last access, bulk actions, invite. */
export default function ParticipantsPage() {
  const t = useTranslations('participants');
  const tRoles = useTranslations('roles');
  const tUndo = useTranslations('undo');
  const tCommon = useTranslations('common');
  const tShell = useTranslations('shell');
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
  const canProgress = can(PERMISSIONS.completionViewAll);
  const report = useProgressReport(course.id, canProgress);
  const progressByUser = new Map((report.data?.rows ?? []).map((row) => [row.userId, row.percent]));
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
    <Tabs defaultValue="people" className="flex flex-col gap-gutter">
      <PageHeader
        className="mb-0"
        breadcrumbs={
          <Breadcrumbs
            label={tShell('breadcrumbs')}
            items={[
              { label: tShell('myCourses'), href: ROUTES.courses },
              { label: course.title, href: ROUTES.course(course.id) },
              { label: tShell('participants') },
            ]}
          />
        }
        title={t('pageTitle')}
        description={t('pageDescription')}
        actions={
          manage ? (
            <>
              <EnrolUsersDialog courseId={course.id} />
              <InviteDialog courseId={course.id} />
            </>
          ) : null
        }
      >
        <TabsList>
          <TabsTrigger value="people">{t('tabPeople')}</TabsTrigger>
          {can(PERMISSIONS.groupManage) ? (
            <TabsTrigger value="groups" className="group">
              {t('tabGroups')}
              {groups.data && groups.data.length > 0 ? (
                <span className="min-w-5 rounded-full bg-surface-container px-1.5 text-center text-label-sm leading-5 group-data-[state=active]:bg-primary-foreground group-data-[state=active]:text-primary">
                  {groups.data.length}
                </span>
              ) : null}
            </TabsTrigger>
          ) : null}
          {canProgress ? <TabsTrigger value="progress">{t('tabProgress')}</TabsTrigger> : null}
        </TabsList>
      </PageHeader>
      <TabsContent value="people" className="mt-0 flex flex-col gap-4">
        <section
          aria-label={t('tabPeople')}
          className="flex min-w-0 flex-col overflow-hidden rounded-lg border border-card-border bg-surface shadow-sm"
        >
          <div className="flex flex-col gap-2 p-5 sm:p-6 md:flex-row md:items-center">
            <div className="relative flex-1">
              <Search
                className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-text-muted"
                aria-hidden
              />
              <Input
                type="search"
                aria-label={t('search')}
                placeholder={t('search')}
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                className={`${PILL_FIELD} pl-10`}
              />
            </div>
            <NativeSelect
              aria-label={t('role')}
              value={role}
              onChange={(event) => setRole(event.target.value)}
              className={`${PILL_FIELD} md:w-44`}
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
              className={`${PILL_FIELD} md:w-44`}
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
              className="mx-5 mb-4 flex flex-wrap items-center gap-2 rounded-full bg-primary-soft/60 py-1.5 pl-4 pr-1.5 sm:mx-6"
            >
              <span className="text-sm font-medium">{t('selected', { count: selected.size })}</span>
              <NativeSelect
                aria-label={t('setRole')}
                defaultValue=""
                className="h-8 w-44 rounded-full border-transparent text-xs"
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
          {enrollments.isLoading ? (
            <div className="px-5 pb-5 sm:px-6">
              <SkeletonList label={tCommon('loading')} />
            </div>
          ) : null}
          {enrollments.isSuccess && rows.length === 0 ? (
            <div className="px-5 pb-5 sm:px-6 sm:pb-6">
              <EmptyState
                icon={Users}
                title={t('emptyTitle')}
                description={t('emptyText')}
                action={manage ? <InviteDialog courseId={course.id} /> : null}
              />
            </div>
          ) : null}
          {rows.length > 0 ? (
            <TableContainer className="rounded-none border-0 border-t border-border shadow-none">
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
                    {canProgress ? <TH className="hidden sm:table-cell">{t('progress')}</TH> : null}
                    <TH className="hidden lg:table-cell">{t('groups')}</TH>
                    <TH className="hidden md:table-cell">{t('status')}</TH>
                    <TH className="hidden md:table-cell">{t('lastAccess')}</TH>
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
                    const percent = progressByUser.get(row.user.id);
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
                          <span className="flex items-center gap-3">
                            <Avatar name={name} src={row.user.avatarUrl} size="md" />
                            <span className="flex min-w-0 flex-col">
                              <span className="truncate font-semibold">{name}</span>
                              <span className="truncate text-xs text-text-muted">
                                {row.user.email}
                              </span>
                            </span>
                          </span>
                        </TD>
                        <TD>
                          {manage ? (
                            <NativeSelect
                              aria-label={t('roleOf', { name })}
                              value={row.role}
                              className="h-8 w-36 rounded-full border-transparent bg-surface-muted text-xs"
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
                        {canProgress ? (
                          <TD className="hidden sm:table-cell">
                            {percent === undefined ? (
                              <span className="text-xs text-text-muted">—</span>
                            ) : (
                              <span className="flex items-center gap-2">
                                <Progress
                                  value={percent}
                                  label={t('progressOf', { name })}
                                  tone={percent >= PERCENT ? 'success' : 'primary'}
                                  className="h-1.5 w-20"
                                />
                                <span className="text-xs font-semibold tabular-nums">
                                  {formatPercent(percent, locale)}
                                </span>
                              </span>
                            )}
                          </TD>
                        ) : null}
                        <TD className="hidden lg:table-cell">
                          <span className="flex flex-wrap gap-1">
                            {row.groupIds
                              .map((id) => groupName.get(id))
                              .filter((group): group is string => !!group)
                              .map((group) => (
                                <span
                                  key={group}
                                  className="rounded-full bg-surface-muted px-2 py-0.5 text-label-sm text-text-muted"
                                >
                                  {group}
                                </span>
                              ))}
                            {row.groupIds.every((id) => !groupName.has(id)) ? (
                              <span className="text-xs text-text-muted">—</span>
                            ) : null}
                          </span>
                        </TD>
                        <TD className="hidden md:table-cell">
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
                        <TD className="hidden text-xs text-text-muted md:table-cell">
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
        </section>
        <LoadMore
          hasMore={!!enrollments.hasNextPage}
          loading={enrollments.isFetchingNextPage}
          onClick={() => void enrollments.fetchNextPage()}
          label={tCommon('loadMore')}
        />
      </TabsContent>
      <TabsContent value="groups" className="mt-0">
        <GroupsPanel courseId={course.id} />
      </TabsContent>
      {canProgress ? (
        <TabsContent value="progress" className="mt-0">
          <ProgressReport courseId={course.id} />
        </TabsContent>
      ) : null}
    </Tabs>
  );
}
