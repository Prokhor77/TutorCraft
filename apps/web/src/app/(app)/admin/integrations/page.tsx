'use client';
import { Copy, KeyRound, Plus, Trash2, Webhook } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { useState } from 'react';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogContent, DialogFooter, DialogTrigger } from '@/components/ui/dialog';
import { EmptyState } from '@/components/ui/empty-state';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Panel } from '@/components/ui/page-header';
import { Sheet, SheetContent } from '@/components/ui/dialog';
import { toast } from '@/components/ui/toast';
import { useDeliveries, useIntegrations } from '@/features/admin/use-admin';
import { flattenPages } from '@/lib/api/pagination';
import { TOKEN_SCOPES, WEBHOOK_EVENTS } from '@/lib/api/schemas/integrations';
import { copyToClipboard } from '@/lib/utils/download';
import { formatDateTime } from '@/lib/utils/format';

function SecretOnce({ value }: { value: string }) {
  const t = useTranslations('adminIntegrations');
  return (
    <div className="flex flex-col gap-2">
      <Alert tone="warning" title={t('shownOnce')} />
      <div className="flex gap-2">
        <Input
          readOnly
          value={value}
          aria-label={t('secret')}
          className="font-mono"
          onFocus={(event) => event.target.select()}
        />
        <Button
          onClick={() =>
            void copyToClipboard(value).then((ok) =>
              toast({ tone: ok ? 'success' : 'error', title: ok ? t('copied') : t('copyFailed') }),
            )
          }
        >
          <Copy aria-hidden /> {t('copy')}
        </Button>
      </div>
    </div>
  );
}

function ToggleList<T extends string>({
  options,
  value,
  onChange,
  labelOf,
}: {
  options: readonly T[];
  value: T[];
  onChange: (value: T[]) => void;
  labelOf: (option: T) => string;
}) {
  return (
    <div className="flex flex-wrap gap-3">
      {options.map((option) => (
        <label key={option} className="flex items-center gap-2 text-sm">
          <Checkbox
            checked={value.includes(option)}
            onCheckedChange={(checked) =>
              onChange(checked ? [...value, option] : value.filter((entry) => entry !== option))
            }
          />
          {labelOf(option)}
        </label>
      ))}
    </div>
  );
}

