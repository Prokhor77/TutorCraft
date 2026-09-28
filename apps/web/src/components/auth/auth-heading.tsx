import type { ReactNode } from 'react';

export function AuthHeading({ title, description }: { title: string; description?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-col gap-1.5 text-center">
      <h1 className="text-2xl">{title}</h1>
      {description ? <p className="text-sm text-text-muted">{description}</p> : null}
    </div>
  );
}
