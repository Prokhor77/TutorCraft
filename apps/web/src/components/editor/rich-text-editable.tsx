'use client';
import { forwardRef, useEffect, useImperativeHandle, useLayoutEffect, useRef } from 'react';
import type { RichText } from '@/lib/api/schemas/blockdoc';
import { domToRichText, richTextToHtml } from '@/lib/blockdoc/richtext';
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
 * from outside (keeps the caret stable while typing). Pasted content is inserted as plain text.
 */
export const RichTextEditable = forwardRef<RichTextEditableHandle, Props>(function RichTextEditable(
  { value, onChange, ariaLabel, placeholder, className, autoFocus, onKeyDown, onBlur },
  ref,
) {
  const elementRef = useRef<HTMLDivElement>(null);
  const lastEmitted = useRef<string | null>(null);

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

  return (
    <div
      ref={elementRef}
      role="textbox"
      aria-multiline="true"
      aria-label={ariaLabel}
      contentEditable
      suppressContentEditableWarning
      data-placeholder={placeholder}
      className={cn(
        'editable-text min-h-[1.5em] whitespace-pre-wrap break-words outline-none',
        className,
      )}
      onInput={emit}
      onBlur={onBlur}
      onKeyDown={(event) => {
        if (event.key === 'Enter' && event.shiftKey) {
          event.preventDefault();
          document.execCommand('insertLineBreak');
          return;
        }
        onKeyDown?.(event);
      }}
      onPaste={(event) => {
        if (event.clipboardData.files.length > 0) return; // bubbles to the editor for upload
        event.preventDefault();
        document.execCommand('insertText', false, event.clipboardData.getData('text/plain'));
      }}
    />
  );
});
