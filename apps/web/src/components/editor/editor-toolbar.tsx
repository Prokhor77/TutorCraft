'use client';
import { Bold, Code, Italic, Link2, Strikethrough, Underline } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogFooter } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Tooltip } from '@/components/ui/tooltip';
import {
  applyLink,
  applyMark,
  saveSelection,
  type SavedSelection,
  type ToolbarMark,
} from './marks';

const MARK_BUTTONS: { mark: ToolbarMark; icon: typeof Bold; shortcut: string }[] = [
  { mark: 'bold', icon: Bold, shortcut: 'Ctrl+B' },
  { mark: 'italic', icon: Italic, shortcut: 'Ctrl+I' },
  { mark: 'underline', icon: Underline, shortcut: 'Ctrl+U' },
  { mark: 'strike', icon: Strikethrough, shortcut: '' },
  { mark: 'code', icon: Code, shortcut: '' },
];

/** Inline formatting toolbar; buttons keep the text selection (mousedown preventDefault). */
export function EditorToolbar() {
  const t = useTranslations('editor');
  const [linkOpen, setLinkOpen] = useState(false);
  const [saved, setSaved] = useState<SavedSelection>(null);
  const [url, setUrl] = useState('https://');
  const [error, setError] = useState<string>();

  return (
    <div
      role="toolbar"
      aria-label={t('formatting')}
      className="sticky top-header z-10 flex flex-wrap gap-0.5 rounded-md border border-border bg-surface/95 p-1 shadow-sm backdrop-blur"
    >
      {MARK_BUTTONS.map(({ mark, icon: Icon, shortcut }) => (
        <Tooltip
          key={mark}
          content={shortcut ? `${t(`marks.${mark}`)} (${shortcut})` : t(`marks.${mark}`)}
        >
          <Button
            variant="ghost"
            size="icon-sm"
            aria-label={t(`marks.${mark}`)}
            onMouseDown={(event) => event.preventDefault()}
            onClick={() => applyMark(mark)}
          >
            <Icon aria-hidden />
          </Button>
        </Tooltip>
      ))}
      <Tooltip content={t('marks.link')}>
        <Button
          variant="ghost"
          size="icon-sm"
          aria-label={t('marks.link')}
          onMouseDown={(event) => event.preventDefault()}
          onClick={() => {
            setSaved(saveSelection());
            setError(undefined);
            setLinkOpen(true);
          }}
        >
          <Link2 aria-hidden />
        </Button>
      </Tooltip>
      <Dialog open={linkOpen} onOpenChange={setLinkOpen}>
        <DialogContent title={t('insertLink')} closeLabel={t('close')}>
          <form
            onSubmit={(event) => {
              event.preventDefault();
              if (!applyLink(saved, url.trim()))
                return setError(saved ? t('invalidLink') : t('selectTextFirst'));
              setLinkOpen(false);
            }}
            className="flex flex-col gap-4"
          >
            <Field label={t('linkUrl')} error={error}>
              <Input
                value={url}
                onChange={(event) => setUrl(event.target.value)}
                autoFocus
                inputMode="url"
              />
            </Field>
            <DialogFooter>
              <Button type="submit">{t('apply')}</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
