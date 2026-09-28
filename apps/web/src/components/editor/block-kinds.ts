import {
  Code2,
  File,
  Heading1,
  Heading2,
  Heading3,
  Image,
  Link2,
  List,
  ListOrdered,
  MessageSquareWarning,
  Pilcrow,
  Quote,
  Sigma,
  Table,
  Video,
  type LucideIcon,
} from 'lucide-react';
import type { Block, BlockType } from '@/lib/api/schemas/blockdoc';
import { convertBlock, createBlock } from '@/lib/blockdoc/doc';

/** What the "+" / "/" picker can insert. Upload kinds open a file dialog instead of creating a block. */
export const BLOCK_KINDS = [
  'paragraph',
  'heading1',
  'heading2',
  'heading3',
  'bullets',
  'numbered',
  'quote',
  'callout',
  'code',
  'math',
  'table',
  'image',
  'file',
  'video',
  'embed',
] as const;
export type BlockKind = (typeof BLOCK_KINDS)[number];

export const UPLOAD_KINDS: Partial<Record<BlockKind, string>> = { image: 'image/*', file: '*/*' };

export const BLOCK_KIND_ICONS: Record<BlockKind, LucideIcon> = {
  paragraph: Pilcrow,
  heading1: Heading1,
  heading2: Heading2,
  heading3: Heading3,
  bullets: List,
  numbered: ListOrdered,
  quote: Quote,
  callout: MessageSquareWarning,
  code: Code2,
  math: Sigma,
  table: Table,
  image: Image,
  file: File,
  video: Video,
  embed: Link2,
};

export const BLOCK_KIND_TYPE: Record<BlockKind, BlockType> = {
  paragraph: 'paragraph',
  heading1: 'heading',
  heading2: 'heading',
  heading3: 'heading',
  bullets: 'list',
  numbered: 'list',
  quote: 'quote',
  callout: 'callout',
  code: 'code',
  math: 'math',
  table: 'table',
  image: 'image',
  file: 'file',
  video: 'video',
  embed: 'embed',
};

/** Minimal set for forum posts, feedback and answers. */
export const COMPACT_BLOCK_KINDS: readonly BlockKind[] = [
  'paragraph',
  'bullets',
  'numbered',
  'quote',
  'code',
  'math',
  'image',
  'file',
];

export function blockFromKind(kind: BlockKind): Block | null {
  if (UPLOAD_KINDS[kind]) return null;
  switch (kind) {
    case 'heading1':
    case 'heading2':
    case 'heading3': {
      const level = Number(kind.slice(-1)) as 1 | 2 | 3;
      return { ...(createBlock('heading') as Extract<Block, { type: 'heading' }>), level };
    }
    case 'bullets':
      return createBlock('list');
    case 'numbered':
      return { ...(createBlock('list') as Extract<Block, { type: 'list' }>), ordered: true };
    default:
      return createBlock(BLOCK_KIND_TYPE[kind] as Exclude<BlockType, 'image' | 'file'>);
  }
}

const HEADING_LEVEL_BY_KIND: Partial<Record<BlockKind, 1 | 2 | 3>> = {
  heading1: 1,
  heading2: 2,
  heading3: 3,
};

/** "Turn into": converts content-bearing blocks, applying heading level / list ordering of the kind. */
export function turnInto(block: Block, kind: BlockKind): Block {
  const converted = convertBlock(block, BLOCK_KIND_TYPE[kind]);
  const level = HEADING_LEVEL_BY_KIND[kind];
  if (converted.type === 'heading' && level) return { ...converted, level };
  if (converted.type === 'list') return { ...converted, ordered: kind === 'numbered' };
  return converted;
}
