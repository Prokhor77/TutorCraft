'use client';
import { useCallback } from 'react';
import { atomLatex, captureRange, insertMathAtom, replaceMathAtom } from '@/lib/math/math-dom';
import { useFormulaEditorStore } from '@/stores/formula-editor-store';

/** Word-style shortcut for «Insert equation» (Alt+=; physical key, so it also works as Option+= on macOS). */
export function isFormulaShortcut(event: { altKey: boolean; code: string }): boolean {
  return event.altKey && event.code === 'Equal';
}

/** Opens the formula dialog for a contenteditable: insert at the caret or edit an existing atom. */
export function useFormulaInsertion() {
  const open = useFormulaEditorStore((state) => state.open);

  const insertAt = useCallback(
    (editable: HTMLElement, range: Range | null = captureRange(editable)) =>
      open({
        initialLatex: '',
        onSubmit: (latex) => insertMathAtom(editable, range, latex),
      }),
    [open],
  );

  const editAtom = useCallback(
    (editable: HTMLElement, atom: HTMLElement) =>
      open({
        initialLatex: atomLatex(atom),
        onSubmit: (latex) => replaceMathAtom(editable, atom, latex),
      }),
    [open],
  );

  return { insertAt, editAtom };
}
