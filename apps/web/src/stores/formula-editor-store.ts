import { create } from 'zustand';

export type FormulaRequest = {
  /** LaTeX to edit; empty string = new formula. */
  initialLatex: string;
  /** Receives the normalized LaTeX; an empty string means «remove the formula». */
  onSubmit: (latex: string) => void;
};

type FormulaEditorState = {
  request: FormulaRequest | null;
  /** Increments per request so the host remounts the dialog with fresh state. */
  requestId: number;
  open: (request: FormulaRequest) => void;
  close: () => void;
};

/** One formula dialog for the whole app, opened by any editor (toolbar, Alt+=, option fields). */
export const useFormulaEditorStore = create<FormulaEditorState>((set) => ({
  request: null,
  requestId: 0,
  open: (request) => set((state) => ({ request, requestId: state.requestId + 1 })),
  close: () => set({ request: null }),
}));
