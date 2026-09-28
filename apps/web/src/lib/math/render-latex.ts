/**
 * Lazy KaTeX renderer shared by read-only views and editor formula atoms. KaTeX is imported on first
 * use so pages without formulas do not pay for it. `trust: false` keeps the output safe HTML
 * (no \href, \includegraphics, raw HTML).
 */
type Katex = typeof import('katex').default;

const EMPTY_FORMULA = '\\;';

let katexPromise: Promise<Katex> | null = null;

export function loadKatex(): Promise<Katex> {
  katexPromise ??= import('katex').then((module) => module.default);
  katexPromise.catch(() => {
    katexPromise = null; // allow a retry after a transient chunk-load failure
  });
  return katexPromise;
}

export function renderLatexWith(katex: Katex, latex: string, displayMode: boolean): string {
  return katex.renderToString(latex || EMPTY_FORMULA, {
    throwOnError: false,
    displayMode,
    trust: false,
    strict: 'ignore',
  });
}

export async function renderLatex(latex: string, displayMode: boolean): Promise<string> {
  return renderLatexWith(await loadKatex(), latex, displayMode);
}
