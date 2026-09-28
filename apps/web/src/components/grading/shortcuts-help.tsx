'use client';
import { useTranslations } from 'next-intl';
import { Dialog, DialogContent } from '@/components/ui/dialog';
import { Kbd } from '@/components/ui/kbd';

export const GRADING_SHORTCUTS = [
  { keys: ['J'], action: 'next' },
  { keys: ['K'], action: 'previous' },
  { keys: ['Ctrl', 'Enter'], action: 'saveNext' },
  { keys: ['?'], action: 'help' },
] as const;

/** UX-11: `?` shows the shortcut reference. */
export function ShortcutsHelp({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const t = useTranslations('grading');
  const tCommon = useTranslations('common');
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent title={t('shortcutsTitle')} closeLabel={tCommon('close')}>
        <dl className="flex flex-col gap-2">
          {GRADING_SHORTCUTS.map((shortcut) => (
            <div key={shortcut.action} className="flex items-center justify-between gap-4">
              <dt className="text-sm">{t(`shortcuts.${shortcut.action}`)}</dt>
              <dd className="flex gap-1">
                {shortcut.keys.map((key) => (
                  <Kbd key={key}>{key}</Kbd>
                ))}
              </dd>
            </div>
          ))}
        </dl>
      </DialogContent>
    </Dialog>
  );
}
