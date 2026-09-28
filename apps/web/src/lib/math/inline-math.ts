/**
 * Inline formulas inside plain strings (question options, pairs, RichText spans, essays).
 *
 * Storage format: a formula is written as `$<latex>$`; a literal dollar in regular text is `\$`.
 * The format keeps every existing string field backward-compatible, so the backend and its
 * sanitizers need no schema change.
 */
export const MATH_DELIMITER = '$';
export const ESCAPED_DELIMITER = '\\$';
/** Mirrors BlockLimits.MAX_LATEX on the server. */
export const MAX_LATEX_LENGTH = 10_000;

const ESCAPE_CHAR = '\\';
const UNESCAPED_DELIMITER = /(^|[^\\])\$/g;

export type MathSegment = { kind: 'text'; text: string } | { kind: 'math'; latex: string };

/** Index of the closing delimiter, skipping escaped characters inside LaTeX; -1 when unpaired. */
function findClosingDelimiter(source: string, from: number): number {
  for (let index = from; index < source.length; index += 1) {
    const char = source[index];
    if (char === ESCAPE_CHAR) {
      index += 1;
      continue;
    }
    if (char === MATH_DELIMITER) return index;
  }
  return -1;
}

/** Splits a stored string into text and formula segments. Unpaired or empty `$` stay literal. */
export function parseMathText(source: string): MathSegment[] {
  const segments: MathSegment[] = [];
  let text = '';
  const flushText = () => {
    if (text) segments.push({ kind: 'text', text });
    text = '';
  };
  let index = 0;
  while (index < source.length) {
    const char = source[index] as string;
    if (char === ESCAPE_CHAR && source[index + 1] === MATH_DELIMITER) {
      text += MATH_DELIMITER;
      index += 2;
      continue;
    }
    const closing = char === MATH_DELIMITER ? findClosingDelimiter(source, index + 1) : -1;
    if (closing > index + 1) {
      flushText();
      segments.push({ kind: 'math', latex: source.slice(index + 1, closing) });
      index = closing + 1;
      continue;
    }
    text += char;
    index += 1;
  }
  flushText();
  return segments;
}

export function escapeMathText(text: string): string {
  return text.split(MATH_DELIMITER).join(ESCAPED_DELIMITER);
}

/** Trims, escapes stray `$` (they would close the formula) and enforces the server limit. */
export function normalizeLatex(latex: string): string {
  return latex
    .trim()
    .replace(UNESCAPED_DELIMITER, `$1${ESCAPED_DELIMITER}`)
    .slice(0, MAX_LATEX_LENGTH);
}

export function wrapLatex(latex: string): string {
  const normalized = normalizeLatex(latex);
  return normalized ? `${MATH_DELIMITER}${normalized}${MATH_DELIMITER}` : '';
}

export function serializeMathText(segments: readonly MathSegment[]): string {
  return segments
    .map((segment) =>
      segment.kind === 'text' ? escapeMathText(segment.text) : wrapLatex(segment.latex),
    )
    .join('');
}

export function hasMath(source: string): boolean {
  return parseMathText(source).some((segment) => segment.kind === 'math');
}

/** Readable fallback (aria-label, <option>, search): text unescaped, formulas as raw LaTeX. */
export function mathTextToPlain(source: string): string {
  return parseMathText(source)
    .map((segment) => (segment.kind === 'text' ? segment.text : segment.latex))
    .join('');
}
