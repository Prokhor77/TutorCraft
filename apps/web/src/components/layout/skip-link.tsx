export const MAIN_CONTENT_ID = 'main-content';

export function SkipLink({ label }: { label: string }) {
  return (
    <a
      href={`#${MAIN_CONTENT_ID}`}
      className="sr-only z-[100] rounded bg-primary px-4 py-2 text-primary-foreground focus:not-sr-only focus:fixed focus:left-4 focus:top-4"
    >
      {label}
    </a>
  );
}
