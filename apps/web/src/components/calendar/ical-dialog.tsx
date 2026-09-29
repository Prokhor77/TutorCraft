'use client';
import { useMutation } from '@tanstack/react-query';
import { Copy, Rss } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogTrigger } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { toast } from '@/components/ui/toast';
import { meApi } from '@/lib/api/endpoints/me';
import { copyToClipboard } from '@/lib/utils/clipboard';

/** iCal subscription by secret link; reissuing revokes the previous link. */
export function IcalDialog() {
  const t = useTranslations('calendar');
  const tCommon = useTranslations('common');
  const issue = useMutation({ mutationFn: meApi.icalToken });
  return (
    <Dialog>
      <DialogTrigger asChild>
        <Button variant="secondary" size="sm">
          <Rss aria-hidden /> {t('subscribe')}
        </Button>
      </DialogTrigger>
      <DialogContent
        title={t('icalTitle')}
        description={t('icalHint')}
        closeLabel={tCommon('close')}
      >
        {issue.data ? (
          <div className="flex gap-2">
            <Input
              readOnly
              value={issue.data.url}
              aria-label={t('icalUrl')}
              onFocus={(event) => event.target.select()}
            />
            <Button
              onClick={() =>
                issue.data &&
                void copyToClipboard(issue.data.url).then((ok) =>
                  toast({
                    tone: ok ? 'success' : 'error',
                    title: ok ? t('copied') : t('copyFailed'),
                  }),
                )
              }
            >
              <Copy aria-hidden /> {t('copy')}
            </Button>
          </div>
        ) : null}
        <Button
          variant={issue.data ? 'ghost' : 'primary'}
          loading={issue.isPending}
          onClick={() => issue.mutate()}
        >
          {issue.data ? t('reissue') : t('getLink')}
        </Button>
        {issue.data ? <p className="text-xs text-text-muted">{t('reissueHint')}</p> : null}
      </DialogContent>
    </Dialog>
  );
}
