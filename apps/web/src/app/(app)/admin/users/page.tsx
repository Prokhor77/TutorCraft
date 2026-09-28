'use client';
import {
  MailPlus,
  MoreHorizontal,
  Search,
  ShieldCheck,
  ShieldOff,
  UserCheck,
  UserX,
  Users,
} from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { useDeferredValue, useState } from 'react';
import { CreateUserDialog } from '@/components/admin/create-user-dialog';
import { ImportUsersDialog } from '@/components/admin/import-users-dialog';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { Input, NativeSelect } from '@/components/ui/input';
import { LoadMore } from '@/components/ui/load-more';
import { SkeletonList } from '@/components/ui/skeleton';
import { Table, TableContainer, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { toast } from '@/components/ui/toast';
import { useUserMutations, useUsers } from '@/features/admin/use-admin';
import { flattenPages } from '@/lib/api/pagination';
import { USER_STATUSES, type UserSummary } from '@/lib/api/schemas/org';
import { formatRelative } from '@/lib/utils/format';

const STATUS_TONE = { active: 'success', suspended: 'warning', invited: 'info' } as const;

/** FR-USER-01..03: users with search/filters, create/invite, suspend, CSV import. */
export default function AdminUsersPage() {
  const t = useTranslations('adminUsers');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('');
  const deferredQuery = useDeferredValue(query.trim());
  const users = useUsers({ q: deferredQuery || undefined, status: status || undefined });
  const { update, resendInvite } = useUserMutations();
  const rows = flattenPages(users.data?.pages);
  const isAdmin = (user: UserSummary) => user.tenantRoles.includes('tenant_admin');

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-2 md:flex-row md:items-center">
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
          aria-label={t('status')}
          value={status}
          onChange={(event) => setStatus(event.target.value)}
          className="md:w-44"
        >
          <option value="">{t('allStatuses')}</option>
          {USER_STATUSES.map((option) => (
            <option key={option} value={option}>
              {t(`statuses.${option}`)}
            </option>
          ))}
        </NativeSelect>
        <div className="flex gap-2">
          <ImportUsersDialog />
          <CreateUserDialog />
        </div>
      </div>
      {users.isLoading ? <SkeletonList label={tCommon('loading')} /> : null}
      {users.isError ? (
        <ErrorState
          title={t('loadError')}
          retryLabel={tCommon('retry')}
          onRetry={() => void users.refetch()}
        />
      ) : null}
      {users.isSuccess && rows.length === 0 ? (
        <EmptyState
          icon={Users}
          title={t('emptyTitle')}
          description={t('emptyText')}
          action={<CreateUserDialog />}
        />
      ) : null}
      {rows.length > 0 ? (
        <TableContainer>
          <Table>
            <THead>
              <tr>
                <TH>{t('name')}</TH>
                <TH>{t('email')}</TH>
                <TH>{t('status')}</TH>
                <TH>{t('roles')}</TH>
                <TH>{t('lastLogin')}</TH>
                <TH className="w-10">
                  <span className="sr-only">{t('actions')}</span>
                </TH>
              </tr>
            </THead>
            <TBody>
              {rows.map((user) => (
                <TR key={user.id}>
                  <TD className="font-medium">
                    {user.firstName} {user.lastName}
                  </TD>
                  <TD className="text-text-muted">{user.email}</TD>
                  <TD>
                    <Badge tone={STATUS_TONE[user.status]}>{t(`statuses.${user.status}`)}</Badge>
                  </TD>
                  <TD className="text-xs">
                    {user.tenantRoles.map((role) => t(`tenantRoles.${role}`)).join(', ') || '—'}
                  </TD>
                  <TD className="text-xs text-text-muted">
                    {user.lastLoginAt ? formatRelative(user.lastLoginAt, locale) : t('never')}
                  </TD>
                  <TD>
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button
                          variant="ghost"
                          size="icon-sm"
                          aria-label={t('actionsFor', {
                            name: `${user.firstName} ${user.lastName}`,
                          })}
                        >
                          <MoreHorizontal aria-hidden />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        {user.status === 'suspended' ? (
                          <DropdownMenuItem
                            onSelect={() =>
                              update.mutate({ id: user.id, patch: { status: 'active' } })
                            }
                          >
                            <UserCheck aria-hidden /> {t('activate')}
                          </DropdownMenuItem>
                        ) : (
                          <DropdownMenuItem
                            onSelect={() =>
                              update.mutate({ id: user.id, patch: { status: 'suspended' } })
                            }
                          >
                            <UserX aria-hidden /> {t('suspend')}
                          </DropdownMenuItem>
                        )}
                        <DropdownMenuItem
                          onSelect={() =>
                            update.mutate({
                              id: user.id,
                              patch: {
                                tenantRoles: isAdmin(user)
                                  ? user.tenantRoles.filter((role) => role !== 'tenant_admin')
                                  : [...user.tenantRoles, 'tenant_admin'],
                              },
                            })
                          }
                        >
                          {isAdmin(user) ? <ShieldOff aria-hidden /> : <ShieldCheck aria-hidden />}{' '}
                          {isAdmin(user) ? t('revokeAdmin') : t('grantAdmin')}
                        </DropdownMenuItem>
                        {user.status === 'invited' ? (
                          <DropdownMenuItem
                            onSelect={() =>
                              resendInvite.mutate(user.id, {
                                onSuccess: () => toast({ tone: 'success', title: t('inviteSent') }),
                              })
                            }
                          >
                            <MailPlus aria-hidden /> {t('resendInvite')}
                          </DropdownMenuItem>
                        ) : null}
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </TD>
                </TR>
              ))}
            </TBody>
          </Table>
        </TableContainer>
      ) : null}
      <LoadMore
        hasMore={!!users.hasNextPage}
        loading={users.isFetchingNextPage}
        onClick={() => void users.fetchNextPage()}
        label={tCommon('loadMore')}
      />
    </div>
  );
}
