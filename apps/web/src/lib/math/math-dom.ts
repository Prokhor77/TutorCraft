import { escapeHtml } from '@/lib/utils/html';
import { normalizeLatex, type MathSegment } from './inline-math';
import { loadKatex, renderLatexWith } from './render-latex';

/**
 * Inline formulas inside a contenteditable are atomic, non-editable spans that carry their LaTeX in
 * `data-latex`. The DOM ↔ RichText converters read the attribute, never the rendered KaTeX markup.
 */
export const MATH_ATOM_CLASS = 'math-atom';
const LATEX_ATTRIBUTE = 'data-latex';
const RENDERED_ATTRIBUTE = 'data-rendered';
const ATOM_SELECTOR = `.${MATH_ATOM_CLASS}[${LATEX_ATTRIBUTE}]`;
/** Keeps the caret from getting stuck next to a trailing non-editable atom. */
const CARET_SPACER = ' ';

export function mathAtomHtml(latex: string): string {
  const escaped = escapeHtml(latex);
  return `<span class="${MATH_ATOM_CLASS}" contenteditable="false" ${LATEX_ATTRIBUTE}="${escaped}">${escaped}</span>`;
}

export function isMathAtom(node: Node): node is HTMLElement {
  return node instanceof HTMLElement && node.matches(ATOM_SELECTOR);
}

export function atomLatex(atom: HTMLElement): string {
  return atom.getAttribute(LATEX_ATTRIBUTE) ?? '';
}

export function closestMathAtom(target: EventTarget | null, root: HTMLElement): HTMLElement | null {
  if (!(target instanceof Element)) return null;
  const atom = target.closest<HTMLElement>(ATOM_SELECTOR);
  return atom && root.contains(atom) ? atom : null;
}

function createMathAtom(latex: string): HTMLElement {
  const template = document.createElement('template');
  template.innerHTML = mathAtomHtml(latex);
  return template.content.firstElementChild as HTMLElement;
}

/** Renders every not-yet-rendered atom under `root` with KaTeX (idempotent). */
export async function renderMathAtoms(root: HTMLElement): Promise<void> {
  const pending = [...root.querySelectorAll<HTMLElement>(ATOM_SELECTOR)].filter(
    (atom) => atom.getAttribute(RENDERED_ATTRIBUTE) !== atomLatex(atom),
  );
  if (pending.length === 0) return;
  try {
    const katex = await loadKatex();
    for (const atom of pending) {
      const latex = atomLatex(atom);
      atom.innerHTML = renderLatexWith(katex, latex, false);
      atom.setAttribute(RENDERED_ATTRIBUTE, latex);
      atom.setAttribute('role', 'math');
      atom.setAttribute('aria-label', latex);
    }
  } catch (error) {
    console.warn('[math] KaTeX failed to load', error instanceof Error ? error.message : 'unknown');
  }
}

function notifyInput(editable: HTMLElement): void {
  editable.dispatchEvent(new Event('input', { bubbles: true }));
}

function placeCaretAfter(node: Node): void {
  const selection = window.getSelection();
  if (!selection) return;
  const range = document.createRange();
  range.setStartAfter(node);
  range.collapse(true);
  selection.removeAllRanges();
  selection.addRange(range);
}

/** The current selection range if it lies inside `editable`, otherwise null (→ insert at the end). */
export function captureRange(editable: HTMLElement): Range | null {
  const selection = window.getSelection();
  if (!selection || selection.rangeCount === 0) return null;
  const range = selection.getRangeAt(0);
  return editable.contains(range.commonAncestorContainer) ? range.cloneRange() : null;
}

/** Inserts a formula atom at `range` (replacing the selected text) or at the end of `editable`. */
export function insertMathAtom(editable: HTMLElement, range: Range | null, latex: string): void {
  const normalized = normalizeLatex(latex);
  if (!normalized) return;
  const atom = createMathAtom(normalized);
  const target = range ?? document.createRange();
  if (!range) {
    target.selectNodeContents(editable);
    target.collapse(false);
  }
  target.deleteContents();
  target.insertNode(atom);
  if (!atom.nextSibling) atom.after(document.createTextNode(CARET_SPACER));
  editable.focus();
  placeCaretAfter(atom);
  void renderMathAtoms(editable);
  notifyInput(editable);
}

/** Updates an existing atom; an empty formula removes it. */
export function replaceMathAtom(editable: HTMLElement, atom: HTMLElement, latex: string): void {
  const normalized = normalizeLatex(latex);
  if (normalized) atom.setAttribute(LATEX_ATTRIBUTE, normalized);
  else atom.remove();
  editable.focus();
  void renderMathAtoms(editable);
  notifyInput(editable);
}

/** Inserts pasted text that contains `$…$` formulas as text nodes + atoms at the current selection. */
export function insertMathText(editable: HTMLElement, segments: readonly MathSegment[]): void {
  const range = captureRange(editable);
  if (!range) return;
  const fragment = document.createDocumentFragment();
  for (const segment of segments)
    fragment.append(
      segment.kind === 'text'
        ? document.createTextNode(segment.text)
        : createMathAtom(normalizeLatex(segment.latex)),
    );
  const last = fragment.lastChild;
  range.deleteContents();
  range.insertNode(fragment);
  if (last) placeCaretAfter(last);
  void renderMathAtoms(editable);
  notifyInput(editable);
}