function CreateTokenDialog() {
  const t = useTranslations('adminIntegrations');
  const tCommon = useTranslations('common');
  const { createToken } = useIntegrations();
  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  const [scopes, setScopes] = useState<(typeof TOKEN_SCOPES)[number][]>(['read']);
  const [secret, setSecret] = useState<string | null>(null);
  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        if (!next) setSecret(null);
      }}
    >
      <DialogTrigger asChild>
        <Button size="sm">
          <Plus aria-hidden /> {t('newToken')}
        </Button>
      </DialogTrigger>
      <DialogContent title={t('newToken')} closeLabel={tCommon('close')}>
        {secret ? (
          <SecretOnce value={secret} />
        ) : (
          <form
            className="flex flex-col gap-4"
            onSubmit={(event) => {
              event.preventDefault();
              if (name.trim())
                createToken.mutate(
                  { name: name.trim(), scopes },
                  { onSuccess: ({ token }) => setSecret(token) },
                );
            }}
          >
            <Field label={t('tokenName')} required>
              <Input value={name} onChange={(event) => setName(event.target.value)} autoFocus />
            </Field>
            <ToggleList
              options={TOKEN_SCOPES}
              value={scopes}
              onChange={setScopes}
              labelOf={(scope) => t(`scopes.${scope}`)}
            />
            <DialogFooter>
              <Button type="submit" loading={createToken.isPending} disabled={scopes.length === 0}>
                {tCommon('create')}
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}

function CreateWebhookDialog() {
  const t = useTranslations('adminIntegrations');
  const tCommon = useTranslations('common');
  const { createWebhook } = useIntegrations();
  const [open, setOpen] = useState(false);
  const [url, setUrl] = useState('https://');
  const [events, setEvents] = useState<(typeof WEBHOOK_EVENTS)[number][]>([...WEBHOOK_EVENTS]);
  const [secret, setSecret] = useState<string | null>(null);
  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        if (!next) setSecret(null);
      }}
    >
      <DialogTrigger asChild>
        <Button size="sm">
          <Plus aria-hidden /> {t('newWebhook')}
        </Button>
      </DialogTrigger>
      <DialogContent
        title={t('newWebhook')}
        description={t('webhookHint')}
        closeLabel={tCommon('close')}
      >
        {secret ? (
          <SecretOnce value={secret} />
        ) : (
          <form
            className="flex flex-col gap-4"
            onSubmit={(event) => {
              event.preventDefault();
              createWebhook.mutate(
                { url, events },
                { onSuccess: ({ secret: created }) => setSecret(created) },
              );
            }}
          >
            <Field label={t('url')} required>
              <Input type="url" value={url} onChange={(event) => setUrl(event.target.value)} />
            </Field>
            <ToggleList
              options={WEBHOOK_EVENTS}
              value={events}
              onChange={setEvents}
              labelOf={(event) => event}
            />
            <DialogFooter>
              <Button
                type="submit"
                loading={createWebhook.isPending}
                disabled={events.length === 0}
              >
                {tCommon('create')}
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}

function DeliveriesSheet({
  webhookId,
  onClose,
}: {
  webhookId: string | null;
  onClose: () => void;
}) {
  const t = useTranslations('adminIntegrations');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const deliveries = useDeliveries(webhookId);
  return (
    <Sheet open={!!webhookId} onOpenChange={(open) => !open && onClose()}>
      <SheetContent title={t('deliveries')} closeLabel={tCommon('close')}>
        <ul className="flex flex-col gap-2">
          {flattenPages(deliveries.data?.pages).map((delivery) => (
            <li
              key={delivery.id}
              className="flex flex-col gap-1 rounded-md bg-surface-muted px-4 py-3 text-sm"
            >
              <span className="flex items-center justify-between">
                <span className="font-mono text-xs">{delivery.event}</span>
                <Badge
                  tone={
                    delivery.status === 'succeeded'
                      ? 'success'
                      : delivery.status === 'failed'
                        ? 'danger'
                        : 'warning'
                  }
                >
                  {t(`deliveryStatuses.${delivery.status}`)}
                </Badge>
              </span>
              <span className="text-xs text-text-muted">
                {formatDateTime(delivery.createdAt, locale)} ·{' '}
                {t('attempts', { count: delivery.attempts })} · HTTP {delivery.responseCode ?? '—'}
              </span>
              {delivery.error ? (
                <span className="text-xs text-danger">{delivery.error}</span>
              ) : null}
            </li>
          ))}
        </ul>
      </SheetContent>
    </Sheet>
  );
}

/** FR-INTEG-01/02: personal access tokens and outgoing webhooks (secrets shown once). */
export default function AdminIntegrationsPage() {
  const t = useTranslations('adminIntegrations');
  const locale = useLocale();
  const { tokens, webhooks, revokeToken, deleteWebhook } = useIntegrations();
  const [deliveriesFor, setDeliveriesFor] = useState<string | null>(null);
  return (
    <div className="grid grid-cols-1 gap-gutter lg:grid-cols-2">
      <Panel
        title={
          <span className="flex items-center gap-2">
            {t('tokens')}
            {tokens.data?.length ? <Badge tone="primary">{tokens.data.length}</Badge> : null}
          </span>
        }
        description={t('tokensHint')}
        actions={<CreateTokenDialog />}
      >
        {tokens.data?.length === 0 ? (
          <EmptyState icon={KeyRound} title={t('noTokens')} description={t('noTokensHint')} />
        ) : null}
        <ul className="flex flex-col gap-2">
          {tokens.data?.map((token) => (
            <li
              key={token.id}
              className="flex items-center gap-3 rounded-md bg-surface-muted px-4 py-3"
            >
              <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-primary-soft text-primary">
                <KeyRound className="size-4" aria-hidden />
              </span>
              <span className="flex min-w-0 flex-1 flex-col gap-1">
                <span className="truncate font-semibold">{token.name}</span>
                <span className="flex flex-wrap items-center gap-1.5 text-xs text-text-muted">
                  {token.scopes.map((scope) => (
                    <Badge key={scope} tone="primary">
                      {t(`scopes.${scope}`)}
                    </Badge>
                  ))}
                  <span>
                    {token.lastUsedAt
                      ? t('lastUsed', { date: formatDateTime(token.lastUsedAt, locale) })
                      : t('neverUsed')}
                  </span>
                </span>
              </span>
              <Button
                variant="ghost"
                size="icon-sm"
                className="text-danger hover:bg-danger-soft hover:text-danger"
                aria-label={t('revokeToken', { name: token.name })}
                onClick={() => revokeToken.mutate(token.id)}
              >
                <Trash2 aria-hidden />
              </Button>
            </li>
          ))}
        </ul>
      </Panel>
      <Panel
        title={
          <span className="flex items-center gap-2">
            {t('webhooks')}
            {webhooks.data?.length ? <Badge tone="primary">{webhooks.data.length}</Badge> : null}
          </span>
        }
        description={t('webhooksHint')}
        actions={<CreateWebhookDialog />}
      >
        {webhooks.data?.length === 0 ? (
          <EmptyState icon={Webhook} title={t('noWebhooks')} description={t('noWebhooksHint')} />
        ) : null}
        <ul className="flex flex-col gap-2">
          {webhooks.data?.map((webhook) => (
            <li
              key={webhook.id}
              className="flex flex-wrap items-center gap-3 rounded-md bg-surface-muted px-4 py-3 sm:flex-nowrap"
            >
              <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-success-soft text-success">
                <Webhook className="size-4" aria-hidden />
              </span>
              <span className="flex min-w-0 flex-1 flex-col gap-1">
                <span className="truncate font-mono text-xs font-semibold">{webhook.url}</span>
                <span className="flex flex-wrap gap-1">
                  {webhook.events.map((event) => (
                    <span
                      key={event}
                      className="rounded-full bg-surface px-2 py-0.5 font-mono text-[11px] text-text-muted"
                    >
                      {event}
                    </span>
                  ))}
                </span>
              </span>
              <span className="flex shrink-0 items-center gap-1">
                <Button variant="secondary" size="sm" onClick={() => setDeliveriesFor(webhook.id)}>
                  {t('deliveries')}
                </Button>
                <Button
                  variant="ghost"
                  size="icon-sm"
                  className="text-danger hover:bg-danger-soft hover:text-danger"
                  aria-label={t('deleteWebhook')}
                  onClick={() => deleteWebhook.mutate(webhook.id)}
                >
                  <Trash2 aria-hidden />
                </Button>
              </span>
            </li>
          ))}
        </ul>
      </Panel>
      <DeliveriesSheet webhookId={deliveriesFor} onClose={() => setDeliveriesFor(null)} />
    </div>
  );
}
