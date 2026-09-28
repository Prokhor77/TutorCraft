'use client';
import dynamic from 'next/dynamic';
import { useFormulaEditorStore } from '@/stores/formula-editor-store';

/** The dialog (palette + catalog + MathLive glue) is split out of the main bundle. */
const FormulaDialog = dynamic(
  () => import('./formula-dialog').then((module) => module.FormulaDialog),
  {
    ssr: false,
  },
);

/** Mounted once in the app shell; renders the formula dialog for the current request. */
export function FormulaDialogHost() {
  const request = useFormulaEditorStore((state) => state.request);
  const requestId = useFormulaEditorStore((state) => state.requestId);
  const close = useFormulaEditorStore((state) => state.close);
  if (!request) return null;
  return (
    <FormulaDialog
      key={requestId}
      initialLatex={request.initialLatex}
      onSubmit={request.onSubmit}
      onClose={close}
    />
  );
}
