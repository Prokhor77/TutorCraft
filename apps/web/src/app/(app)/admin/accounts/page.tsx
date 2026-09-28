'use client';
import { Ban, MoreHorizontal, Search, ShieldCheck, Trash2, UserCog } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { useDeferredValue, useState } from 'react';
import { AccountOriginCell } from '@/components/admin/account-origin';
import { AdminTablePanel, FilterChips, PersonCell } from '@/components/admin/admin-panel';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogFooter } from '@/components/ui/dialog';
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
import { LoadMore } from '@/components/ui/load-more';
import { SkeletonList } from '@/components/ui/skeleton';
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { toast } from '@/components/ui/toast';
import { usePlatformTenants } from '@/features/admin/use-admin';
import { usePlatformUserMutations, usePlatformUsers } from '@/features/members/use-members';
import { flattenPages } from '@/lib/api/pagination';
import {
  ACCOUNT_ORIGINS,
  MEMBER_STATUS_FILTERS,
  type PlatformUser,
} from '@/lib/api/schemas/members';
import { formatDateTime, formatRelative } from '@/lib/utils/format';

const STATUS_TONE = { active: 'success', suspended: 'warning', invited: 'info' } as const;
const MAX_REASON_LENGTH = 500;

type PendingAction = { kind: 'block' | 'erase'; user: PlatformUser } | null;

/**
 * Every account on the platform: school, who created it and how, school status and platform block.
 * The platform administrator blocks / unblocks (tutors cannot lift it) or deletes an account completely.
 */
