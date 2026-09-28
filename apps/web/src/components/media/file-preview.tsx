'use client';
import { Download, FileText, Loader2 } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { Button } from '@/components/ui/button';
import { useFileMeta } from '@/features/files/use-files';
import type { FileMeta } from '@/lib/api/schemas/files';
import { formatFileSize } from '@/lib/utils/format';
import { HlsPlayer } from './hls-player';

const PREVIEW_HEIGHT_CLASS = 'h-[70dvh]';

/** In-browser preview for PDF, images, audio and video (FR-CONTENT-03). */
export function FilePreview({ meta }: { meta: FileMeta }) {
  const t = useTranslations('files');
  if (!meta.url) return <p className="text-sm text-text-muted">{t('notReady')}</p>;
  if (meta.video) {
    if (meta.video.status === 'processing') return <VideoProcessing />;
    if (meta.video.status === 'ready' && meta.video.hlsUrl)
      return <HlsPlayer src={meta.video.hlsUrl} title={meta.name} />;
  }
  if (meta.mime === 'application/pdf') {
    return (
      <object
        data={meta.url}
        type="application/pdf"
        aria-label={meta.name}
        className={`w-full rounded border border-border ${PREVIEW_HEIGHT_CLASS}`}
      >
        <FileCard meta={meta} />
      </object>
    );
  }
  if (meta.mime.startsWith('image/')) {
    // eslint-disable-next-line @next/next/no-img-element -- pre-signed storage URL, not optimizable by next/image
    return <img src={meta.url} alt={meta.name} className="max-h-[70dvh] max-w-full rounded" />;
  }
  if (meta.mime.startsWith('audio/'))
    return <audio controls src={meta.url} aria-label={meta.name} className="w-full" />;
  if (meta.mime.startsWith('video/'))
    return (
      <video
        controls
        src={meta.url}
        aria-label={meta.name}
        className="aspect-video w-full rounded bg-black"
      />
    );
  return <FileCard meta={meta} />;
}

export function VideoProcessing() {
  const t = useTranslations('files');
  return (
    <div
      role="status"
      className="flex aspect-video w-full flex-col items-center justify-center gap-2 rounded bg-surface-muted text-sm text-text-muted"
    >
      <Loader2 className="size-6 animate-spin" aria-hidden />
      {t('videoProcessing')}
    </div>
  );
}

export function FileCard({ meta }: { meta: FileMeta }) {
  const t = useTranslations('files');
  const locale = useLocale();
  return (
    <div className="flex items-center gap-3 rounded border border-card-border bg-surface p-3 shadow-sm">
      <FileText className="size-8 shrink-0 text-primary" aria-hidden />
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium">{meta.name}</p>
        <p className="text-xs text-text-muted">{formatFileSize(meta.size, locale)}</p>
      </div>
      {meta.url ? (
        <Button asChild variant="secondary" size="sm">
          <a href={meta.url} target="_blank" rel="noopener noreferrer" download={meta.name}>
            <Download aria-hidden /> {t('download')}
          </a>
        </Button>
      ) : null}
    </div>
  );
}

/** Resolves a fileId to a preview (fetches fresh FileMeta). */
export function FileById({
  fileId,
  mode = 'preview',
}: {
  fileId: string;
  mode?: 'preview' | 'card';
}) {
  const t = useTranslations('files');
  const { data, isLoading, isError } = useFileMeta(fileId);
  if (isLoading)
    return (
      <div className="h-16 animate-shimmer rounded bg-surface-muted" aria-label={t('loading')} />
    );
  if (isError || !data) return <p className="text-sm text-danger">{t('unavailable')}</p>;
  return mode === 'card' ? <FileCard meta={data} /> : <FilePreview meta={data} />;
}
