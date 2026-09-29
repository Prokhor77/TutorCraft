'use client';
import type { MathfieldElement } from 'mathlive';
import {
  forwardRef,
  type ReactNode,
  useEffect,
  useImperativeHandle,
  useRef,
  useState,
} from 'react';
import { cn } from '@/lib/utils/cn';
import { loadMathLive, RUSSIAN_INLINE_SHORTCUTS } from './mathlive';

export type MathFieldHandle = {
  /**
   * Inserts a LaTeX fragment/template (`#0`, `#?` placeholders) at the caret and selects its first
   * slot. Returns false while MathLive is not ready (or failed), so the caller can fall back.
   */
  insert: (latex: string) => boolean;
  /** Replaces the whole formula without echoing it back through `onChange`. */
  setValue: (latex: string) => void;
  focus: () => void;
};

type Props = {
  /** Initial LaTeX; later changes flow through the handle, the field stays uncontrolled. */
  initialValue: string;
  onChange: (latex: string) => void;
  /** Ctrl/Cmd+Enter. */
  onSubmit?: () => void;
  ariaLabel: string;
  autoFocus?: boolean;
  /** Rendered instead of the field when MathLive cannot be loaded. */
  fallback?: ReactNode;
  className?: string;
};

type LoadState = 'loading' | 'ready' | 'failed';

const SUBMIT_KEY = 'Enter';

function createField(module: typeof import('mathlive'), initialValue: string): MathfieldElement {
  const field = new module.MathfieldElement();
  field.value = initialValue;
  field.smartFence = true;
  field.mathVirtualKeyboardPolicy = 'auto';
  field.menuItems = []; // the context menu renders outside the modal dialog and would be unreachable
  field.inlineShortcuts = { ...field.inlineShortcuts, ...RUSSIAN_INLINE_SHORTCUTS };
  return field;
}

/**
 * WYSIWYG formula input (MathLive `<math-field>`), created imperatively so SSR and JSX typings stay
 * untouched. Empty slots are drawn as boxes (`\placeholder{}`); Tab moves between them.
 */
export const MathField = forwardRef<MathFieldHandle, Props>(function MathField(
  { initialValue, onChange, onSubmit, ariaLabel, autoFocus = false, fallback = null, className },
  ref,
) {
  const hostRef = useRef<HTMLDivElement>(null);
  const fieldRef = useRef<MathfieldElement | null>(null);
  const callbacks = useRef({ onChange, onSubmit });
  callbacks.current = { onChange, onSubmit };
  const initialRef = useRef(initialValue);
  const autoFocusRef = useRef(autoFocus);
  const [state, setState] = useState<LoadState>('loading');

  useImperativeHandle(ref, () => ({
    insert: (latex) => {
      const field = fieldRef.current;
      if (!field) return false;
      field.insert(latex, { selectionMode: 'placeholder', focus: true, scrollIntoView: true });
      callbacks.current.onChange(field.value);
      return true;
    },
    setValue: (latex) => {
      const field = fieldRef.current;
      if (field && field.value !== latex) field.setValue(latex, { silenceNotifications: true });
    },
    focus: () => fieldRef.current?.focus(),
  }));

  useEffect(() => {
    let disposed = false;
    let field: MathfieldElement | null = null;
    const handleInput = () => field && callbacks.current.onChange(field.value);
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key !== SUBMIT_KEY || !(event.ctrlKey || event.metaKey)) return;
      event.preventDefault();
      callbacks.current.onSubmit?.();
    };
    loadMathLive()
      .then((module) => {
        if (disposed || !hostRef.current) return;
        field = createField(module, initialRef.current);
        field.setAttribute('aria-label', ariaLabel);
        field.addEventListener('input', handleInput);
        field.addEventListener('keydown', handleKeyDown);
        hostRef.current.replaceChildren(field);
        fieldRef.current = field;
        setState('ready');
        if (autoFocusRef.current) requestAnimationFrame(() => field?.focus());
      })
      .catch((error: unknown) => {
        console.warn(
          '[math] MathLive failed to load',
          error instanceof Error ? error.message : 'unknown',
        );
        if (!disposed) setState('failed');
      });
    return () => {
      disposed = true;
      field?.removeEventListener('input', handleInput);
      field?.removeEventListener('keydown', handleKeyDown);
      window.mathVirtualKeyboard?.hide();
      fieldRef.current = null;
    };
  }, [ariaLabel]);

  if (state === 'failed') return <>{fallback}</>;
  return (
    <div
      ref={hostRef}
      aria-busy={state === 'loading'}
      className={cn('math-field-host min-h-16', className)}
    />
  );
});
