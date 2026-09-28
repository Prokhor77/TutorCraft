'use client';
import { Receipt } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { AdminTablePanel, PersonCell } from '@/components/admin/admin-panel';
import { Badge, type BadgeTone } from '@/components/ui/badge';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { LoadMore } from '@/components/ui/load-more';
import { SkeletonList } from '@/components/ui/skeleton';
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { useOrders } from '@/features/admin/use-admin';
import { flattenPages } from '@/lib/api/pagination';
import type { Order } from '@/lib/api/schemas/billing';
import { formatDateTime } from '@/lib/utils/format';
import { formatMoney } from '@/lib/utils/money';

const STATUS_TONE: Record<Order['status'], BadgeTone> = {
  pending: 'warning',
  paid: 'success',
  failed: 'danger',
  refunded: 'info',
  canceled: 'neutral',
};

/** Course sales (FR-ENROL-09 / ADR-002). */
export default function AdminOrdersPage() {
  const t = useTranslations('adminOrders');
  const tAdmin = useTranslations('admin');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const orders = useOrders();
  const rows = flattenPages(orders.data?.pages);
  return (
    <AdminTablePanel
      title={tAdmin('nav.orders')}
      count={
        rows.length > 0 ? (
          <Badge tone="primary">{t('shownCount', { count: rows.length })}</Badge>
        ) : null
      }
      description={t('panelHint')}
      footer={
        <LoadMore
          hasMore={!!orders.hasNextPage}
          loading={orders.isFetchingNextPage}
          onClick={() => void orders.fetchNextPage()}
          label={tCommon('loadMore')}
        />
      }
    >
      {orders.isLoading ? (
        <div className="p-5 sm:p-6">
          <SkeletonList label={tCommon('loading')} />
        </div>
      ) : null}
      {orders.isError ? (
        <div className="p-5 sm:p-6">
          <ErrorState
            title={t('loadError')}
            retryLabel={tCommon('retry')}
            onRetry={() => void orders.refetch()}
          />
        </div>
      ) : null}
      {orders.isSuccess && rows.length === 0 ? (
        <div className="p-5 sm:p-6">
          <EmptyState icon={Receipt} title={t('emptyTitle')} description={t('emptyText')} />
        </div>
      ) : null}
      {rows.length > 0 ? (
        <Table>
          <THead>
            <tr>
              <TH>{t('buyer')}</TH>
              <TH>{t('course')}</TH>
              <TH className="text-right">{t('amount')}</TH>
              <TH>{t('status')}</TH>
              <TH className="hidden md:table-cell">{t('created')}</TH>
              <TH className="hidden lg:table-cell">{t('provider')}</TH>
            </tr>
          </THead>
          <TBody>
            {rows.map((order) => (
              <TR key={order.id}>
                <TD className="min-w-48">
                  <PersonCell
                    name={order.buyerName}
                    secondary={
                      <span className="md:hidden">{formatDateTime(order.createdAt, locale)}</span>
                    }
                  />
                </TD>
                <TD className="min-w-40 font-medium">{order.courseTitle}</TD>
                <TD className="whitespace-nowrap text-right font-heading text-base font-semibold tabular-nums">
                  {formatMoney(order.amount, locale)}
                </TD>
                <TD>
                  <Badge dot tone={STATUS_TONE[order.status]}>
                    {t(`statuses.${order.status}`)}
                  </Badge>
                </TD>
                <TD className="hidden whitespace-nowrap text-xs text-text-muted md:table-cell">
                  {formatDateTime(order.createdAt, locale)}
                </TD>
                <TD className="hidden lg:table-cell">
                  <span className="rounded-full bg-surface-muted px-2.5 py-1 text-label-md text-text-muted">
                    {order.provider}
                  </span>
                </TD>
              </TR>
            ))}
          </TBody>
        </Table>
      ) : null}
    </AdminTablePanel>
  );
}