export default function AdminAccountsPage() {
  const t = useTranslations('members');
  const tAdmin = useTranslations('admin');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('');
  const [origin, setOrigin] = useState('');
  const [tenantId, setTenantId] = useState('');
  const [pending, setPending] = useState<PendingAction>(null);
  const deferredQuery = useDeferredValue(query.trim());
  const tenants = usePlatformTenants();
  const users = usePlatformUsers({
    q: deferredQuery || undefined,
    status: status || undefined,
    origin: origin || undefined,
    tenantId: tenantId || undefined,
  });
  const { unblock } = usePlatformUserMutations();
  const rows = flattenPages(users.data?.pages);
  const name = (user: PlatformUser) => `${user.firstName} ${user.lastName}`;

  return (
    <>
      <AdminTablePanel
        title={tAdmin('nav.accounts')}
        count={
          rows.length > 0 ? (
            <Badge tone="primary">{t('shownCount', { count: rows.length })}</Badge>
          ) : null
        }
        description={t('platformPanelHint')}
        toolbar={
          <div className="flex flex-col gap-3">
            <div className="flex flex-col gap-2 md:flex-row">
              <div className="relative md:flex-1">
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
              <NativeSelect
                aria-label={t('school')}
                value={tenantId}
                onChange={(event) => setTenantId(event.target.value)}
                className="h-10 rounded-full border-transparent bg-surface-muted md:w-56"
              >
                <option value="">{t('allSchools')}</option>
                {tenants.data?.map((tenant) => (
                  <option key={tenant.id} value={tenant.id}>
                    {tenant.name}
                  </option>
                ))}
              </NativeSelect>
              <NativeSelect
                aria-label={t('origin')}
                value={origin}
                onChange={(event) => setOrigin(event.target.value)}
                className="h-10 rounded-full border-transparent bg-surface-muted md:w-56"
              >
                <option value="">{t('allOrigins')}</option>
                {ACCOUNT_ORIGINS.map((option) => (
                  <option key={option} value={option}>
                    {t(`origins.${option}`)}
                  </option>
                ))}
              </NativeSelect>
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
            hasMore={!!users.hasNextPage}
            loading={users.isFetchingNextPage}
            onClick={() => void users.fetchNextPage()}
            label={tCommon('loadMore')}
          />
        }
      >
        {users.isLoading ? (
          <div className="p-5 sm:p-6">
            <SkeletonList label={tCommon('loading')} />
          </div>
        ) : null}
        {users.isError ? (
          <div className="p-5 sm:p-6">
            <ErrorState
              title={t('loadError')}
              retryLabel={tCommon('retry')}
              onRetry={() => void users.refetch()}
            />
          </div>
        ) : null}
        {users.isSuccess && rows.length === 0 ? (
          <div className="p-5 sm:p-6">
            <EmptyState
              icon={UserCog}
              title={t('emptyTitle')}
              description={t('platformEmptyText')}
            />
          </div>
        ) : null}
        {rows.length > 0 ? (
          <Table>
            <THead>
              <tr>
                <TH>{t('user')}</TH>
                <TH className="hidden md:table-cell">{t('school')}</TH>
                <TH className="hidden lg:table-cell">{t('origin')}</TH>
                <TH className="hidden sm:table-cell">{t('status')}</TH>
                <TH className="hidden xl:table-cell">{t('lastLogin')}</TH>
                <TH className="w-10">
                  <span className="sr-only">{t('actions')}</span>
                </TH>
              </tr>
            </THead>
            <TBody>
              {rows.map((user) => {
                const platformAdmin = user.tenantRoles.includes('platform_admin');
                const owner = user.tenantRoles.includes('tenant_admin');
                return (
                  <TR key={user.id}>
                    <TD className="sm:min-w-56">
                      <PersonCell
                        name={name(user)}
                        secondary={user.email}
                        extra={
                          <span className="mt-1 flex flex-wrap gap-1 empty:hidden md:hidden">
                            <span className="text-xs text-text-muted">{user.school.name}</span>
                          </span>
                        }
                      />
                    </TD>
                    <TD className="hidden md:table-cell">
                      <span className="flex min-w-0 flex-col">
                        <span className="truncate text-sm font-medium">{user.school.name}</span>
                        {owner || platformAdmin ? (
                          <span className="text-xs text-text-muted">
                            {t(platformAdmin ? 'rolePlatformAdmin' : 'roleOwner')}
                          </span>
                        ) : null}
                      </span>
                    </TD>
                    <TD className="hidden lg:table-cell">
                      <AccountOriginCell origin={user.origin} createdBy={user.createdBy} />
                    </TD>
                    <TD className="hidden sm:table-cell">
                      <span className="flex flex-wrap gap-1">
                        <Badge dot tone={STATUS_TONE[user.status]}>
                          {t(`statuses.${user.status}`)}
                        </Badge>
                        {user.platformBlock ? (
                          <Badge
                            tone="danger"
                            title={[
                              formatDateTime(user.platformBlock.at, locale),
                              user.platformBlock.reason,
                            ]
                              .filter(Boolean)
                              .join(' · ')}
                          >
                            {t('platformBlocked')}
                          </Badge>
                        ) : null}
                      </span>
                    </TD>
                    <TD className="hidden whitespace-nowrap text-xs text-text-muted xl:table-cell">
                      {user.lastLoginAt ? formatRelative(user.lastLoginAt, locale) : t('never')}
                    </TD>
                    <TD>
                      {platformAdmin ? null : (
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <Button
                              variant="ghost"
                              size="icon-sm"
                              aria-label={t('actionsFor', { name: name(user) })}
                            >
                              <MoreHorizontal aria-hidden />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end">
                            {user.platformBlock ? (
                              <DropdownMenuItem
                                onSelect={() =>
                                  unblock.mutate(user.id, {
                                    onSuccess: () =>
                                      toast({
                                        tone: 'success',
                                        title: t('unblockedToast', { name: name(user) }),
                                      }),
                                  })
                                }
                              >
                                <ShieldCheck aria-hidden /> {t('unblockOnPlatform')}
                              </DropdownMenuItem>
                            ) : (
                              <DropdownMenuItem
                                onSelect={() => setPending({ kind: 'block', user })}
                              >
                                <Ban aria-hidden /> {t('blockOnPlatform')}
                              </DropdownMenuItem>
                            )}
                            <DropdownMenuItem
                              disabled={owner}
                              onSelect={() => setPending({ kind: 'erase', user })}
                            >
                              <Trash2 aria-hidden /> {t(owner ? 'eraseOwnerDisabled' : 'erase')}
                            </DropdownMenuItem>
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
      <BlockDialog
        user={pending?.kind === 'block' ? pending.user : null}
        onClose={() => setPending(null)}
      />
      <EraseDialog
        user={pending?.kind === 'erase' ? pending.user : null}
        onClose={() => setPending(null)}
      />
    </>
  );
}

function BlockDialog({ user, onClose }: { user: PlatformUser | null; onClose: () => void }) {
  const t = useTranslations('members');
  const tCommon = useTranslations('common');
  const [reason, setReason] = useState('');
  const { block } = usePlatformUserMutations();
  const close = () => {
    setReason('');
    onClose();
  };
  return (
    <Dialog open={!!user} onOpenChange={(open) => !open && close()}>
      <DialogContent
        title={t('blockTitle', { name: user ? `${user.firstName} ${user.lastName}` : '' })}
        description={t('blockHint')}
        closeLabel={tCommon('close')}
      >
        <Field label={t('blockReason')} hint={t('blockReasonHint')}>
          <Input
            value={reason}
            maxLength={MAX_REASON_LENGTH}
            onChange={(event) => setReason(event.target.value)}
          />
        </Field>
        <DialogFooter>
          <Button variant="secondary" onClick={close}>
            {tCommon('cancel')}
          </Button>
          <Button
            variant="danger"
            loading={block.isPending}
            onClick={() =>
              user &&
              block.mutate(
                { id: user.id, reason: reason.trim() || undefined },
                {
                  onSuccess: () => {
                    toast({
                      tone: 'success',
                      title: t('blockedToast', { name: `${user.firstName} ${user.lastName}` }),
                    });
                    close();
                  },
                },
              )
            }
          >
            <Ban aria-hidden /> {t('blockOnPlatform')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function EraseDialog({ user, onClose }: { user: PlatformUser | null; onClose: () => void }) {
  const t = useTranslations('members');
  const tCommon = useTranslations('common');
  const [confirmation, setConfirmation] = useState('');
  const { erase } = usePlatformUserMutations();
  const close = () => {
    setConfirmation('');
    onClose();
  };
  const confirmed = !!user && confirmation.trim().toLowerCase() === user.email.toLowerCase();
  return (
    <Dialog open={!!user} onOpenChange={(open) => !open && close()}>
      <DialogContent
        title={t('eraseTitle', { name: user ? `${user.firstName} ${user.lastName}` : '' })}
        closeLabel={tCommon('close')}
      >
        <Alert tone="danger" title={t('eraseWarningTitle')}>
          {t('eraseWarning')}
        </Alert>
        <Field label={t('eraseConfirm', { email: user?.email ?? '' })}>
          <Input
            value={confirmation}
            autoComplete="off"
            onChange={(event) => setConfirmation(event.target.value)}
          />
        </Field>
        <DialogFooter>
          <Button variant="secondary" onClick={close}>
            {tCommon('cancel')}
          </Button>
          <Button
            variant="danger"
            disabled={!confirmed}
            loading={erase.isPending}
            onClick={() =>
              user &&
              erase.mutate(user.id, {
                onSuccess: () => {
                  toast({ tone: 'success', title: t('erasedToast', { email: user.email }) });
                  close();
                },
              })
            }
          >
            <Trash2 aria-hidden /> {t('erase')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
