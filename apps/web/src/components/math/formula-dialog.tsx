'use client';
import { Search, Trash2 } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useRef, useState } from 'react';
import { MathView } from '@/components/blockdoc/math-view';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogFooter } from '@/components/ui/dialog';
import { IconInput, Textarea } from '@/components/ui/input';
import { Kbd } from '@/components/ui/kbd';
import { Segmented } from '@/components/ui/segmented';
import { stripPlaceholders } from '@/lib/math/formula-catalog';
import { normalizeLatex } from '@/lib/math/inline-math';
import { readRecentFormulas, rememberFormula } from '@/lib/math/recent-formulas';
import { FormulaPalette } from './formula-palette';
import { MathField, type MathFieldHandle } from './math-field';

type InputMode = 'visual' | 'latex';

/** MathLive renders its virtual keyboard and suggestion popover on <body>, outside the dialog. */
const MATHLIVE_OVERLAY_SELECTOR = '.ML__keyboard, .ML__popover';

function isMathLiveOverlay(target: EventTarget | null): boolean {
  return target instanceof Element && target.closest(MATHLIVE_OVERLAY_SELECTOR) !== null;
}

function LatexSource({
  value,
  onChange,
  onSubmit,
  textareaRef,
}: {
  value: string;
  onChange: (latex: string) => void;
  onSubmit: () => void;
  textareaRef: React.RefObject<HTMLTextAreaElement | null>;
}) {
  const t = useTranslations('math');
  return (
    <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
      <Textarea
        ref={textareaRef}
        aria-label={t('latexSource')}
        value={value}
        autoFocus
        spellCheck={false}
        className="min-h-24 font-mono text-sm"
        onChange={(event) => onChange(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) onSubmit();
        }}
      />
      <div
        className="flex min-h-24 items-center justify-center rounded bg-surface-muted p-2"
        aria-live="polite"
      >
        <MathView latex={value} />
      </div>
    </div>
  );
}

function insertIntoTextarea(textarea: HTMLTextAreaElement | null, text: string): string | null {
  if (!textarea) return null;
  textarea.setRangeText(text, textarea.selectionStart, textarea.selectionEnd, 'end');
  textarea.focus();
  return textarea.value;
}

type Props = {
  initialLatex: string;
  onSubmit: (latex: string) => void;
  onClose: () => void;
};

/**
 * «Конструктор формул»: WYSIWYG field (MathLive) + template palette + ready formulas + recent,
 * with a LaTeX source mode for advanced users. Mounted per request, so state starts fresh.
 */
export function FormulaDialog({ initialLatex, onSubmit, onClose }: Props) {
  const t = useTranslations('math');
  const [latex, setLatex] = useState(initialLatex);
  const [mode, setMode] = useState<InputMode>('visual');
  const [query, setQuery] = useState('');
  const [recent] = useState(readRecentFormulas);
  const fieldRef = useRef<MathFieldHandle>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const editing = initialLatex.trim().length > 0;
  const [open, setOpen] = useState(true);
  /** null = dismissed; otherwise the LaTeX to hand over once the dialog has released focus. */
  const result = useRef<string | null>(null);

  const finish = (value: string | null) => {
    result.current = value;
    setOpen(false);
  };
  const submit = () => {
    const normalized = normalizeLatex(latex);
    if (normalized) rememberFormula(normalized);
    finish(normalized);
  };
  /**
   * The caller moves the caret into its editor, so it must run after the focus trap is gone;
   * on dismiss Radix restores focus to the editor as usual.
   */
  const handOver = (event: Event) => {
    const value = result.current;
    if (value) event.preventDefault();
    window.setTimeout(() => {
      if (value !== null) onSubmit(value);
      onClose();
    }, 0);
  };
  const pick = (template: string) => {
    if (mode === 'visual') return fieldRef.current?.insert(template);
    const next = insertIntoTextarea(textareaRef.current, stripPlaceholders(template));
    if (next !== null) setLatex(next);
  };

  return (
    <Dialog open={open} onOpenChange={(next) => !next && finish(null)}>
      <DialogContent
        title={editing ? t('editTitle') : t('title')}
        description={t('description')}
        closeLabel={t('cancel')}
        className="max-w-3xl"
        onPointerDownOutside={(event) => {
          if (isMathLiveOverlay(event.target)) event.preventDefault();
        }}
        onInteractOutside={(event) => {
          if (isMathLiveOverlay(event.target)) event.preventDefault();
        }}
        onCloseAutoFocus={handOver}
      >
        {mode === 'visual' ? (
          <MathField
            ref={fieldRef}
            initialValue={latex}
            onChange={setLatex}
            onSubmit={submit}
            ariaLabel={t('fieldLabel')}
            className="rounded border-[1.5px] border-border bg-surface px-3 py-2 text-2xl focus-within:border-accent"
          />
        ) : (
          <LatexSource
            value={latex}
            onChange={setLatex}
            onSubmit={submit}
            textareaRef={textareaRef}
          />
        )}
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
          <Segmented<InputMode>
            label={t('mode')}
            value={mode}
            onChange={setMode}
            options={[
              { value: 'visual', label: t('modeVisual') },
              { value: 'latex', label: t('modeLatex') },
            ]}
          />
          <IconInput
            icon={Search}
            type="search"
            aria-label={t('search')}
            placeholder={t('search')}
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            className="h-10"
          />
        </div>
        <div className="min-h-48">
          <FormulaPalette query={query} recent={recent} onPick={pick} />
        </div>
        <DialogFooter className="items-center">
          <span className="mr-auto hidden text-xs text-text-muted sm:inline">
            <Kbd>Ctrl</Kbd>+<Kbd>Enter</Kbd> — {editing ? t('save') : t('insert')}
          </span>
          {editing ? (
            <Button variant="ghost" onClick={() => finish('')}>
              <Trash2 aria-hidden /> {t('remove')}
            </Button>
          ) : null}
          <Button variant="secondary" onClick={() => finish(null)}>
            {t('cancel')}
          </Button>
          <Button onClick={submit} disabled={!latex.trim()}>
            {editing ? t('save') : t('insert')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
