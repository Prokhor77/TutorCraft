'use client';
import {
  BookOpen,
  Building2,
  MoreHorizontal,
  ScrollText,
  Search,
  Settings2,
  Trash2,
  Users,
} from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import { useDeferredValue, useState } from 'react';
import { AdminTablePanel, PersonCell } from '@/components/admin/admin-panel';
import { DeleteSchoolDialog, type SchoolToDelete } from '@/components/admin/delete-school-dialog';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { Input } from '@/components/ui/input';
import { SkeletonList } from '@/components/ui/skeleton';
import { StatCard } from '@/components/ui/stat-card';
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { usePlatformStorage, usePlatformTenants } from '@/features/admin/use-admin';
import { ROUTES } from '@/features/auth/routes';
import type { PlatformTenant } from '@/lib/api/endpoints/platform';
import { formatDate, formatFileSize } from '@/lib/utils/format';
import { useAdminTenantStore } from '@/stores/admin-tenant-store';

function matches(tenant: PlatformTenant, query: string): boolean {
  if (!query) return true;
  const owner = tenant.owner;
  const ownerName = owner ? `${owner.firstName} ${owner.lastName}` : '';
  return [tenant.name, tenant.slug, owner?.email ?? '', ownerName].some((value) =>
    value.toLowerCase().includes(query),
  );
}

/**
 * Every school on the platform: who it is registered to, how many people and courses it has and how much space it
 * takes. From here the administrator opens a school's management or logs, or deletes it with all its accounts.
 */
