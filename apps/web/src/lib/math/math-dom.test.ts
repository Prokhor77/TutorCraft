import { describe, expect, it, vi } from 'vitest';
import { domToRichText } from '@/lib/blockdoc/richtext';
import { insertMathAtom, insertMathText, renderMathAtoms, replaceMathAtom } from './math-dom';

function editable(html = ''): HTMLDivElement {
  const element = document.createElement('div');
  element.contentEditable = 'true';
  element.innerHTML = html;
  document.body.append(element);
  return element;
}

describe('math atoms in a contenteditable', () => {
  it('inserts an atom at the end and notifies the editor', () => {
    const element = editable('Ответ: ');
    const onInput = vi.fn();
    element.addEventListener('input', onInput);
    insertMathAtom(element, null, ' \\frac{1}{2} ');
    expect(onInput).toHaveBeenCalledTimes(1);
    expect(domToRichText(element)).toEqual([{ text: 'Ответ: $\\frac{1}{2}$ ' }]);
  });

  it('replaces the selected text', () => {
    const element = editable('a b c');
    const range = document.createRange();
    const text = element.firstChild as Text;
    range.setStart(text, 2);
    range.setEnd(text, 3);
    insertMathAtom(element, range, 'x');
    expect(domToRichText(element)).toEqual([{ text: 'a $x$ c' }]);
  });

  it('edits and removes atoms', () => {
    const element = editable(
      '<span class="math-atom" contenteditable="false" data-latex="x">x</span>',
    );
    const atom = element.querySelector<HTMLElement>('.math-atom');
    if (!atom) throw new Error('atom missing');
    replaceMathAtom(element, atom, 'y^2');
    expect(domToRichText(element)).toEqual([{ text: '$y^2$' }]);
    replaceMathAtom(element, atom, '');
    expect(domToRichText(element)).toEqual([]);
  });

  it('pastes text with formulas as atoms', () => {
    const element = editable();
    element.focus();
    const range = document.createRange();
    range.selectNodeContents(element);
    window.getSelection()?.removeAllRanges();
    window.getSelection()?.addRange(range);
    insertMathText(element, [
      { kind: 'text', text: 'где ' },
      { kind: 'math', latex: '\\pi' },
    ]);
    expect(domToRichText(element)).toEqual([{ text: 'где $\\pi$' }]);
  });

  it('renders atoms with KaTeX and keeps the LaTeX for serialization', async () => {
    const element = editable(
      '<span class="math-atom" contenteditable="false" data-latex="x^2">x^2</span>',
    );
    await renderMathAtoms(element);
    expect(element.querySelector('.katex')).not.toBeNull();
    expect(domToRichText(element)).toEqual([{ text: '$x^2$' }]);
  });
});
