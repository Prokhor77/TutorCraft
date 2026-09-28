'use client';
import { useEffect, useRef, useState } from 'react';
import { cn } from '@/lib/utils/cn';

type InlineEditProps = {
  value: string;
  onSave: (next: string) => void;
  label: string;
  className?: string;
  inputClassName?: string;
  disabled?: boolean;
  /** Render the display text (e.g. inside a link) */
  children?: React.ReactNode;
};

/** UX-04: click/Enter to edit in place; Enter/blur saves, Escape cancels. Empty values are rejected. */
export function InlineEdit({
  value,
  onSave,
  label,
  className,
  inputClassName,
  disabled,
  children,
}: InlineEditProps) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(value);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => setDraft(value), [value]);
  useEffect(() => {
    if (editing) inputRef.current?.select();
  }, [editing]);

  const commit = () => {
    setEditing(false);
    const trimmed = draft.trim();
    if (!trimmed || trimmed === value) return setDraft(value);
    onSave(trimmed);
  };

  if (!editing) {
    return (
      <span className={cn('group inline-flex min-w-0 items-center', className)}>
        {children ?? <span className="min-w-0 break-words">{value}</span>}
        {!disabled ? (
          <button
            type="button"
            onClick={() => setEditing(true)}
            aria-label={label}
            className="ml-1 rounded-xs px-1 text-xs text-text-muted opacity-0 hover:text-text focus-visible:opacity-100 group-hover:opacity-100"
          >
            ✎
          </button>
        ) : null}
      </span>
    );
  }
  return (
    <input
      ref={inputRef}
      aria-label={label}
      value={draft}
      onChange={(event) => setDraft(event.target.value)}
      onBlur={commit}
      onKeyDown={(event) => {
        if (event.key === 'Enter') commit();
        if (event.key === 'Escape') {
          setDraft(value);
          setEditing(false);
        }
      }}
      className={cn(
        'min-w-0 flex-1 rounded-xs border border-primary bg-surface px-1.5 py-0.5 text-inherit focus:outline-none focus:ring-2 focus:ring-focus-ring/40',
        inputClassName,
      )}
    />
  );
}
