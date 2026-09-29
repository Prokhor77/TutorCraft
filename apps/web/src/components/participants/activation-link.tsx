'use client';
import { Copy } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { toast } from '@/components/ui/toast';
import { copyToClipboard } from '@/lib/utils/clipboard';

/** One-time activation link for an invited account: shown to the inviter once, to pass on via any messenger. */
export function ActivationLink({ url }: { url: string }) {
  const t = useTranslations('participants');
  return (
    <div className="flex flex-col gap-3">
      <Alert tone="warning" title={t('activationShownOnce')} />
      <div className="flex gap-2">
        <Input
          readOnly
          value={url}
          aria-label={t('activationUrl')}
          onFocus={(event) => event.target.select()}
        />
        <Button
          onClick={() =>
            void copyToClipboard(url).then((ok) =>
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
    </div>
  );
}
