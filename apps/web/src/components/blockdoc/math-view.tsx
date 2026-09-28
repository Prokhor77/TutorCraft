'use client';
import { useEffect, useState } from 'react';
import { renderLatex } from '@/lib/math/render-latex';
import { cn } from '@/lib/utils/cn';

/**
 * LaTeX via KaTeX (trust disabled → safe HTML). KaTeX is loaded lazily so pages without formulas
 * do not pay for it; the raw LaTeX is shown until it is ready.
 */
export function MathView({
  latex,
  block = true,
  className,
}: {
  latex: string;
  block?: boolean;
  className?: string;
}) {
  const [html, setHtml] = useState<string | null>(null);
  useEffect(() => {
    let active = true;
    renderLatex(latex, block)
      .then((rendered) => {
        if (active) setHtml(rendered);
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
  const classes = cn(
    block ? 'block overflow-x-auto py-2' : 'inline-block max-w-full align-middle',
    className,
  );
  if (html === null) return <code className={classes}>{latex}</code>;
  return (
    <span
      className={classes}
      role="math"
      aria-label={latex}
      dangerouslySetInnerHTML={{ __html: html }}
    />
  );
}
