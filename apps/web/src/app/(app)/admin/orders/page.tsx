'use client';
import { Receipt } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { Badge, type BadgeTone } from '@/components/ui/badge';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { LoadMore } from '@/components/ui/load-more';
import { SkeletonList } from '@/components/ui/skeleton';
import { Table, TableContainer, TBody, TD, TH, THead, TR } from '@/components/ui/table';
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
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const orders = useOrders();
  const rows = flattenPages(orders.data?.pages);
  return (
    <div className="flex flex-col gap-4">
      {orders.isLoading ? <SkeletonList label={tCommon('loading')} /> : null}
      {orders.isError ? (
        <ErrorState
          title={t('loadError')}
          retryLabel={tCommon('retry')}
          onRetry={() => void orders.refetch()}
        />
      ) : null}
      {orders.isSuccess && rows.length === 0 ? (
        <EmptyState icon={Receipt} title={t('emptyTitle')} description={t('emptyText')} />
      ) : null}
      {rows.length > 0 ? (
        <TableContainer>
          <Table>
            <THead>
              <tr>
                <TH>{t('created')}</TH>
                <TH>{t('course')}</TH>
                <TH>{t('buyer')}</TH>
                <TH>{t('amount')}</TH>
                <TH>{t('status')}</TH>
                <TH>{t('provider')}</TH>
              </tr>
            </THead>
            <TBody>
              {rows.map((order) => (
                <TR key={order.id}>
                  <TD className="whitespace-nowrap text-xs">
                    {formatDateTime(order.createdAt, locale)}
                  </TD>
                  <TD className="font-medium">{order.courseTitle}</TD>
                  <TD>{order.buyerName}</TD>
                  <TD className="tabular-nums">{formatMoney(order.amount, locale)}</TD>
                  <TD>
                    <Badge tone={STATUS_TONE[order.status]}>{t(`statuses.${order.status}`)}</Badge>
                  </TD>
                  <TD className="text-xs text-text-muted">{order.provider}</TD>
                </TR>
              ))}
            </TBody>
          </Table>
        </TableContainer>
      ) : null}
      <LoadMore
        hasMore={!!orders.hasNextPage}
        loading={orders.isFetchingNextPage}
        onClick={() => void orders.fetchNextPage()}
        label={tCommon('loadMore')}
      />
    </div>
  );
}
