import { Fragment, type ReactNode } from 'react';
import { MathText } from '@/components/math/math-text';
import type { Mark, RichText } from '@/lib/api/schemas/blockdoc';
import { isSafeHref } from '@/lib/blockdoc/richtext';

const markWrappers: Record<Mark, (children: ReactNode) => ReactNode> = {
  bold: (children) => <strong>{children}</strong>,
  italic: (children) => <em>{children}</em>,
  underline: (children) => <u>{children}</u>,
  strike: (children) => <s>{children}</s>,
  code: (children) => <code>{children}</code>,
};

/** Safe React rendering of RichText (no innerHTML for text); `$…$` spans render as formulas. */
export function RichTextView({ value }: { value: RichText }) {
  return (
    <>
      {value.map((span, index) => {
        let node: ReactNode = <MathText value={span.text} />;
        for (const mark of span.marks ?? []) node = markWrappers[mark]?.(node) ?? node;
        if (isSafeHref(span.href)) {
          node = (
            <a href={span.href} target="_blank" rel="noopener noreferrer nofollow">
              {node}
            </a>
          );
        }
        return <Fragment key={index}>{node}</Fragment>;
      })}
    </>
  );
}
