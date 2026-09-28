'use client';
import { KeyRound, Lock, MoreHorizontal, Search, UserCheck, UserX, Users } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { useDeferredValue, useState } from 'react';
import { AccountOriginCell } from '@/components/admin/account-origin';
import { AdminTablePanel, FilterChips, PersonCell } from '@/components/admin/admin-panel';
import { ActivationLink } from '@/components/participants/activation-link';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent } from '@/components/ui/dialog';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { Input } from '@/components/ui/input';
import { LoadMore } from '@/components/ui/load-more';
import { Breadcrumbs, PageHeader } from '@/components/ui/page-header';
import { SkeletonList } from '@/components/ui/skeleton';
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { toast } from '@/components/ui/toast';
import { ROUTES } from '@/features/auth/routes';
import { useSchoolMemberMutations, useSchoolMembers } from '@/features/members/use-members';
import { isSchoolOwnerHint } from '@/lib/access/permissions';
import { flattenPages } from '@/lib/api/pagination';
import { MEMBER_STATUS_FILTERS, type SchoolMember } from '@/lib/api/schemas/members';
import { formatRelative } from '@/lib/utils/format';
import { selectMe, useAuthStore } from '@/stores/auth-store';

const STATUS_TONE = { active: 'success', suspended: 'warning', invited: 'info' } as const;

/**
 * Students of the tutor's own school (member.view / member.manage): who invited whom, block / unblock in the school,
 * a fresh activation link for those who have not accepted yet. A platform block is shown but cannot be lifted here.
 */
export default function SchoolStudentsPage() {
  const t = useTranslations('members');
  const tNav = useTranslations('nav');
  const tShell = useTranslations('shell');
  const me = useAuthStore(selectMe);
  if (!isSchoolOwnerHint(me?.tenantRoles)) {
    return <EmptyState icon={Lock} title={t('forbiddenTitle')} description={t('forbiddenText')} />;
  }
  return (
    <>
      <PageHeader
        breadcrumbs={
          <Breadcrumbs
            label={tShell('breadcrumbs')}
            items={[{ label: tNav('home'), href: ROUTES.home }, { label: tNav('students') }]}
          />
        }
        title={t('schoolTitle')}
        description={t('schoolDescription')}
      />
      <MembersTable />
    </>
  );
}

