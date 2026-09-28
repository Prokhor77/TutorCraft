'use client';
import { ArrowDown, ArrowUp, Copy, GripVertical, Loader2, Plus, Trash2, Type } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useMemo, useRef, useState } from 'react';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { useFileUpload } from '@/features/files/use-files';
import type { Block, BlockDoc } from '@/lib/api/schemas/blockdoc';
import type { FilePurpose } from '@/lib/api/schemas/files';
import {
  blockForUploadedFile,
  convertBlock,
  createParagraph,
  insertBlocksAfter,
  isTextBlock,
  moveBlock,
  removeBlock,
  replaceBlock,
  validateDoc,
} from '@/lib/blockdoc/doc';
import { localId } from '@/lib/utils/ids';
import { cn } from '@/lib/utils/cn';
import { BlockPicker } from './block-picker';
import { BLOCK_KINDS, blockFromKind, turnInto, UPLOAD_KINDS, type BlockKind } from './block-kinds';
import {
  CodeBlockField,
  EmbedBlockField,
  FileBlockField,
  ImageBlockField,
  ListBlockField,
  MathBlockField,
  TableBlockField,
  TextBlockField,
  VideoBlockField,
} from './block-fields';
import { EditorToolbar } from './editor-toolbar';

const TURN_INTO_KINDS: readonly BlockKind[] = [
  'paragraph',
  'heading1',
  'heading2',
  'heading3',
  'bullets',
  'quote',
  'callout',
  'code',
];
const INITIAL_BLOCK_ID = 'initial';

export type BlockEditorProps = {
  value: BlockDoc;
  onChange: (doc: BlockDoc) => void;
  label: string;
  uploadPurpose?: FilePurpose;
  kinds?: readonly BlockKind[];
  onBlur?: () => void;
  className?: string;
};

type PickerTarget = { blockId: string | null; mode: 'insert' | 'replace' } | null;

/**
 * Notion-like block editor storing the contract BlockDoc (FR-CONTENT-01).
 * Paste/drop of files uploads through the presigned flow and inserts image/video/file blocks (FR-CONTENT-02).
 */
