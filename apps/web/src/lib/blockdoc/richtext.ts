import { MARKS, type Mark, type RichText, type RichTextSpan } from '@/lib/api/schemas/blockdoc';

/** Canonical mark nesting order when serializing to HTML (outermost first). */
const MARK_ORDER: readonly Mark[] = ['bold', 'italic', 'underline', 'strike', 'code'];
const MARK_TAG: Record<Mark, string> = {
  bold: 'strong',
  italic: 'em',
  underline: 'u',
  strike: 's',
  code: 'code',
};
const TAG_MARK: Record<string, Mark> = {
  STRONG: 'bold',
  B: 'bold',
  EM: 'italic',
  I: 'italic',
  U: 'underline',
  S: 'strike',
  STRIKE: 'strike',
  DEL: 'strike',
  CODE: 'code',
};
const SAFE_URL = /^(https?:|mailto:|\/(?!\/)|#)/i;

export function isSafeHref(href: string | undefined | null): href is string {
  return !!href && SAFE_URL.test(href.trim());
}

export function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function sortMarks(marks: readonly Mark[] | undefined): Mark[] {
  if (!marks?.length) return [];
  const unique = new Set(marks.filter((mark) => (MARKS as readonly string[]).includes(mark)));
  return MARK_ORDER.filter((mark) => unique.has(mark));
}

function sameFormatting(a: RichTextSpan, b: RichTextSpan): boolean {
  return (
    (a.href ?? '') === (b.href ?? '') && sortMarks(a.marks).join() === sortMarks(b.marks).join()
  );
}

/** Drops empty spans, dedupes/sorts marks, merges adjacent spans with identical formatting. */
export function normalizeRichText(richText: RichText): RichText {
  const result: RichText = [];
  for (const span of richText) {
    if (!span.text) continue;
    const marks = sortMarks(span.marks);
    const clean: RichTextSpan = {
      text: span.text,
      ...(marks.length ? { marks } : {}),
      ...(isSafeHref(span.href) ? { href: span.href } : {}),
    };
    const previous = result[result.length - 1];
    if (previous && sameFormatting(previous, clean)) previous.text += clean.text;
    else result.push(clean);
  }
  return result;
}

export function richTextToPlain(richText: RichText | undefined): string {
  return (richText ?? []).map((span) => span.text).join('');
}

export function plainToRichText(text: string): RichText {
  return text ? [{ text }] : [];
}

/** Serializes RichText to safe HTML for contenteditable seeding. Newlines become <br>. */
export function richTextToHtml(richText: RichText): string {
  return normalizeRichText(richText)
    .map((span) => {
      let html = escapeHtml(span.text).replace(/\n/g, '<br>');
      for (const mark of [...sortMarks(span.marks)].reverse())
        html = `<${MARK_TAG[mark]}>${html}</${MARK_TAG[mark]}>`;
      return span.href ? `<a href="${escapeHtml(span.href)}">${html}</a>` : html;
    })
    .join('');
}

type WalkContext = { marks: Mark[]; href?: string };

/** Parses a contenteditable DOM subtree into RichText, keeping only supported marks and safe links. */
export function domToRichText(root: Node): RichText {
  const spans: RichText = [];
  const walk = (node: Node, context: WalkContext) => {
    if (node.nodeType === Node.TEXT_NODE) {
      const text = (node.textContent ?? '').replace(/ /g, ' ');
      if (text) spans.push({ text, marks: [...context.marks], href: context.href });
      return;
    }
    if (node.nodeType !== Node.ELEMENT_NODE) return;
    const element = node as HTMLElement;
    if (element.tagName === 'BR') {
      spans.push({ text: '\n', marks: [...context.marks], href: context.href });
      return;
    }
    const next = childContext(element, context);
    element.childNodes.forEach((child) => walk(child, next));
    if (isBlockElement(element) && element.nextSibling) spans.push({ text: '\n', marks: [] });
  };
  root.childNodes.forEach((child) => walk(child, { marks: [] }));
  return trimTrailingNewline(normalizeRichText(spans));
}

function childContext(element: HTMLElement, context: WalkContext): WalkContext {
  const mark = TAG_MARK[element.tagName] ?? styleMark(element);
  const href = element.tagName === 'A' ? (element.getAttribute('href') ?? undefined) : context.href;
  return {
    marks: mark ? [...context.marks, mark] : context.marks,
    href: isSafeHref(href) ? href : undefined,
  };
}

/** execCommand in some browsers emits <span style="font-weight:bold">. */
function styleMark(element: HTMLElement): Mark | undefined {
  const style = element.style;
  if (!style) return undefined;
  if (style.fontWeight === 'bold' || Number(style.fontWeight) >= 600) return 'bold';
  if (style.fontStyle === 'italic') return 'italic';
  if (style.textDecoration.includes('underline')) return 'underline';
  if (style.textDecoration.includes('line-through')) return 'strike';
  return undefined;
}

function isBlockElement(element: HTMLElement): boolean {
  return element.tagName === 'DIV' || element.tagName === 'P';
}

function trimTrailingNewline(richText: RichText): RichText {
  const last = richText[richText.length - 1];
  if (!last || !last.text.endsWith('\n')) return richText;
  const trimmed = last.text.replace(/\n+$/, '');
  return trimmed ? [...richText.slice(0, -1), { ...last, text: trimmed }] : richText.slice(0, -1);
}

export function richTextEquals(a: RichText, b: RichText): boolean {
  return JSON.stringify(normalizeRichText(a)) === JSON.stringify(normalizeRichText(b));
}
