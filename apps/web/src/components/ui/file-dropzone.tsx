'use client';
import { UploadCloud } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useId, useRef, useState } from 'react';
import { cn } from '@/lib/utils/cn';

type FileDropzoneProps = {
  onFiles: (files: File[]) => void;
  /** Defaults to the Stitch copy «Перетащите аудио, видео или материалы урока сюда». */
  title?: string;
  hint?: string;
  browseLabel: string;
  accept?: string;
  multiple?: boolean;
  disabled?: boolean;
  className?: string;
  /** `inline` = one-row Stitch strip (icon · copy · button) used above grids. */
  layout?: 'stacked' | 'inline';
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
  layout = 'stacked',
}: FileDropzoneProps) {
  const defaultTitle = useTranslations('files')('dropzoneTitle');
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
        'flex rounded-lg border-2 border-dashed border-accent/30 bg-dropzone transition-colors duration-fast',
        layout === 'inline'
          ? 'flex-col items-start gap-3 px-5 py-4 text-left sm:flex-row sm:items-center'
          : 'flex-col items-center justify-center gap-2 px-6 py-8 text-center',
        dragging && 'border-accent bg-primary-soft',
        disabled && 'opacity-60',
        className,
      )}
    >
      <span className="flex size-12 items-center justify-center rounded-full bg-accent/10 text-primary">
        <UploadCloud className="size-6" aria-hidden />
      </span>
      <span className={cn('flex flex-col gap-1', layout === 'inline' && 'min-w-0 flex-1')}>
        <span className="font-heading text-base font-semibold">{title ?? defaultTitle}</span>
        {hint ? <span className="text-xs text-text-muted">{hint}</span> : null}
      </span>
      <label
        htmlFor={inputId}
        className="inline-flex h-9 shrink-0 cursor-pointer items-center rounded-full bg-surface px-4 text-label-lg text-primary shadow-sm transition-shadow duration-fast focus-within:ring-4 focus-within:ring-focus-ring/20 hover:shadow-md"
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
