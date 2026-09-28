'use client';
import { Send } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useEffect, useState } from 'react';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { ErrorState } from '@/components/ui/error-state';
import { PageHeader } from '@/components/ui/page-header';
import { SkeletonList } from '@/components/ui/skeleton';
import { toast } from '@/components/ui/toast';
import { useMe } from '@/features/auth/use-auth';
import {
  useLinkTelegram,
  useNotificationPreferences,
  useSaveNotificationPreferences,
} from '@/features/notifications/use-notifications';
import {
  NOTIFICATION_CATEGORIES,
  NOTIFICATION_CHANNELS,
  type NotificationPreferences,
} from '@/lib/api/schemas/me';

/** FR-NOTIF-02: one screen — rows = categories, columns = channels; Telegram linking (FR-NOTIF-HYB-01). */
export default function NotificationSettingsPage() {
  const t = useTranslations('notificationSettings');
  const tCommon = useTranslations('common');
  const me = useMe();
  const prefs = useNotificationPreferences();
  const save = useSaveNotificationPreferences();
  const link = useLinkTelegram();
  const [matrix, setMatrix] = useState<NotificationPreferences['matrix'] | null>(null);
  useEffect(() => {
    if (prefs.data) setMatrix(prefs.data.matrix);
  }, [prefs.data]);

  if (prefs.isLoading || !matrix)
    return prefs.isError ? (
      <ErrorState
        title={t('loadError')}
        retryLabel={tCommon('retry')}
        onRetry={() => void prefs.refetch()}
      />
    ) : (
      <SkeletonList label={tCommon('loading')} />
    );

  const toggle = (
    category: (typeof NOTIFICATION_CATEGORIES)[number],
    channel: (typeof NOTIFICATION_CHANNELS)[number],
    value: boolean,
  ) =>
    setMatrix((current) =>
      current ? { ...current, [category]: { ...current[category], [channel]: value } } : current,
    );

  return (
    <>
      <PageHeader title={t('title')} description={t('description')} />
      <div className="mb-6 flex flex-col gap-3 rounded-lg border border-border bg-surface p-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-col gap-1">
          <span className="flex items-center gap-2 font-medium">
            Telegram{' '}
            {me?.telegramLinked ? (
              <Badge tone="success">{t('linked')}</Badge>
            ) : (
              <Badge>{t('notLinked')}</Badge>
            )}
          </span>
          <span className="text-sm text-text-muted">{t('telegramHint')}</span>
        </div>
        <Button
          variant={me?.telegramLinked ? 'secondary' : 'primary'}
          loading={link.isPending}
          onClick={() =>
            link.mutate(undefined, {
              onSuccess: ({ deepLink }) => window.open(deepLink, '_blank', 'noopener,noreferrer'),
            })
          }
        >
          <Send aria-hidden /> {me?.telegramLinked ? t('relink') : t('link')}
        </Button>
      </div>
      {!me?.telegramLinked ? (
        <Alert tone="info" className="mb-4" title={t('telegramDisabledColumn')} />
      ) : null}
      <div className="overflow-x-auto rounded-lg border border-border bg-surface">
        <table className="w-full text-sm">
          <caption className="sr-only">{t('title')}</caption>
          <thead className="bg-surface-muted text-xs uppercase text-text-muted">
            <tr>
              <th scope="col" className="px-4 py-3 text-left">
                {t('category')}
              </th>
              {NOTIFICATION_CHANNELS.map((channel) => (
                <th key={channel} scope="col" className="px-4 py-3 text-center">
                  {t(`channels.${channel}`)}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {NOTIFICATION_CATEGORIES.map((category) => (
              <tr key={category} className="border-t border-border">
                <th scope="row" className="px-4 py-3 text-left font-normal">
                  <span className="block font-medium">{t(`categories.${category}.title`)}</span>
                  <span className="block text-xs text-text-muted">
                    {t(`categories.${category}.hint`)}
                  </span>
                </th>
                {NOTIFICATION_CHANNELS.map((channel) => (
                  <td key={channel} className="px-4 py-3 text-center">
                    <Checkbox
                      aria-label={t('toggle', {
                        category: t(`categories.${category}.title`),
                        channel: t(`channels.${channel}`),
                      })}
                      checked={matrix[category]?.[channel] ?? false}
                      disabled={channel === 'telegram' && !me?.telegramLinked}
                      onCheckedChange={(checked) => toggle(category, channel, checked === true)}
                    />
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="mt-4 flex justify-end">
        <Button
          loading={save.isPending}
          onClick={() =>
            save.mutate(
              { matrix },
              { onSuccess: () => toast({ tone: 'success', title: t('saved') }) },
            )
          }
        >
          {tCommon('save')}
        </Button>
      </div>
    </>
  );
}
