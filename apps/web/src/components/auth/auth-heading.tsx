import type { ReactNode } from 'react';
import { cn } from '@/lib/utils/cn';

/** Auth card heading; `visuallyHidden` when the «Вход · Регистрация» tabs already name the screen. */
export function AuthHeading({
  title,
  description,
  visuallyHidden,
}: {
  title: string;
  description?: ReactNode;
  visuallyHidden?: boolean;
}) {
  return (
    <div className={cn('mb-6 flex flex-col gap-1.5 text-center', visuallyHidden && 'sr-only')}>
      <h1 className="text-2xl">{title}</h1>
      {description ? <p className="text-sm text-text-muted">{description}</p> : null}
    </div>
  );
}
