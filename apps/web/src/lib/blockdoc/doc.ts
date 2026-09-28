import {
  BLOCK_DOC_SCHEMA_VERSION,
  type Block,
  type BlockDoc,
  type BlockOf,
  type BlockType,
} from '@/lib/api/schemas/blockdoc';
import { localId } from '@/lib/utils/ids';
import { plainToRichText, richTextToPlain } from './richtext';

export const TEXT_BLOCK_TYPES = [
  'heading',
  'paragraph',
  'quote',
  'callout',
] as const satisfies readonly BlockType[];
export const DEFAULT_TABLE_SIZE = { rows: 2, cols: 2 } as const;
export const DEFAULT_CODE_LANGUAGE = 'plaintext';

export function emptyDoc(): BlockDoc {
  return { schemaVersion: BLOCK_DOC_SCHEMA_VERSION, blocks: [] };
}

export function docFromText(text: string): BlockDoc {
  return { schemaVersion: BLOCK_DOC_SCHEMA_VERSION, blocks: text ? [createParagraph(text)] : [] };
}

export function createParagraph(text = ''): BlockOf<'paragraph'> {
  return { id: localId(), type: 'paragraph', text: plainToRichText(text) };
}

/** Blocks that need data from an upload (image/file/video) are created by upload handlers, not here. */
export function createBlock(type: Exclude<BlockType, 'image' | 'file'>): Block {
  const id = localId();
  switch (type) {
    case 'heading':
      return { id, type, level: 2, text: [] };
    case 'paragraph':
      return { id, type, text: [] };
    case 'list':
      return { id, type, ordered: false, items: [[]] };
    case 'quote':
      return { id, type, text: [] };
    case 'code':
      return { id, type, language: DEFAULT_CODE_LANGUAGE, code: '' };
    case 'math':
      return { id, type, latex: '' };
    case 'table':
      return {
        id,
        type,
        rows: Array.from({ length: DEFAULT_TABLE_SIZE.rows }, () =>
          Array.from({ length: DEFAULT_TABLE_SIZE.cols }, () => []),
        ),
      };
    case 'video':
      return { id, type };
    case 'embed':
      return { id, type, url: '' };
    case 'callout':
      return { id, type, tone: 'info', text: [] };
  }
}

export function isTextBlock(block: Block): block is Extract<Block, { text: unknown }> {
  return (TEXT_BLOCK_TYPES as readonly string[]).includes(block.type);
}

/** Converts between text-bearing block types, keeping content where possible ("turn into"). */
export function convertBlock(block: Block, type: BlockType): Block {
  const text = isTextBlock(block) ? block.text : block.type === 'list' ? block.items.flat() : [];
  switch (type) {
    case 'heading':
      return { id: block.id, type, level: block.type === 'heading' ? block.level : 2, text };
    case 'paragraph':
    case 'quote':
      return { id: block.id, type, text };
    case 'callout':
      return { id: block.id, type, tone: 'info', text };
    case 'list':
      return { id: block.id, type, ordered: false, items: [text] };
    case 'code':
      return { id: block.id, type, language: DEFAULT_CODE_LANGUAGE, code: richTextToPlain(text) };
    default:
      return block;
  }
}

export function isDocEmpty(doc: BlockDoc | null | undefined): boolean {
  if (!doc) return true;
  return doc.blocks.every(
    (block) => isTextBlock(block) && richTextToPlain(block.text).trim() === '',
  );
}

export function docToPlainText(doc: BlockDoc | null | undefined): string {
  if (!doc) return '';
  return doc.blocks
    .map((block) => {
      if (isTextBlock(block)) return richTextToPlain(block.text);
      if (block.type === 'list') return block.items.map(richTextToPlain).join('\n');
      if (block.type === 'code') return block.code;
      if (block.type === 'math') return block.latex;
      if (block.type === 'table')
        return block.rows.map((row) => row.map(richTextToPlain).join('\t')).join('\n');
      if (block.type === 'image') return block.alt;
      if (block.type === 'file') return block.name;
      return '';
    })
    .filter(Boolean)
    .join('\n');
}

export function countWords(doc: BlockDoc | null | undefined): number {
  return docToPlainText(doc).split(/\s+/).filter(Boolean).length;
}

export type DocIssue = {
  blockId: string;
  code: 'image_alt_required' | 'embed_url_invalid' | 'video_source_required';
};

const HTTP_URL = /^https?:\/\/\S+$/i;

/** Client-side guard before saving (NFR-A11Y-01: images must have alt). Server still sanitizes. */
export function validateDoc(doc: BlockDoc): DocIssue[] {
  const issues: DocIssue[] = [];
  for (const block of doc.blocks) {
    if (block.type === 'image' && !block.alt.trim())
      issues.push({ blockId: block.id, code: 'image_alt_required' });
    if (block.type === 'embed' && !HTTP_URL.test(block.url))
      issues.push({ blockId: block.id, code: 'embed_url_invalid' });
    if (block.type === 'video' && !block.fileId && !block.embedUrl)
      issues.push({ blockId: block.id, code: 'video_source_required' });
  }
  return issues;
}

export function replaceBlock(doc: BlockDoc, blockId: string, next: Block): BlockDoc {
  return { ...doc, blocks: doc.blocks.map((block) => (block.id === blockId ? next : block)) };
}

export function insertBlocksAfter(
  doc: BlockDoc,
  afterId: string | null,
  blocks: Block[],
): BlockDoc {
  if (afterId === null) return { ...doc, blocks: [...blocks, ...doc.blocks] };
  const index = doc.blocks.findIndex((block) => block.id === afterId);
  const at = index === -1 ? doc.blocks.length : index + 1;
  return { ...doc, blocks: [...doc.blocks.slice(0, at), ...blocks, ...doc.blocks.slice(at)] };
}

export function removeBlock(doc: BlockDoc, blockId: string): BlockDoc {
  return { ...doc, blocks: doc.blocks.filter((block) => block.id !== blockId) };
}

export function moveBlock(doc: BlockDoc, blockId: string, delta: -1 | 1): BlockDoc {
  const index = doc.blocks.findIndex((block) => block.id === blockId);
  const target = index + delta;
  if (index === -1 || target < 0 || target >= doc.blocks.length) return doc;
  const blocks = [...doc.blocks];
  [blocks[index], blocks[target]] = [blocks[target] as Block, blocks[index] as Block];
  return { ...doc, blocks };
}

export function blockForUploadedFile(file: { id: string; name: string; mime: string }): Block {
  const id = localId();
  if (file.mime.startsWith('image/')) return { id, type: 'image', fileId: file.id, alt: '' };
  if (file.mime.startsWith('video/')) return { id, type: 'video', fileId: file.id };
  return { id, type: 'file', fileId: file.id, name: file.name };
}
