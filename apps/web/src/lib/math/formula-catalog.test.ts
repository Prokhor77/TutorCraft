import katex from 'katex';
import { describe, expect, it } from 'vitest';
import { LIBRARY_GROUPS, PALETTE_GROUPS, searchFormulas, toPreviewLatex } from './formula-catalog';

describe('formula catalog', () => {
  it('has unique group ids and non-empty groups', () => {
    for (const groups of [PALETTE_GROUPS, LIBRARY_GROUPS]) {
      const ids = groups.map((item) => item.id);
      expect(new Set(ids).size).toBe(ids.length);
      expect(groups.every((item) => item.items.length > 0)).toBe(true);
    }
  });

  it('has no duplicate formulas inside a group', () => {
    for (const item of [...PALETTE_GROUPS, ...LIBRARY_GROUPS]) {
      const latex = item.items.map((entry) => entry.latex);
      expect(new Set(latex).size, item.id).toBe(latex.length);
    }
  });

  it('replaces MathLive placeholders for previews', () => {
    expect(toPreviewLatex('\\frac{#0}{#?}')).toBe('\\frac{□}{□}');
  });

  it('searches in both languages and keywords', () => {
    expect(searchFormulas(LIBRARY_GROUPS, 'пифагор').map((entry) => entry.latex)).toContain(
      'c^2=a^2+b^2',
    );
    expect(searchFormulas(PALETTE_GROUPS, 'root').length).toBeGreaterThan(0);
    expect(searchFormulas(PALETTE_GROUPS, '   ')).toEqual([]);
  });

  it('renders every entry with KaTeX (no parse errors)', () => {
    for (const entry of [...PALETTE_GROUPS, ...LIBRARY_GROUPS].flatMap((item) => item.items)) {
      expect(
        () => katex.renderToString(toPreviewLatex(entry.latex), { throwOnError: true }),
        entry.latex,
      ).not.toThrow();
    }
  });
});
