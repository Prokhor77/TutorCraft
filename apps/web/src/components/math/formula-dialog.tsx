'use client';
import { Search, Trash2 } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useRef, useState } from 'react';
import { MathView } from '@/components/blockdoc/math-view';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogFooter } from '@/components/ui/dialog';
import { IconInput, Textarea } from '@/components/ui/input';
import { Kbd } from '@/components/ui/kbd';
import {
  EMPTY_SLOT,
  fieldToSource,
  findSlot,
  sourceToField,
  stripSlots,
  templateToSource,
} from '@/lib/math/formula-slots';
import { normalizeLatex } from '@/lib/math/inline-math';
import { readRecentFormulas, rememberFormula } from '@/lib/math/recent-formulas';
import { FormulaPalette } from './formula-palette';
import { MathField, type MathFieldHandle } from './math-field';

/** Where the palette inserts: the pane the user edited last. */
type Pane = 'latex' | 'visual';

/** MathLive renders its virtual keyboard and suggestion popover on <body>, outside the dialog. */
const MATHLIVE_OVERLAY_SELECTOR = '.ML__keyboard, .ML__popover';
const SUBMIT_KEY = 'Enter';
const NEXT_SLOT_KEY = 'Tab';

function isMathLiveOverlay(target: EventTarget | null): boolean {
  return target instanceof Element && target.closest(MATHLIVE_OVERLAY_SELECTOR) !== null;
}

/** Inserts a palette template at the caret (wrapping the selection) and selects its first slot. */
function insertTemplate(textarea: HTMLTextAreaElement, template: string): string {
  const { selectionStart: start, selectionEnd: end, value } = textarea;
  const text = templateToSource(template, value.slice(start, end));
  textarea.setRangeText(text, start, end, 'end');
  const slot = text.indexOf(EMPTY_SLOT);
  if (slot !== -1) textarea.setSelectionRange(start + slot, start + slot + 1);
  textarea.focus();
  return textarea.value;
}

/** Tab / Shift+Tab jumps between empty slots; returns false when there is none (normal Tab). */
function selectAdjacentSlot(textarea: HTMLTextAreaElement, backward: boolean): boolean {
  const from = backward ? textarea.selectionStart : textarea.selectionEnd;
  const index = findSlot(textarea.value, from, backward ? 'backward' : 'forward');
  if (index === -1) return false;
  textarea.setSelectionRange(index, index + 1);
  return true;
}

type PanesProps = {
  source: string;
  onSourceChange: (source: string) => void;
  onFieldChange: (value: string) => void;
  onFocusPane: (pane: Pane) => void;
  onSubmit: () => void;
  textareaRef: React.RefObject<HTMLTextAreaElement | null>;
  fieldRef: React.RefObject<MathFieldHandle | null>;
};

/** LaTeX source on the left, the same formula editable visually on the right; kept in sync. */
function FormulaPanes(props: PanesProps) {
  const { source, onSourceChange, onFieldChange, onFocusPane, onSubmit, textareaRef, fieldRef } =
    props;
  const t = useTranslations('math');
  const [initialField] = useState(() => sourceToField(source));
  const handleKeyDown = (event: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === SUBMIT_KEY && (event.ctrlKey || event.metaKey)) return onSubmit();
    if (event.key !== NEXT_SLOT_KEY || event.altKey || event.ctrlKey || event.metaKey) return;
    if (selectAdjacentSlot(event.currentTarget, event.shiftKey)) event.preventDefault();
  };
  return (
    <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
      <Textarea
        ref={textareaRef}
        aria-label={t('latexSource')}
        value={source}
        spellCheck={false}
        className="min-h-24 font-mono text-sm"
        onFocus={() => onFocusPane('latex')}
        onChange={(event) => onSourceChange(event.target.value)}
        onKeyDown={handleKeyDown}
      />
      <div
        className="flex min-h-24 items-center rounded border-[1.5px] border-transparent bg-surface-muted px-3 py-2 text-2xl focus-within:border-accent"
        onFocus={() => onFocusPane('visual')}
      >
        <MathField
          ref={fieldRef}
          initialValue={initialField}
          onChange={onFieldChange}
          onSubmit={onSubmit}
          ariaLabel={t('visualField')}
          autoFocus
          className="w-full"
          fallback={
            <div className="w-full text-center" aria-live="polite">
              <MathView latex={source} />
            </div>
          }
        />
      </div>
    </div>
  );
}

type Props = {
  initialLatex: string;
  onSubmit: (latex: string) => void;
  onClose: () => void;
};

/**
 * «Конструктор формул»: LaTeX source and a WYSIWYG field (MathLive) side by side — edit either,
 * the other follows. Palette templates show their empty slots (□) right away. Mounted per request,
 * so state starts fresh.
 */
export function FormulaDialog({ initialLatex, onSubmit, onClose }: Props) {
  const t = useTranslations('math');
  /** LaTeX in source notation: empty template slots are `□` (see formula-slots). */
  const [source, setSource] = useState(initialLatex);
  const [query, setQuery] = useState('');
  const [recent] = useState(readRecentFormulas);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const fieldRef = useRef<MathFieldHandle>(null);
  const activePane = useRef<Pane>('visual');
  const editing = initialLatex.trim().length > 0;
  const [open, setOpen] = useState(true);
  /** null = dismissed; otherwise the LaTeX to hand over once the dialog has released focus. */
  const result = useRef<string | null>(null);
  const cleanLatex = normalizeLatex(stripSlots(source));

  const finish = (value: string | null) => {
    result.current = value;
    setOpen(false);
  };
  const submit = () => {
    if (cleanLatex) rememberFormula(cleanLatex);
    finish(cleanLatex);
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
  const changeSource = (next: string) => {
    setSource(next);
    fieldRef.current?.setValue(sourceToField(next));
  };
  const pick = (template: string) => {
    if (activePane.current === 'visual' && fieldRef.current?.insert(template)) return;
    const textarea = textareaRef.current;
    if (textarea) changeSource(insertTemplate(textarea, template));
  };
  const keepMathLiveOverlay = (event: { target: EventTarget | null; preventDefault(): void }) => {
    if (isMathLiveOverlay(event.target)) event.preventDefault();
  };

  return (
    <Dialog open={open} onOpenChange={(next) => !next && finish(null)}>
      <DialogContent
        title={editing ? t('editTitle') : t('title')}
        description={t('description')}
        closeLabel={t('cancel')}
        className="max-w-3xl"
        onPointerDownOutside={keepMathLiveOverlay}
        onInteractOutside={keepMathLiveOverlay}
        onCloseAutoFocus={handOver}
      >
        <FormulaPanes
          source={source}
          onSourceChange={changeSource}
          onFieldChange={(value) => setSource(fieldToSource(value))}
          onFocusPane={(pane) => (activePane.current = pane)}
          onSubmit={submit}
          textareaRef={textareaRef}
          fieldRef={fieldRef}
        />
        <div className="flex">
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
        {/* Only the palette scrolls: an explicit min-height disables flex's content-based
            minimum, so without its own overflow it would shrink and spill under the footer. */}
        <div className="-mx-1 min-h-48 flex-1 overflow-y-auto px-1 py-1">
          <FormulaPalette query={query} recent={recent} onPick={pick} />
        </div>
        <DialogFooter className="shrink-0 items-center">
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
          <Button onClick={submit} disabled={!cleanLatex}>
            {editing ? t('save') : t('insert')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
