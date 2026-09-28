'use client';
import { forwardRef, useEffect, useImperativeHandle, useLayoutEffect, useRef } from 'react';
import { isFormulaShortcut, useFormulaInsertion } from '@/components/math/use-formula-insertion';
import type { RichText } from '@/lib/api/schemas/blockdoc';
import { domToRichText, richTextToHtml } from '@/lib/blockdoc/richtext';
import { hasMath, parseMathText } from '@/lib/math/inline-math';
import { closestMathAtom, insertMathText, renderMathAtoms } from '@/lib/math/math-dom';
import { cn } from '@/lib/utils/cn';

export type RichTextEditableHandle = {
  focus: (atEnd?: boolean) => void;
  element: HTMLDivElement | null;
};

type Props = {
  value: RichText;
  onChange: (value: RichText) => void;
  ariaLabel: string;
  placeholder?: string;
  className?: string;
  autoFocus?: boolean;
  /** false = single line (Enter/Shift+Enter are ignored), e.g. answer options. */
  multiline?: boolean;
  disabled?: boolean;
  onKeyDown?: (event: React.KeyboardEvent<HTMLDivElement>) => void;
  onBlur?: () => void;
};

function placeCaretAtEnd(element: HTMLElement): void {
  const selection = window.getSelection();
  if (!selection) return;
  const range = document.createRange();
  range.selectNodeContents(element);
  range.collapse(false);
  selection.removeAllRanges();
  selection.addRange(range);
}

/**
 * Uncontrolled contenteditable bound to RichText. The DOM is re-seeded only when the value changes
 * from outside (keeps the caret stable while typing). Pasted content is inserted as plain text;
 * `$…$` in pasted text becomes formulas. Inline formulas are atoms: Alt+= inserts one, a click edits it.
 */
export const RichTextEditable = forwardRef<RichTextEditableHandle, Props>(function RichTextEditable(
  {
    value,
    onChange,
    ariaLabel,
    placeholder,
    className,
    autoFocus,
    multiline = true,
    disabled = false,
    onKeyDown,
    onBlur,
  },
  ref,
) {
  const elementRef = useRef<HTMLDivElement>(null);
  const lastEmitted = useRef<string | null>(null);
  const { insertAt, editAtom } = useFormulaInsertion();

  useImperativeHandle(ref, () => ({
    focus: (atEnd = true) => {
      const element = elementRef.current;
      if (!element) return;
      element.focus();
      if (atEnd) placeCaretAtEnd(element);
    },
    element: elementRef.current,
  }));

  useLayoutEffect(() => {
    const element = elementRef.current;
    const serialized = JSON.stringify(value);
    if (!element || serialized === lastEmitted.current) return;
    element.innerHTML = richTextToHtml(value);
    lastEmitted.current = serialized;
    void renderMathAtoms(element);
  }, [value]);

  useEffect(() => {
    if (!autoFocus || !elementRef.current) return;
    elementRef.current.focus();
    placeCaretAtEnd(elementRef.current);
  }, [autoFocus]);

  const emit = () => {
    if (!elementRef.current) return;
    const next = domToRichText(elementRef.current);
    lastEmitted.current = JSON.stringify(next);
    onChange(next);
  };

  const handleKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
    if (isFormulaShortcut(event)) {
      event.preventDefault();
      insertAt(event.currentTarget);
      return;
    }
    if (event.key === 'Enter' && !multiline) {
      event.preventDefault();
      return;
    }
    if (event.key === 'Enter' && event.shiftKey) {
      event.preventDefault();
      document.execCommand('insertLineBreak');
      return;
    }
    onKeyDown?.(event);
  };

  const handlePaste = (event: React.ClipboardEvent<HTMLDivElement>) => {
    if (event.clipboardData.files.length > 0) return; // bubbles to the editor for upload
    event.preventDefault();
    const text = event.clipboardData.getData('text/plain');
    const pasted = multiline ? text : text.replace(/\s*\n\s*/g, ' ');
    if (hasMath(pasted)) insertMathText(event.currentTarget, parseMathText(pasted));
    else document.execCommand('insertText', false, pasted);
  };

  return (
    <div
      ref={elementRef}
      role="textbox"
      aria-multiline={multiline}
      aria-label={ariaLabel}
      aria-disabled={disabled || undefined}
      contentEditable={!disabled}
      suppressContentEditableWarning
      data-placeholder={placeholder}
      className={cn(
        'editable-text min-h-[1.5em] whitespace-pre-wrap break-words outline-none',
        className,
      )}
      onInput={emit}
      onBlur={onBlur}
      onKeyDown={handleKeyDown}
      onPaste={handlePaste}
      onClick={(event) => {
        const atom = closestMathAtom(event.target, event.currentTarget);
        if (atom && !disabled) editAtom(event.currentTarget, atom);
      }}
    />
  );
});
