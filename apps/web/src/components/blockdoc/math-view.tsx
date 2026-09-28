'use client';
import { useEffect, useState } from 'react';
import { cn } from '@/lib/utils/cn';

/**
 * LaTeX via KaTeX (trust disabled → safe HTML). KaTeX is loaded lazily so pages without formulas
 * do not pay for it; the raw LaTeX is shown until it is ready.
 */
export function MathView({ latex, block = true }: { latex: string; block?: boolean }) {
  const [html, setHtml] = useState<string | null>(null);
  useEffect(() => {
    let active = true;
    import('katex')
      .then(({ default: katex }) => {
        if (active)
          setHtml(
            katex.renderToString(latex || '\;', {
              throwOnError: false,
              displayMode: block,
              trust: false,
              strict: 'ignore',
            }),
          );
      })
      .catch((error: unknown) =>
        console.warn(
          '[math] KaTeX failed to load',
          error instanceof Error ? error.message : 'unknown',
        ),
      );
    return () => {
      active = false;
    };
  }, [latex, block]);
  const className = cn(block && 'block overflow-x-auto py-2');
  if (html === null) return <code className={className}>{latex}</code>;
  return (
    <span
      className={className}
      role="math"
      aria-label={latex}
      dangerouslySetInnerHTML={{ __html: html }}
    />
  );
}
