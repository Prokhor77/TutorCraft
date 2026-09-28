'use client';
import { ExternalLink, FolderOpen } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { EmbedFrame } from '@/components/blockdoc/embed-frame';
import { BlockRenderer } from '@/components/blockdoc/block-renderer';
import { FileById, VideoProcessing } from '@/components/media/file-preview';
import { HlsPlayer } from '@/components/media/hls-player';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';
import { useFileMeta } from '@/features/files/use-files';
import { isSafeHref } from '@/lib/blockdoc/richtext';
import type { ItemDetail, SettingsOf } from '@/lib/api/schemas/courses';

function VideoView({ item, settings }: { item: ItemDetail; settings: SettingsOf<'video'> }) {
  const t = useTranslations('itemView');
  const meta = useFileMeta(settings.fileId);
  if (settings.embedUrl) return <EmbedFrame url={settings.embedUrl} title={item.title} />;
  const status = settings.videoStatus ?? meta.data?.video?.status;
  const hlsUrl = settings.hlsUrl ?? meta.data?.video?.hlsUrl;
  if (status === 'processing') return <VideoProcessing />;
  if (status === 'failed') return <Alert tone="danger" title={t('videoFailed')} />;
  if (hlsUrl) return <HlsPlayer src={hlsUrl} title={item.title} />;
  return (
    <EmptyState icon={FolderOpen} title={t('videoMissing')} description={t('videoMissingHint')} />
  );
}

/** Student/reader views for resources (FR-CONTENT-03). */
export function ResourceView({ item }: { item: ItemDetail }) {
  const t = useTranslations('itemView');
  const settings = item.settings;
  return (
    <div className="flex flex-col gap-6">
      <BlockRenderer doc={item.content} />
      {settings.kind === 'file' && settings.fileId ? <FileById fileId={settings.fileId} /> : null}
      {settings.kind === 'url' && isSafeHref(settings.url) ? (
        <Button asChild size="lg" className="self-start">
          <a href={settings.url} target="_blank" rel="noopener noreferrer">
            <ExternalLink aria-hidden /> {t('openLink')}
          </a>
        </Button>
      ) : null}
      {settings.kind === 'folder' ? (
        settings.fileIds.length === 0 ? (
          <EmptyState
            icon={FolderOpen}
            title={t('folderEmpty')}
            description={t('folderEmptyHint')}
          />
        ) : (
          <ul className="flex flex-col gap-2">
            {settings.fileIds.map((fileId) => (
              <li key={fileId}>
                <FileById fileId={fileId} mode="card" />
              </li>
            ))}
          </ul>
        )
      ) : null}
      {settings.kind === 'video' ? <VideoView item={item} settings={settings} /> : null}
    </div>
  );
}
