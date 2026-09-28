'use client';
import { Info, AlertTriangle, CheckCircle2 } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { createContext, useContext } from 'react';
import { FileById, VideoProcessing } from '@/components/media/file-preview';
import { HlsPlayer } from '@/components/media/hls-player';
import { useFileMeta } from '@/features/files/use-files';
import type { Block, BlockDoc, BlockOf } from '@/lib/api/schemas/blockdoc';
import { cn } from '@/lib/utils/cn';
import { EmbedFrame } from './embed-frame';
import { MathView } from './math-view';
import { RichTextView } from './rich-text-view';

/** `public` mode (SSR storefront) never calls authenticated file endpoints. */
const MediaModeContext = createContext<'private' | 'public'>('private');

const calloutStyles = {
  info: { icon: Info, className: 'border-info/30 bg-info-soft [&>svg]:text-info' },
  warning: {
    icon: AlertTriangle,
    className: 'border-warning/30 bg-warning-soft [&>svg]:text-warning',
  },
  success: {
    icon: CheckCircle2,
    className: 'border-success/30 bg-success-soft [&>svg]:text-success',
  },
} as const;

function ImageBlockView({ block }: { block: BlockOf<'image'> }) {
  const isPublic = useContext(MediaModeContext) === 'public';
  const { data } = useFileMeta(block.fileId, { enabled: !isPublic });
  return (
    <figure className="flex flex-col gap-2">
      {data?.url ? (
        // eslint-disable-next-line @next/next/no-img-element -- pre-signed storage URL
        <img src={data.url} alt={block.alt} loading="lazy" className="rounded-md" />
      ) : (
        <div className="flex aspect-video items-center justify-center rounded-md bg-surface-muted text-sm text-text-muted">
          {block.alt}
        </div>
      )}
      {block.caption ? (
        <figcaption className="text-center text-sm text-text-muted">{block.caption}</figcaption>
      ) : null}
    </figure>
  );
}

function VideoBlockView({ block }: { block: BlockOf<'video'> }) {
  const t = useTranslations('editor');
  const isPublic = useContext(MediaModeContext) === 'public';
  const { data } = useFileMeta(block.fileId, { enabled: !isPublic });
  if (block.embedUrl) return <EmbedFrame url={block.embedUrl} title={t('blockTypes.video')} />;
  if (!data) return null;
  if (data.video?.status === 'processing') return <VideoProcessing />;
  if (data.video?.hlsUrl) return <HlsPlayer src={data.video.hlsUrl} title={data.name} />;
  return data.url ? (
    <video
      controls
      src={data.url}
      aria-label={data.name}
      className="aspect-video w-full rounded-md bg-black"
    />
  ) : null;
}

function FileBlockView({ block }: { block: BlockOf<'file'> }) {
  const isPublic = useContext(MediaModeContext) === 'public';
  if (isPublic) return <p className="text-sm text-text-muted">{block.name}</p>;
  return <FileById fileId={block.fileId} mode="card" />;
}

export function BlockView({ block }: { block: Block }) {
  const t = useTranslations('editor');
  switch (block.type) {
    case 'heading': {
      const Tag = (['h1', 'h2', 'h3'] as const)[block.level - 1] ?? 'h2';
      return (
        <Tag>
          <RichTextView value={block.text} />
        </Tag>
      );
    }
    case 'paragraph':
      return (
        <p>
          <RichTextView value={block.text} />
        </p>
      );
    case 'list': {
      const Tag = block.ordered ? 'ol' : 'ul';
      return (
        <Tag>
          {block.items.map((item, index) => (
            <li key={index}>
              <RichTextView value={item} />
            </li>
          ))}
        </Tag>
      );
    }
    case 'quote':
      return (
        <blockquote>
          <RichTextView value={block.text} />
        </blockquote>
      );
    case 'code':
      return (
        <pre data-language={block.language}>
          <code>{block.code}</code>
        </pre>
      );
    case 'math':
      return <MathView latex={block.latex} />;
    case 'table':
      return (
        <div className="overflow-x-auto">
          <table>
            <tbody>
              {block.rows.map((row, rowIndex) => (
                <tr key={rowIndex}>
                  {row.map((cell, cellIndex) => (
                    <td key={cellIndex}>
                      <RichTextView value={cell} />
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      );
    case 'image':
      return <ImageBlockView block={block} />;
    case 'file':
      return <FileBlockView block={block} />;
    case 'video':
      return <VideoBlockView block={block} />;
    case 'embed':
      return <EmbedFrame url={block.url} title={t('blockTypes.embed')} />;
    case 'callout': {
      const { icon: Icon, className } = calloutStyles[block.tone];
      return (
        <div className={cn('flex gap-3 rounded-md border p-4', className)}>
          <Icon className="mt-0.5 size-5 shrink-0" aria-hidden />
          <div>
            <RichTextView value={block.text} />
          </div>
        </div>
      );
    }
  }
}

/** Reader view for BlockDoc content (pages, descriptions, feedback, forum posts). */
export function BlockRenderer({
  doc,
  className,
  mediaMode = 'private',
}: {
  doc: BlockDoc | null | undefined;
  className?: string;
  mediaMode?: 'private' | 'public';
}) {
  if (!doc || doc.blocks.length === 0) return null;
  return (
    <MediaModeContext.Provider value={mediaMode}>
      <div className={cn('prose-content', className)}>
        {doc.blocks.map((block) => (
          <BlockView key={block.id} block={block} />
        ))}
      </div>
    </MediaModeContext.Provider>
  );
}