export default function AdminSchoolsPage() {
  const t = useTranslations('admin.schools');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const router = useRouter();
  const [query, setQuery] = useState('');
  const [pending, setPending] = useState<SchoolToDelete | null>(null);
  const deferredQuery = useDeferredValue(query.trim().toLowerCase());
  const tenants = usePlatformTenants();
  const storage = usePlatformStorage();
  const setTenantId = useAdminTenantStore((state) => state.setTenantId);
  const setLogScope = useAdminTenantStore((state) => state.setLogScope);
  const all = tenants.data ?? [];
  const rows = all.filter((tenant) => matches(tenant, deferredQuery));
  const usedBytes = new Map(storage.data?.map((usage) => [usage.tenantId, usage.usedBytes]));
  const number = (value: number) => new Intl.NumberFormat(locale).format(value);
  const totals = all.reduce(
    (sum, tenant) => ({
      users: sum.users + tenant.usersCount,
      courses: sum.courses + tenant.coursesCount,
    }),
    { users: 0, courses: 0 },
  );

  const open = (tenant: PlatformTenant, href: string) => {
    setTenantId(tenant.id);
    router.push(href);
  };

  return (
    <div className="flex flex-col gap-4 md:gap-gutter">
      <div className="grid gap-4 sm:grid-cols-3">
        <StatCard
          label={t('statSchools')}
          icon={Building2}
          value={tenants.data ? number(all.length) : '—'}
        />
        <StatCard
          label={t('statUsers')}
          icon={Users}
          tone="success"
          value={tenants.data ? number(totals.users) : '—'}
        />
        <StatCard
          label={t('statCourses')}
          icon={BookOpen}
          tone="warning"
          value={tenants.data ? number(totals.courses) : '—'}
        />
      </div>
      <AdminTablePanel
        title={t('title')}
        count={
          rows.length > 0 ? (
            <Badge tone="primary">{t('shownCount', { count: rows.length })}</Badge>
          ) : null
        }
        description={t('hint')}
        toolbar={
          <div className="relative md:max-w-md">
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
        }
      >
        {tenants.isLoading ? (
          <div className="p-5 sm:p-6">
            <SkeletonList label={tCommon('loading')} />
          </div>
        ) : null}
        {tenants.isError ? (
          <div className="p-5 sm:p-6">
            <ErrorState
              title={t('loadError')}
              retryLabel={tCommon('retry')}
              onRetry={() => void tenants.refetch()}
            />
          </div>
        ) : null}
        {tenants.isSuccess && rows.length === 0 ? (
          <div className="p-5 sm:p-6">
            <EmptyState
              icon={Building2}
              title={deferredQuery ? t('nothingFound') : t('emptyTitle')}
              description={deferredQuery ? t('nothingFoundText') : t('emptyText')}
            />
          </div>
        ) : null}
        {rows.length > 0 ? (
          <Table>
            <THead>
              <tr>
                <TH>{t('school')}</TH>
                <TH className="hidden md:table-cell">{t('owner')}</TH>
                <TH className="hidden sm:table-cell">{t('users')}</TH>
                <TH className="hidden sm:table-cell">{t('courses')}</TH>
                <TH className="hidden lg:table-cell">{t('storage')}</TH>
                <TH className="hidden xl:table-cell">{t('created')}</TH>
                <TH className="w-10">
                  <span className="sr-only">{t('actions')}</span>
                </TH>
              </tr>
            </THead>
            <TBody>
              {rows.map((tenant) => {
                const owner = tenant.owner;
                const ownerName = owner ? `${owner.firstName} ${owner.lastName}` : null;
                const used = usedBytes.get(tenant.id);
                return (
                  <TR key={tenant.id}>
                    <TD className="min-w-48">
                      <span className="flex min-w-0 flex-col">
                        <span className="flex flex-wrap items-center gap-2">
                          <span className="truncate font-semibold">{tenant.name}</span>
                          {tenant.status === 'suspended' ? (
                            <Badge tone="warning">{t('suspended')}</Badge>
                          ) : null}
                        </span>
                        <span className="truncate font-mono text-xs text-text-muted">
                          {tenant.slug}
                        </span>
                        <span className="truncate text-xs text-text-muted md:hidden">
                          {owner?.email ?? t('noOwner')}
                        </span>
                      </span>
                    </TD>
                    <TD className="hidden md:table-cell">
                      {owner && ownerName ? (
                        <PersonCell name={ownerName} secondary={owner.email} />
                      ) : (
                        <span className="text-text-muted">{t('noOwner')}</span>
                      )}
                    </TD>
                    <TD className="hidden tabular-nums sm:table-cell">
                      {number(tenant.usersCount)}
                    </TD>
                    <TD className="hidden tabular-nums sm:table-cell">
                      {number(tenant.coursesCount)}
                    </TD>
                    <TD className="hidden whitespace-nowrap text-sm lg:table-cell">
                      {used === undefined ? '—' : formatFileSize(used, locale)}
                    </TD>
                    <TD className="hidden whitespace-nowrap text-xs text-text-muted xl:table-cell">
                      {formatDate(tenant.createdAt, locale)}
                    </TD>
                    <TD>
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button
                            variant="ghost"
                            size="icon-sm"
                            aria-label={t('actionsFor', { name: tenant.name })}
                          >
                            <MoreHorizontal aria-hidden />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem onSelect={() => open(tenant, ROUTES.adminUsers)}>
                            <Settings2 aria-hidden /> {t('manage')}
                          </DropdownMenuItem>
                          <DropdownMenuItem
                            onSelect={() =>
                              router.push(
                                `${ROUTES.adminCourses}?school=${encodeURIComponent(tenant.id)}`,
                              )
                            }
                          >
                            <BookOpen aria-hidden /> {t('coursesAction')}
                          </DropdownMenuItem>
                          <DropdownMenuItem
                            onSelect={() => {
                              setLogScope('school');
                              open(tenant, ROUTES.adminAudit);
                            }}
                          >
                            <ScrollText aria-hidden /> {t('logs')}
                          </DropdownMenuItem>
                          <DropdownMenuSeparator />
                          <DropdownMenuItem
                            className="text-danger focus:text-danger"
                            onSelect={() =>
                              setPending({
                                id: tenant.id,
                                slug: tenant.slug,
                                name: tenant.name,
                                ownerEmail: owner?.email,
                              })
                            }
                          >
                            <Trash2 aria-hidden /> {t('delete')}
                          </DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </TD>
                  </TR>
                );
              })}
            </TBody>
          </Table>
        ) : null}
      </AdminTablePanel>
      <DeleteSchoolDialog school={pending} onClose={() => setPending(null)} />
    </div>
  );
}
