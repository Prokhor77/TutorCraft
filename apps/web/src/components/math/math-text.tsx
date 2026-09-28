import { Fragment, type ReactNode } from 'react';
import { MathView } from '@/components/blockdoc/math-view';
import { parseMathText } from '@/lib/math/inline-math';

function withLineBreaks(text: string): ReactNode {
  const lines = text.split('\n');
  return lines.map((line, index) => (
    <Fragment key={index}>
      {line}
      {index < lines.length - 1 ? <br /> : null}
    </Fragment>
  ));
}

/** Read-only rendering of a stored string with inline `$…$` formulas (no innerHTML for text). */
export function MathText({ value }: { value: string }) {
  return (
    <>
      {parseMathText(value).map((segment, index) =>
        segment.kind === 'math' ? (
          <MathView key={index} latex={segment.latex} block={false} />
        ) : (
          <Fragment key={index}>{withLineBreaks(segment.text)}</Fragment>
        ),
      )}
    </>
  );
}
