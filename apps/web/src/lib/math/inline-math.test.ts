import { describe, expect, it } from 'vitest';
import {
  hasMath,
  mathTextToPlain,
  MAX_LATEX_LENGTH,
  normalizeLatex,
  parseMathText,
  serializeMathText,
  wrapLatex,
  type MathSegment,
} from './inline-math';

describe('parseMathText', () => {
  it('returns plain text untouched', () => {
    expect(parseMathText('Столица Франции')).toEqual([{ kind: 'text', text: 'Столица Франции' }]);
  });

  it('extracts inline formulas between delimiters', () => {
    expect(parseMathText('Решите $x^2=4$ и $y$.')).toEqual([
      { kind: 'text', text: 'Решите ' },
      { kind: 'math', latex: 'x^2=4' },
      { kind: 'text', text: ' и ' },
      { kind: 'math', latex: 'y' },
      { kind: 'text', text: '.' },
    ]);
  });

  it('treats escaped and unpaired dollars as text', () => {
    expect(parseMathText('Цена \\$5 и 10$')).toEqual([{ kind: 'text', text: 'Цена $5 и 10$' }]);
  });

  it('keeps LaTeX escapes (including \\$) inside a formula', () => {
    expect(parseMathText('$\\$ + \\{a\\}$')).toEqual([{ kind: 'math', latex: '\\$ + \\{a\\}' }]);
  });

  it('does not create empty formulas', () => {
    expect(parseMathText('$$')).toEqual([{ kind: 'text', text: '$$' }]);
  });
});

describe('serializeMathText', () => {
  it('round-trips mixed content', () => {
    const segments: MathSegment[] = [
      { kind: 'text', text: 'Стоит $3, а ' },
      { kind: 'math', latex: '\\frac{1}{2}' },
      { kind: 'text', text: ' — дробь' },
    ];
    expect(parseMathText(serializeMathText(segments))).toEqual(segments);
  });

  it('drops empty formulas', () => {
    expect(serializeMathText([{ kind: 'math', latex: '  ' }])).toBe('');
  });
});

describe('normalizeLatex / wrapLatex', () => {
  it('escapes stray delimiters and trims', () => {
    expect(normalizeLatex('  a$b \\$c ')).toBe('a\\$b \\$c');
    expect(wrapLatex('x')).toBe('$x$');
  });

  it('enforces the server length limit', () => {
    expect(normalizeLatex('x'.repeat(MAX_LATEX_LENGTH + 5))).toHaveLength(MAX_LATEX_LENGTH);
  });
});

describe('helpers', () => {
  it('detects formulas and builds a plain fallback', () => {
    expect(hasMath('a $b$')).toBe(true);
    expect(hasMath('a \\$b$')).toBe(false);
    expect(mathTextToPlain('Ответ: $\\sqrt{2}$')).toBe('Ответ: \\sqrt{2}');
  });
});