export function BlockEditor({
  value,
  onChange,
  label,
  uploadPurpose = 'content',
  kinds = BLOCK_KINDS,
  onBlur,
  className,
}: BlockEditorProps) {
  const t = useTranslations('editor');
  const { upload, uploads } = useFileUpload(uploadPurpose);
  const [focusId, setFocusId] = useState<string | null>(null);
  const [picker, setPicker] = useState<PickerTarget>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const uploadAfterRef = useRef<string | null>(null);

  const blocks =
    value.blocks.length > 0 ? value.blocks : [{ ...createParagraph(), id: INITIAL_BLOCK_ID }];
  const invalidIds = useMemo(
    () => new Set(validateDoc(value).map((issue) => issue.blockId)),
    [value],
  );

  const commit = (next: BlockDoc) => onChange(next);
  const updateBlock = (next: Block) =>
    commit(
      value.blocks.length === 0
        ? {
            ...value,
            blocks: [{ ...next, id: next.id === INITIAL_BLOCK_ID ? localId() : next.id }],
          }
        : replaceBlock(value, next.id, next),
    );

  const insertAfter = (afterId: string | null, newBlocks: Block[]) => {
    const base = value.blocks.length === 0 ? { ...value, blocks: [] } : value;
    commit(insertBlocksAfter(base, afterId === INITIAL_BLOCK_ID ? null : afterId, newBlocks));
    setFocusId(newBlocks[newBlocks.length - 1]?.id ?? null);
  };

  const uploadFiles = async (files: File[], afterId: string | null) => {
    const created: Block[] = [];
    for (const file of files) {
      const meta = await upload(file);
      if (meta) created.push(blockForUploadedFile(meta));
    }
    if (created.length > 0) insertAfter(afterId, created);
  };

  const openFileDialog = (afterId: string | null, accept: string) => {
    uploadAfterRef.current = afterId;
    if (!fileInputRef.current) return;
    fileInputRef.current.accept = accept;
    fileInputRef.current.click();
  };

  const pickKind = (kind: BlockKind) => {
    const target = picker;
    setPicker(null);
    const accept = UPLOAD_KINDS[kind];
    const anchorId = target?.blockId ?? value.blocks[value.blocks.length - 1]?.id ?? null;
    if (accept) return openFileDialog(anchorId, accept);
    const block = blockFromKind(kind);
    if (!block) return;
    if (target?.mode === 'replace' && target.blockId) {
      updateBlock({
        ...block,
        id: target.blockId === INITIAL_BLOCK_ID ? block.id : target.blockId,
      });
      setFocusId(target.blockId === INITIAL_BLOCK_ID ? block.id : target.blockId);
      return;
    }
    insertAfter(anchorId, [block]);
  };

  const removeAndFocusPrevious = (blockId: string) => {
    const index = value.blocks.findIndex((block) => block.id === blockId);
    if (index <= 0) return;
    commit(removeBlock(value, blockId));
    setFocusId(value.blocks[index - 1]?.id ?? null);
  };

  const renderField = (block: Block) => {
    const common = {
      autoFocus: focusId === block.id,
      invalid: invalidIds.has(block.id),
      onChange: updateBlock,
      onEnter: () => insertAfter(block.id, [createParagraph()]),
      onBackspaceEmpty: () => removeAndFocusPrevious(block.id),
      onSlash: () => setPicker({ blockId: block.id, mode: 'replace' }),
    };
    switch (block.type) {
      case 'paragraph':
      case 'heading':
      case 'quote':
      case 'callout':
        return <TextBlockField {...common} block={block} />;
      case 'list':
        return (
          <ListBlockField
            {...common}
            block={block}
            onBackspaceEmpty={() => updateBlock(convertBlock(block, 'paragraph'))}
          />
        );
      case 'code':
        return <CodeBlockField {...common} block={block} />;
      case 'math':
        return <MathBlockField {...common} block={block} />;
      case 'table':
        return <TableBlockField {...common} block={block} />;
      case 'image':
        return <ImageBlockField {...common} block={block} />;
      case 'file':
        return <FileBlockField {...common} block={block} />;
      case 'video':
        return (
          <VideoBlockField
            {...common}
            block={block}
            onUploadVideo={() => {
              commit(removeBlock(value, block.id));
              openFileDialog(
                value.blocks[value.blocks.findIndex((b) => b.id === block.id) - 1]?.id ?? null,
                'video/*',
              );
            }}
          />
        );
      case 'embed':
        return <EmbedBlockField {...common} block={block} />;
    }
  };

  return (
    <div
      role="group"
      aria-label={label}
      className={cn('flex flex-col gap-3', className)}
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget as Node | null)) onBlur?.();
      }}
      onPaste={(event) => {
        const files = Array.from(event.clipboardData.files);
        if (files.length === 0) return;
        event.preventDefault();
        void uploadFiles(files, focusId ?? value.blocks[value.blocks.length - 1]?.id ?? null);
      }}
      onDragOver={(event) => {
        if (event.dataTransfer.types.includes('Files')) event.preventDefault();
      }}
      onDrop={(event) => {
        const files = Array.from(event.dataTransfer.files);
        if (files.length === 0) return;
        event.preventDefault();
        void uploadFiles(files, value.blocks[value.blocks.length - 1]?.id ?? null);
      }}
    >
      <EditorToolbar />
      <div className="flex flex-col gap-2">
        {blocks.map((block, index) => (
          <div
            key={block.id}
            className="group relative flex gap-1 rounded-md focus-within:bg-surface-muted/40 md:-ml-16 md:pl-0"
            onFocus={() => setFocusId(null)}
          >
            <div className="flex shrink-0 items-start gap-0.5 pt-0.5 opacity-100 transition-opacity md:w-16 md:justify-end md:opacity-0 md:focus-within:opacity-100 md:group-hover:opacity-100">
              <BlockPicker
                open={picker?.blockId === block.id}
                onOpenChange={(open) =>
                  setPicker(open ? { blockId: block.id, mode: 'insert' } : null)
                }
                kinds={kinds}
                onPick={pickKind}
              >
                <Button
                  variant="ghost"
                  size="icon-sm"
                  aria-label={t('insertBlockAfter', { index: index + 1 })}
                >
                  <Plus aria-hidden />
                </Button>
              </BlockPicker>
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    aria-label={t('blockActions', { index: index + 1 })}
                  >
                    <GripVertical aria-hidden />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="start">
                  {isTextBlock(block) || block.type === 'list'
                    ? TURN_INTO_KINDS.filter((kind) => kinds.includes(kind)).map((kind) => (
                        <DropdownMenuItem
                          key={kind}
                          onSelect={() => updateBlock(turnInto(block, kind))}
                        >
                          <Type aria-hidden /> {t('turnInto', { kind: t(`kinds.${kind}`) })}
                        </DropdownMenuItem>
                      ))
                    : null}
                  <DropdownMenuSeparator />
                  <DropdownMenuItem
                    disabled={index === 0}
                    onSelect={() => commit(moveBlock(value, block.id, -1))}
                  >
                    <ArrowUp aria-hidden /> {t('moveUp')}
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    disabled={index === blocks.length - 1}
                    onSelect={() => commit(moveBlock(value, block.id, 1))}
                  >
                    <ArrowDown aria-hidden /> {t('moveDown')}
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    onSelect={() => insertAfter(block.id, [{ ...block, id: localId() }])}
                  >
                    <Copy aria-hidden /> {t('duplicate')}
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    tone="danger"
                    onSelect={() => commit(removeBlock(value, block.id))}
                  >
                    <Trash2 aria-hidden /> {t('delete')}
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
            <div className="min-w-0 flex-1 py-0.5">{renderField(block)}</div>
          </div>
        ))}
      </div>
      {uploads.length > 0 ? (
        <ul aria-live="polite" className="flex flex-col gap-1 text-sm text-text-muted">
          {uploads.map((entry) => (
            <li key={entry.name} className="flex items-center gap-2">
              <Loader2 className="size-4 animate-spin" aria-hidden />
              {t('uploading', { name: entry.name, percent: Math.round(entry.progress * 100) })}
            </li>
          ))}
        </ul>
      ) : null}
      <p className="text-xs text-text-muted">{t('hint')}</p>
      <input
        ref={fileInputRef}
        type="file"
        multiple
        className="sr-only"
        tabIndex={-1}
        aria-hidden
        onChange={(event) => {
          const files = Array.from(event.target.files ?? []);
          event.target.value = '';
          if (files.length > 0) void uploadFiles(files, uploadAfterRef.current);
        }}
      />
    </div>
  );
}
