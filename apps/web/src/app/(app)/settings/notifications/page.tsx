'use client';
import { useTranslations } from 'next-intl';
import { useEffect, useState } from 'react';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { ErrorState } from '@/components/ui/error-state';
import { PageHeader, Panel } from '@/components/ui/page-header';
import { SkeletonList } from '@/components/ui/skeleton';
import { SettingsTabs } from '@/components/settings/settings-tabs';
import { TelegramConnect } from '@/components/settings/telegram-connect';
import { toast } from '@/components/ui/toast';
import { useMe } from '@/features/auth/use-auth';
import {
  useNotificationPreferences,
  useSaveNotificationPreferences,
} from '@/features/notifications/use-notifications';
import {
  NOTIFICATION_CATEGORIES,
  NOTIFICATION_CHANNELS,
  type NotificationPreferences,
} from '@/lib/api/schemas/me';
import { cn } from '@/lib/utils/cn';

/** FR-NOTIF-02: one screen — rows = categories, columns = channels; Telegram linking (FR-NOTIF-HYB-01). */
export default function NotificationSettingsPage() {
  const t = useTranslations('notificationSettings');
  const tCommon = useTranslations('common');
  const me = useMe();
  const prefs = useNotificationPreferences();
  const save = useSaveNotificationPreferences();
  const [matrix, setMatrix] = useState<NotificationPreferences['matrix'] | null>(null);
  useEffect(() => {
    if (prefs.data) setMatrix(prefs.data.matrix);
  }, [prefs.data]);

  const header = (
    <PageHeader title={t('title')} description={t('description')}>
      <SettingsTabs />
    </PageHeader>
  );

  if (prefs.isLoading || !matrix)
    return (
      <>
        {header}
        {prefs.isError ? (
          <ErrorState
            title={t('loadError')}
            retryLabel={tCommon('retry')}
            onRetry={() => void prefs.refetch()}
          />
        ) : (
          <SkeletonList label={tCommon('loading')} />
        )}
      </>
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
      {header}
      <TelegramConnect />
      {!me?.telegramLinked ? (
        <Alert tone="info" className="mb-4 md:mb-gutter" title={t('telegramDisabledColumn')} />
      ) : null}
      <Panel title={t('matrixTitle')}>
        <div className="-mx-1 hidden overflow-x-auto px-1 sm:block">
          <table className="w-full min-w-[32rem] border-separate border-spacing-y-1 text-sm">
            <caption className="sr-only">{t('title')}</caption>
            <thead className="text-label-md uppercase text-text-muted">
              <tr>
                <th
                  scope="col"
                  className="rounded-l-full bg-surface-muted px-4 py-2.5 text-left font-semibold"
                >
                  {t('category')}
                </th>
                {NOTIFICATION_CHANNELS.map((channel, index) => (
                  <th
                    key={channel}
                    scope="col"
                    className={cn(
                      'bg-surface-muted px-3 py-2.5 text-center font-semibold',
                      index === NOTIFICATION_CHANNELS.length - 1 && 'rounded-r-full',
                    )}
                  >
                    {t(`channels.${channel}`)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {NOTIFICATION_CATEGORIES.map((category) => (
                <tr key={category} className="group">
                  <th
                    scope="row"
                    className="rounded-l-md px-4 py-3 text-left font-normal transition-colors duration-fast group-hover:bg-surface-muted/50"
                  >
                    <span className="block font-semibold">{t(`categories.${category}.title`)}</span>
                    <span className="block text-xs text-text-muted">
                      {t(`categories.${category}.hint`)}
                    </span>
                  </th>
                  {NOTIFICATION_CHANNELS.map((channel, index) => (
                    <td
                      key={channel}
                      className={cn(
                        'px-3 py-3 text-center transition-colors duration-fast group-hover:bg-surface-muted/50',
                        index === NOTIFICATION_CHANNELS.length - 1 && 'rounded-r-md',
                      )}
                    >
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
        {/* Phones: one rounded card per event with labelled channel pills instead of a wide table. */}
        <ul className="flex flex-col gap-2 sm:hidden">
          {NOTIFICATION_CATEGORIES.map((category) => (
            <li
              key={category}
              className="flex flex-col gap-2.5 rounded-md bg-surface-muted/60 px-3 py-3"
            >
              <span className="flex flex-col">
                <span className="text-sm font-semibold">{t(`categories.${category}.title`)}</span>
                <span className="text-xs text-text-muted">{t(`categories.${category}.hint`)}</span>
              </span>
              <span className="flex flex-wrap gap-1.5">
                {NOTIFICATION_CHANNELS.map((channel) => {
                  const disabled = channel === 'telegram' && !me?.telegramLinked;
                  return (
                    <label
                      key={channel}
                      className={cn(
                        'inline-flex items-center gap-2 rounded-full bg-surface py-1.5 pl-2 pr-3 text-label-md shadow-sm',
                        disabled ? 'opacity-60' : 'cursor-pointer',
                      )}
                    >
                      <Checkbox
                        checked={matrix[category]?.[channel] ?? false}
                        disabled={disabled}
                        onCheckedChange={(checked) => toggle(category, channel, checked === true)}
                      />
                      {t(`channels.${channel}`)}
                    </label>
                  );
                })}
              </span>
            </li>
          ))}
        </ul>
        <div className="flex justify-end border-t border-border pt-4">
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
      </Panel>
    </>
  );
}
