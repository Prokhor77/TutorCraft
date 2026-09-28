'use client';
import { UploadCloud } from 'lucide-react';
import { useId, useRef, useState } from 'react';
import { cn } from '@/lib/utils/cn';

type FileDropzoneProps = {
  onFiles: (files: File[]) => void;
  title: string;
  hint?: string;
  browseLabel: string;
  accept?: string;
  multiple?: boolean;
  disabled?: boolean;
  className?: string;
};

/** Drag & drop + click + keyboard accessible file picker. */
export function FileDropzone({
  onFiles,
  title,
  hint,
  browseLabel,
  accept,
  multiple = true,
  disabled,
  className,
}: FileDropzoneProps) {
  const inputId = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);

  const handleFiles = (list: FileList | null) => {
    if (!list || list.length === 0 || disabled) return;
    onFiles(Array.from(list));
  };

  return (
    <div
      onDragOver={(event) => {
        event.preventDefault();
        if (!disabled) setDragging(true);
      }}
      onDragLeave={() => setDragging(false)}
      onDrop={(event) => {
        event.preventDefault();
        setDragging(false);
        handleFiles(event.dataTransfer.files);
      }}
      className={cn(
        'flex flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed border-border bg-surface px-4 py-8 text-center transition-colors duration-fast',
        dragging && 'border-primary bg-primary-soft',
        disabled && 'opacity-60',
        className,
      )}
    >
      <UploadCloud className="size-8 text-primary" aria-hidden />
      <p className="text-sm font-medium">{title}</p>
      {hint ? <p className="text-xs text-text-muted">{hint}</p> : null}
      <label
        htmlFor={inputId}
        className="mt-1 cursor-pointer rounded-md px-3 py-1.5 text-sm font-medium text-primary focus-within:ring-2 focus-within:ring-focus-ring hover:bg-primary-soft"
      >
        {browseLabel}
        <input
          ref={inputRef}
          id={inputId}
          type="file"
          accept={accept}
          multiple={multiple}
          disabled={disabled}
          className="sr-only"
          onChange={(event) => {
            handleFiles(event.target.files);
            if (inputRef.current) inputRef.current.value = '';
          }}
        />
      </label>
    </div>
  );
}