function MembersTable() {
  const t = useTranslations('members');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('');
  const [activationUrl, setActivationUrl] = useState<string | null>(null);
  const deferredQuery = useDeferredValue(query.trim());
  const members = useSchoolMembers({ q: deferredQuery || undefined, status: status || undefined });
  const { setStatus: changeStatus, activationLink } = useSchoolMemberMutations();
  const rows = flattenPages(members.data?.pages);
  const name = (member: SchoolMember) => `${member.firstName} ${member.lastName}`;

  const toggleBlock = (member: SchoolMember) => {
    const next = member.status === 'suspended' ? 'active' : 'suspended';
    changeStatus.mutate(
      { id: member.id, status: next },
      {
        onSuccess: () =>
          toast({
            tone: 'success',
            title: t(next === 'suspended' ? 'blockedToast' : 'unblockedToast', {
              name: name(member),
            }),
          }),
      },
    );
  };

  return (
    <>
      <AdminTablePanel
        title={t('schoolPanel')}
        count={
          rows.length > 0 ? (
            <Badge tone="primary">{t('shownCount', { count: rows.length })}</Badge>
          ) : null
        }
        description={t('schoolPanelHint')}
        toolbar={
          <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
            <div className="relative lg:max-w-md lg:flex-1">
              <Search
                className="pointer-events-none absolute left-4 top-1/2 size-4 -translate-y-1/2 text-text-muted"
                aria-hidden
              />
              <Input
                type="search"
                aria-label={t('search')}
                placeholder={t('search')}
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                className="rounded-full border-transparent bg-surface-muted pl-10"
              />
            </div>
            <FilterChips
              label={t('status')}
              value={status}
              onChange={setStatus}
              options={[
                { value: '', label: t('allStatuses') },
                ...MEMBER_STATUS_FILTERS.map((option) => ({
                  value: option,
                  label: t(`statusFilters.${option}`),
                })),
              ]}
            />
          </div>
        }
        footer={
          <LoadMore
            hasMore={!!members.hasNextPage}
            loading={members.isFetchingNextPage}
            onClick={() => void members.fetchNextPage()}
            label={tCommon('loadMore')}
          />
        }
      >
        {members.isLoading ? (
          <div className="p-5 sm:p-6">
            <SkeletonList label={tCommon('loading')} />
          </div>
        ) : null}
        {members.isError ? (
          <div className="p-5 sm:p-6">
            <ErrorState
              title={t('loadError')}
              retryLabel={tCommon('retry')}
              onRetry={() => void members.refetch()}
            />
          </div>
        ) : null}
        {members.isSuccess && rows.length === 0 ? (
          <div className="p-5 sm:p-6">
            <EmptyState icon={Users} title={t('emptyTitle')} description={t('schoolEmptyText')} />
          </div>
        ) : null}
        {rows.length > 0 ? (
          <Table>
            <THead>
              <tr>
                <TH>{t('user')}</TH>
                <TH className="hidden sm:table-cell">{t('status')}</TH>
                <TH className="hidden md:table-cell">{t('origin')}</TH>
                <TH className="hidden lg:table-cell">{t('lastLogin')}</TH>
                <TH className="w-10">
                  <span className="sr-only">{t('actions')}</span>
                </TH>
              </tr>
            </THead>
            <TBody>
              {rows.map((member) => {
                const owner = member.tenantRoles.includes('tenant_admin');
                return (
                  <TR key={member.id}>
                    <TD className="sm:min-w-56">
                      <PersonCell
                        name={name(member)}
                        secondary={member.email}
                        extra={
                          <span className="mt-1 flex flex-wrap gap-1 sm:hidden">
                            <MemberStatusBadges member={member} />
                          </span>
                        }
                      />
                    </TD>
                    <TD className="hidden sm:table-cell">
                      <span className="flex flex-wrap gap-1">
                        <MemberStatusBadges member={member} />
                      </span>
                    </TD>
                    <TD className="hidden md:table-cell">
                      <AccountOriginCell origin={member.origin} createdBy={member.createdBy} />
                    </TD>
                    <TD className="hidden whitespace-nowrap text-xs text-text-muted lg:table-cell">
                      {member.lastLoginAt ? formatRelative(member.lastLoginAt, locale) : t('never')}
                    </TD>
                    <TD>
                      {owner ? null : (
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <Button
                              variant="ghost"
                              size="icon-sm"
                              aria-label={t('actionsFor', { name: name(member) })}
                            >
                              <MoreHorizontal aria-hidden />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end">
                            {member.status === 'invited' ? (
                              <DropdownMenuItem
                                disabled={member.platformBlocked}
                                onSelect={() =>
                                  activationLink.mutate(member.id, {
                                    onSuccess: ({ activationUrl: url }) => setActivationUrl(url),
                                  })
                                }
                              >
                                <KeyRound aria-hidden /> {t('newActivationLink')}
                              </DropdownMenuItem>
                            ) : (
                              <DropdownMenuItem onSelect={() => toggleBlock(member)}>
                                {member.status === 'suspended' ? (
                                  <>
                                    <UserCheck aria-hidden /> {t('unblockInSchool')}
                                  </>
                                ) : (
                                  <>
                                    <UserX aria-hidden /> {t('blockInSchool')}
                                  </>
                                )}
                              </DropdownMenuItem>
                            )}
                          </DropdownMenuContent>
                        </DropdownMenu>
                      )}
                    </TD>
                  </TR>
                );
              })}
            </TBody>
          </Table>
        ) : null}
      </AdminTablePanel>
      <Dialog open={!!activationUrl} onOpenChange={(open) => !open && setActivationUrl(null)}>
        <DialogContent
          title={t('activationTitle')}
          description={t('activationHint')}
          closeLabel={tCommon('close')}
          className="max-w-xl"
        >
          {activationUrl ? <ActivationLink url={activationUrl} /> : null}
        </DialogContent>
      </Dialog>
    </>
  );
}

function MemberStatusBadges({ member }: { member: SchoolMember }) {
  const t = useTranslations('members');
  return (
    <>
      <Badge dot tone={STATUS_TONE[member.status]}>
        {t(`statuses.${member.status}`)}
      </Badge>
      {member.platformBlocked ? <Badge tone="danger">{t('platformBlocked')}</Badge> : null}
    </>
  );
}
