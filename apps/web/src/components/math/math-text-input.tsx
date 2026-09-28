'use client';
import { Sigma } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useMemo, useRef } from 'react';
import {
  RichTextEditable,
  type RichTextEditableHandle,
} from '@/components/editor/rich-text-editable';
import { Tooltip } from '@/components/ui/tooltip';
import { fieldControlClass } from '@/components/ui/input';
import { plainToRichText, richTextToPlain } from '@/lib/blockdoc/richtext';
import { captureRange } from '@/lib/math/math-dom';
import { cn } from '@/lib/utils/cn';
import { useFormulaInsertion } from './use-formula-insertion';

/** Formatting shortcuts are swallowed: this field stores plain text with `$…$` formulas only. */
const FORMATTING_KEYS = new Set(['b', 'i', 'u']);

type Props = {
  value: string;
  onChange: (value: string) => void;
  ariaLabel: string;
  placeholder?: string;
  multiline?: boolean;
  disabled?: boolean;
  className?: string;
};

/**
 * Drop-in replacement for `Input`/`Textarea` where formulas are allowed (answer options, pairs,
 * essays): formulas render inline, Σ or Alt+= opens the formula editor, a click edits a formula.
 */
export function MathTextInput({
  value,
  onChange,
  ariaLabel,
  placeholder,
  multiline = false,
  disabled = false,
  className,
}: Props) {
  const t = useTranslations('math');
  const editableRef = useRef<RichTextEditableHandle>(null);
  const { insertAt } = useFormulaInsertion();
  const richText = useMemo(() => plainToRichText(value), [value]);

  const openEditor = () => {
    const editable = editableRef.current?.element;
    if (editable) insertAt(editable, captureRange(editable));
  };

  return (
    <div
      className={cn(
        fieldControlClass,
        'flex items-start gap-1 py-2 pr-1 focus-within:border-accent focus-within:ring-4 focus-within:ring-focus-ring/15',
        multiline ? 'min-h-24' : 'min-h-11',
        disabled && 'cursor-not-allowed opacity-60',
        className,
      )}
    >
      <RichTextEditable
        ref={editableRef}
        value={richText}
        onChange={(next) => onChange(richTextToPlain(next))}
        ariaLabel={ariaLabel}
        placeholder={placeholder}
        multiline={multiline}
        disabled={disabled}
        className="min-w-0 flex-1 self-center leading-7"
        onKeyDown={(event) => {
          if ((event.ctrlKey || event.metaKey) && FORMATTING_KEYS.has(event.key.toLowerCase()))
            event.preventDefault();
        }}
      />
      <Tooltip content={t('insertHint')}>
        <button
          type="button"
          aria-label={t('insertHint')}
          disabled={disabled}
          onMouseDown={(event) => event.preventDefault()}
          onClick={openEditor}
          className="flex size-8 shrink-0 items-center justify-center rounded-full text-text-muted transition-colors duration-fast hover:bg-accent/10 hover:text-primary focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20 disabled:pointer-events-none"
        >
          <Sigma className="size-4" aria-hidden />
        </button>
      </Tooltip>
    </div>
  );
}
