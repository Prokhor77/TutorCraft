'use client';
import { BookOpen, Files, Film, HardDrive } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { useState } from 'react';
import { AdminTablePanel } from '@/components/admin/admin-panel';
import { Badge } from '@/components/ui/badge';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { Progress } from '@/components/ui/progress';
import { SkeletonList } from '@/components/ui/skeleton';
import { StatCard } from '@/components/ui/stat-card';
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import {
  usePlatformCourseStorage,
  usePlatformStorage,
  usePlatformTenants,
} from '@/features/admin/use-admin';
import type { PlatformTenant, TenantStorage } from '@/lib/api/endpoints/platform';
import { cn } from '@/lib/utils/cn';
import { formatFileSize } from '@/lib/utils/format';
import { useAdminTenantStore } from '@/stores/admin-tenant-store';

const BYTES_PER_MB = 1024 * 1024;
const PERCENT = 100;
const QUOTA_WARNING_PERCENT = 90;

type SchoolRow = { tenant: PlatformTenant; usage: TenantStorage | undefined };

/**
 * How much space every school (tutor) takes: all uploads as counted against the quota, the estimated size of processed
 * video, and — for the school in focus — the breakdown by file purpose and by course.
 */
export default function AdminStoragePage() {
  const t = useTranslations('admin.storage');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const tenants = usePlatformTenants();
  const storage = usePlatformStorage();
  const headerTenantId = useAdminTenantStore((state) => state.tenantId);
  const [focusId, setFocusId] = useState<string | null>(null);
  const size = (bytes: number) => formatFileSize(bytes, locale);

  const usageById = new Map(storage.data?.map((usage) => [usage.tenantId, usage]));
  const rows: SchoolRow[] = (tenants.data ?? [])
    .map((tenant) => ({ tenant, usage: usageById.get(tenant.id) }))
    .sort((a, b) => (b.usage?.usedBytes ?? 0) - (a.usage?.usedBytes ?? 0));
  const maxUsed = Math.max(1, ...rows.map((row) => row.usage?.usedBytes ?? 0));
  const totals = (storage.data ?? []).reduce(
    (sum, usage) => ({
      used: sum.used + usage.usedBytes,
      files: sum.files + usage.filesCount,
      hls: sum.hls + usage.hlsBytes,
    }),
    { used: 0, files: 0, hls: 0 },
  );
  const focused =
    rows.find((row) => row.tenant.id === focusId) ??
    rows.find((row) => row.tenant.id === headerTenantId) ??
    rows[0];

  if (storage.isError || tenants.isError) {
    return (
      <ErrorState
        title={t('loadError')}
        retryLabel={tCommon('retry')}
        onRetry={() => void storage.refetch()}
      />
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="grid gap-4 sm:grid-cols-3">
        <StatCard
          label={t('totalUsed')}
          icon={HardDrive}
          value={storage.data ? size(totals.used) : '—'}
        />
        <StatCard
          label={t('totalFiles')}
          icon={Files}
          tone="success"
          value={storage.data ? new Intl.NumberFormat(locale).format(totals.files) : '—'}
        />
        <StatCard
          label={t('totalHls')}
          icon={Film}
          tone="warning"
          value={storage.data ? `≈ ${size(totals.hls)}` : '—'}
          footer={<p className="text-xs text-text-muted">{t('hlsHint')}</p>}
        />
      </div>

      <AdminTablePanel title={t('schoolsTitle')} description={t('schoolsHint')}>
        {storage.isLoading || tenants.isLoading ? (
          <div className="p-5 sm:p-6">
            <SkeletonList label={tCommon('loading')} />
          </div>
        ) : rows.length === 0 ? (
          <div className="p-5 sm:p-6">
            <EmptyState
              icon={HardDrive}
              title={t('emptySchools')}
              description={t('emptySchoolsText')}
            />
          </div>
        ) : (
          <Table>
            <THead>
              <tr>
                <TH>{t('school')}</TH>
                <TH className="hidden sm:table-cell">{t('files')}</TH>
                <TH className="min-w-48">{t('used')}</TH>
                <TH className="hidden lg:table-cell">{t('hls')}</TH>
              </tr>
            </THead>
            <TBody>
              {rows.map(({ tenant, usage }) => {
                const used = usage?.usedBytes ?? 0;
                const quota =
                  tenant.storageQuotaMb !== null ? tenant.storageQuotaMb * BYTES_PER_MB : null;
                const percent = quota ? (used / quota) * PERCENT : (used / maxUsed) * PERCENT;
                const active = tenant.id === focused?.tenant.id;
                return (
                  <TR
                    key={tenant.id}
                    className={cn('cursor-pointer', active && 'bg-primary-soft/40')}
                    onClick={() => setFocusId(tenant.id)}
                  >
                    <TD>
                      <button
                        type="button"
                        aria-pressed={active}
                        onClick={() => setFocusId(tenant.id)}
                        className="flex min-w-0 flex-col text-left focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20"
                      >
                        <span className="truncate font-semibold">{tenant.name}</span>
                        <span className="truncate text-xs text-text-muted">{tenant.slug}</span>
                      </button>
                    </TD>
                    <TD className="hidden whitespace-nowrap text-sm sm:table-cell">
                      {new Intl.NumberFormat(locale).format(usage?.filesCount ?? 0)}
                    </TD>
                    <TD>
                      <span className="flex flex-col gap-1.5">
                        <span className="flex flex-wrap items-baseline justify-between gap-x-2 text-sm">
                          <span className="font-semibold">{size(used)}</span>
                          <span className="text-xs text-text-muted">
                            {quota
                              ? t('ofQuota', {
                                  quota: size(quota),
                                  percent: Math.round(percent),
                                })
                              : t('noQuota')}
                          </span>
                        </span>
                        <Progress
                          value={percent}
                          label={t('usedOf', { name: tenant.name })}
                          tone={quota && percent >= QUOTA_WARNING_PERCENT ? 'danger' : 'primary'}
                        />
                      </span>
                    </TD>
                    <TD className="hidden whitespace-nowrap text-sm text-text-muted lg:table-cell">
                      {usage?.hlsBytes ? `≈ ${size(usage.hlsBytes)}` : '—'}
                    </TD>
                  </TR>
                );
              })}
            </TBody>
          </Table>
        )}
      </AdminTablePanel>

      {focused ? <SchoolBreakdown row={focused} /> : null}
    </div>
  );
}

function SchoolBreakdown({ row }: { row: SchoolRow }) {
  const t = useTranslations('admin.storage');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const courses = usePlatformCourseStorage(row.tenant.id);
  const size = (bytes: number) => formatFileSize(bytes, locale);
  const list = courses.data ?? [];
  const maxCourse = Math.max(1, ...list.map((course) => course.bytes));

  return (
    <AdminTablePanel
      title={t('breakdownTitle', { name: row.tenant.name })}
      description={t('coursesHint')}
      toolbar={
        row.usage?.byPurpose.length ? (
          <ul className="flex flex-wrap gap-2" aria-label={t('byPurpose')}>
            {row.usage.byPurpose.map((purpose) => (
              <li key={purpose.purpose}>
                <Badge tone="primary">
                  {t(`purposes.${purpose.purpose}`)}: {size(purpose.bytes)} ·{' '}
                  {t('filesCount', { count: purpose.files })}
                </Badge>
              </li>
            ))}
          </ul>
        ) : null
      }
    >
      {courses.isLoading ? (
        <div className="p-5 sm:p-6">
          <SkeletonList label={tCommon('loading')} />
        </div>
      ) : courses.isError ? (
        <div className="p-5 sm:p-6">
          <ErrorState
            title={t('coursesLoadError')}
            retryLabel={tCommon('retry')}
            onRetry={() => void courses.refetch()}
          />
        </div>
      ) : list.length === 0 ? (
        <div className="p-5 sm:p-6">
          <EmptyState
            icon={BookOpen}
            title={t('emptyCourses')}
            description={t('emptyCoursesText')}
          />
        </div>
      ) : (
        <Table>
          <THead>
            <tr>
              <TH>{t('course')}</TH>
              <TH className="hidden sm:table-cell">{t('files')}</TH>
              <TH className="min-w-48">{t('used')}</TH>
              <TH className="hidden lg:table-cell">{t('hls')}</TH>
            </tr>
          </THead>
          <TBody>
            {list.map((course) => (
              <TR key={course.courseId}>
                <TD>
                  <span className="flex min-w-0 flex-wrap items-center gap-2">
                    <span className="truncate font-semibold">{course.title}</span>
                    {course.inTrash ? <Badge tone="warning">{t('inTrash')}</Badge> : null}
                  </span>
                </TD>
                <TD className="hidden whitespace-nowrap text-sm sm:table-cell">
                  {new Intl.NumberFormat(locale).format(course.filesCount)}
                </TD>
                <TD>
                  <span className="flex flex-col gap-1.5">
                    <span className="text-sm font-semibold">{size(course.bytes)}</span>
                    <Progress
                      value={(course.bytes / maxCourse) * PERCENT}
                      label={t('usedOf', { name: course.title })}
                    />
                  </span>
                </TD>
                <TD className="hidden whitespace-nowrap text-sm text-text-muted lg:table-cell">
                  {course.hlsBytes ? `≈ ${size(course.hlsBytes)}` : '—'}
                </TD>
              </TR>
            ))}
          </TBody>
        </Table>
      )}
    </AdminTablePanel>
  );
}
