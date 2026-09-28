import { describe, expect, it } from 'vitest';
import type { RichText } from '@/lib/api/schemas/blockdoc';
import {
  domToRichText,
  escapeHtml,
  isSafeHref,
  normalizeRichText,
  richTextEquals,
  richTextToHtml,
  richTextToPlain,
} from './richtext';

function parse(html: string): RichText {
  const root = document.createElement('div');
  root.innerHTML = html;
  return domToRichText(root);
}

describe('normalizeRichText', () => {
  it('merges adjacent spans with identical formatting and drops empty ones', () => {
    expect(
      normalizeRichText([
        { text: 'a', marks: ['bold'] },
        { text: '' },
        { text: 'b', marks: ['bold'] },
        { text: 'c' },
      ]),
    ).toEqual([{ text: 'ab', marks: ['bold'] }, { text: 'c' }]);
  });

  it('dedupes and orders marks canonically', () => {
    expect(normalizeRichText([{ text: 'x', marks: ['code', 'bold', 'bold'] }])).toEqual([
      { text: 'x', marks: ['bold', 'code'] },
    ]);
  });

  it('removes unsafe links', () => {
    expect(normalizeRichText([{ text: 'x', href: 'javascript:alert(1)' }])).toEqual([
      { text: 'x' },
    ]);
  });
});

describe('richTextToHtml', () => {
  it('escapes HTML and nests marks', () => {
    expect(richTextToHtml([{ text: '<b>&', marks: ['bold', 'italic'] }])).toBe(
      '<strong><em>&lt;b&gt;&amp;</em></strong>',
    );
  });

  it('renders links and line breaks', () => {
    expect(richTextToHtml([{ text: 'a\nb', href: 'https://x.org' }])).toBe(
      '<a href="https://x.org">a<br>b</a>',
    );
  });
});

describe('domToRichText', () => {
  it('round-trips RichText through contenteditable HTML', () => {
    const original: RichText = [
      { text: 'Hello ' },
      { text: 'bold', marks: ['bold'] },
      { text: ' and ', marks: [] },
      { text: 'code', marks: ['italic', 'code'] },
      { text: ' link', href: 'https://example.com' },
    ];
    expect(richTextEquals(parse(richTextToHtml(original)), original)).toBe(true);
  });

  it('understands execCommand output (b/i/strike, styled spans, divs)', () => {
    expect(
      parse(
        '<b>B</b><i>I</i><strike>S</strike><span style="font-weight: bold">W</span><div>line</div>',
      ),
    ).toEqual([
      { text: 'B', marks: ['bold'] },
      { text: 'I', marks: ['italic'] },
      { text: 'S', marks: ['strike'] },
      { text: 'W', marks: ['bold'] },
      { text: 'line' },
    ]);
  });

  it('keeps only safe hrefs and strips unknown tags', () => {
    expect(parse('<a href="javascript:x">evil</a><script>bad()</script><font>ok</font>')).toEqual([
      { text: 'evilbad()ok' },
    ]);
  });

  it('turns <br> into newlines and drops a trailing one', () => {
    expect(richTextToPlain(parse('a<br>b<br>'))).toBe('a\nb');
  });
});

describe('helpers', () => {
  it('validates hrefs', () => {
    expect(isSafeHref('https://a.b')).toBe(true);
    expect(isSafeHref('mailto:a@b.c')).toBe(true);
    expect(isSafeHref('/courses/1')).toBe(true);
    expect(isSafeHref('//evil.com')).toBe(false);
    expect(isSafeHref('data:text/html,x')).toBe(false);
  });

  it('escapes quotes', () => {
    expect(escapeHtml(`"'`)).toBe('&quot;&#39;');
  });
});

describe('inline formulas', () => {
  it('seeds `$…$` as non-editable atoms carrying the LaTeX', () => {
    const html = richTextToHtml([{ text: 'x = $\\frac{1}{2}$', marks: ['bold'] }]);
    expect(html).toBe(
      '<strong>x = <span class="math-atom" contenteditable="false" data-latex="\\frac{1}{2}">\\frac{1}{2}</span></strong>',
    );
  });

  it('round-trips atoms through the DOM, ignoring rendered KaTeX markup', () => {
    const value: RichText = [{ text: 'Найдите $x^2$, если \\$5' }];
    const root = document.createElement('div');
    root.innerHTML = richTextToHtml(value);
    const atom = root.querySelector('.math-atom');
    expect(atom).not.toBeNull();
    if (atom) atom.innerHTML = '<span class="katex">x²</span>';
    expect(domToRichText(root)).toEqual(value);
  });

  it('escapes literal dollars typed as text', () => {
    expect(parse('Цена 5$ и 7$')).toEqual([{ text: 'Цена 5\\$ и 7\\$' }]);
  });
});
