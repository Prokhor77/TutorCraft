'use client';
import { Copy, ExternalLink, Globe, Send } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useEffect, useState } from 'react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Panel } from '@/components/ui/page-header';
import { toast } from '@/components/ui/toast';
import { useMe } from '@/features/auth/use-auth';
import { useAwaitTelegramLink, useLinkTelegram } from '@/features/notifications/use-notifications';
import {
  buildTelegramLinks,
  preferredTelegramUrl,
  type TelegramLinks,
} from '@/lib/telegram/deep-link';
import { copyToClipboard } from '@/lib/utils/clipboard';

/** Matches CODE_TTL in core-api TelegramLinkService: after it the start code is useless. */
const LINK_CODE_TTL_MS = 15 * 60 * 1000;

/** FR-NOTIF-HYB-01: open the bot in the Telegram app on any platform, with manual fallbacks. */
export function TelegramConnect() {
  const t = useTranslations('notificationSettings');
  const me = useMe();
  const alreadyLinked = Boolean(me?.telegramLinked);
  const [links, setLinks] = useState<TelegramLinks | null>(null);
  const request = useLinkTelegram();
  const justLinked = useAwaitTelegramLink(links !== null && !alreadyLinked);

  useEffect(() => {
    if (!justLinked) return;
    setLinks(null);
    toast({ tone: 'success', title: t('linkedToast') });
  }, [justLinked, t]);

  useEffect(() => {
    if (!links) return;
    const expire = window.setTimeout(() => setLinks(null), LINK_CODE_TTL_MS);
    return () => window.clearTimeout(expire);
  }, [links]);

  const connect = () =>
    request.mutate(undefined, {
      onSuccess: ({ deepLink }) => {
        const built = buildTelegramLinks(deepLink);
        if (!built) {
          console.error('[telegram] core-api returned an unusable deep link');
          toast({ tone: 'error', title: t('linkError') });
          return;
        }
        setLinks(built);
        window.location.assign(preferredTelegramUrl(built, navigator));
      },
      onError: () => toast({ tone: 'error', title: t('linkError') }),
    });

  return (
    <Panel className="mb-4 md:mb-gutter">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <TelegramStatus linked={alreadyLinked} />
        <Button
          variant={alreadyLinked ? 'secondary' : 'primary'}
          loading={request.isPending}
          onClick={connect}
        >
          <Send aria-hidden /> {alreadyLinked ? t('relink') : t('link')}
        </Button>
      </div>
      {links ? <TelegramFallbacks links={links} /> : null}
    </Panel>
  );
}

function TelegramStatus({ linked }: { linked: boolean }) {
  const t = useTranslations('notificationSettings');
  return (
    <div className="flex items-start gap-3 sm:items-center">
      <span className="flex size-11 shrink-0 items-center justify-center rounded-full bg-info-soft text-info">
        <Send className="size-5" aria-hidden />
      </span>
      <div className="flex min-w-0 flex-col gap-1">
        <span className="flex flex-wrap items-center gap-2">
          <h2 className="text-lg">Telegram</h2>
          {linked ? (
            <Badge tone="success" dot>
              {t('linked')}
            </Badge>
          ) : (
            <Badge dot>{t('notLinked')}</Badge>
          )}
        </span>
        <span className="text-sm text-text-muted">{t('telegramHint')}</span>
      </div>
    </div>
  );
}

/** Every option is a real link the user taps, so neither popup blockers nor OS prompts can swallow it. */
function TelegramFallbacks({ links }: { links: TelegramLinks }) {
  const t = useTranslations('notificationSettings');
  const copy = async () => {
    const copied = await copyToClipboard(links.httpsUrl);
    toast({ tone: copied ? 'success' : 'error', title: t(copied ? 'linkCopied' : 'copyFailed') });
  };
  return (
    <div role="status" className="flex flex-col gap-3 rounded-md bg-surface-muted p-4 text-sm">
      <p>
        <span className="font-semibold">{t('fallbackTitle')}</span>{' '}
        <span className="text-text-muted">{t('fallbackHint')}</span>
      </p>
      <div className="flex flex-wrap gap-2">
        <Button asChild size="sm">
          <a href={links.appUrl}>
            <Send aria-hidden /> {t('openApp')}
          </a>
        </Button>
        <Button asChild size="sm" variant="secondary">
          <a href={links.webUrl} target="_blank" rel="noopener noreferrer">
            <Globe aria-hidden /> {t('openWeb')}
          </a>
        </Button>
        <Button asChild size="sm" variant="ghost">
          <a href={links.httpsUrl} target="_blank" rel="noopener noreferrer">
            <ExternalLink aria-hidden /> {t('openTme')}
          </a>
        </Button>
        <Button size="sm" variant="ghost" onClick={() => void copy()}>
          <Copy aria-hidden /> {t('copyLink')}
        </Button>
      </div>
    </div>
  );
}
