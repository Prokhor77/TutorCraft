import { describe, expect, it } from 'vitest';
import { blockDocSchema, type Block, type BlockDoc } from '@/lib/api/schemas/blockdoc';
import {
  blockForUploadedFile,
  convertBlock,
  countWords,
  docFromText,
  docToPlainText,
  emptyDoc,
  insertBlocksAfter,
  isDocEmpty,
  moveBlock,
  removeBlock,
  validateDoc,
} from './doc';

const para = (id: string, text: string): Block => ({ id, type: 'paragraph', text: [{ text }] });
const doc = (...blocks: Block[]): BlockDoc => ({ schemaVersion: 1, blocks });

describe('blockDocSchema', () => {
  it('drops unknown or malformed blocks instead of failing the document', () => {
    const parsed = blockDocSchema.parse({
      schemaVersion: 1,
      blocks: [
        { id: 'a', type: 'paragraph', text: [{ text: 'ok' }] },
        { id: 'b', type: 'hologram' },
        { id: 'c', type: 'image', fileId: 'f' },
      ],
    });
    expect(parsed.blocks.map((block) => block.id)).toEqual(['a']);
  });

  it('rejects an unknown schema version', () => {
    expect(blockDocSchema.safeParse({ schemaVersion: 2, blocks: [] }).success).toBe(false);
  });
});

describe('validateDoc', () => {
  it('requires alt text on images (NFR-A11Y-01), valid embed URLs and a video source', () => {
    const issues = validateDoc(
      doc(
        { id: 'i1', type: 'image', fileId: 'f', alt: '  ' },
        { id: 'i2', type: 'image', fileId: 'f', alt: 'Graph' },
        { id: 'e1', type: 'embed', url: 'ftp://x' },
        { id: 'v1', type: 'video' },
      ),
    );
    expect(issues).toEqual([
      { blockId: 'i1', code: 'image_alt_required' },
      { blockId: 'e1', code: 'embed_url_invalid' },
      { blockId: 'v1', code: 'video_source_required' },
    ]);
  });
});

describe('document operations', () => {
  it('inserts, moves and removes blocks immutably', () => {
    const base = doc(para('a', 'A'), para('b', 'B'));
    const inserted = insertBlocksAfter(base, 'a', [para('x', 'X')]);
    expect(inserted.blocks.map((block) => block.id)).toEqual(['a', 'x', 'b']);
    expect(insertBlocksAfter(base, null, [para('x', 'X')]).blocks[0]?.id).toBe('x');
    expect(moveBlock(inserted, 'b', -1).blocks.map((block) => block.id)).toEqual(['a', 'b', 'x']);
    expect(moveBlock(inserted, 'a', -1)).toBe(inserted);
    expect(removeBlock(inserted, 'x').blocks).toHaveLength(2);
    expect(base.blocks).toHaveLength(2);
  });

  it('converts text blocks while keeping content', () => {
    const heading = convertBlock(para('a', 'Title'), 'heading');
    expect(heading).toEqual({ id: 'a', type: 'heading', level: 2, text: [{ text: 'Title' }] });
    expect(convertBlock(heading, 'list')).toEqual({
      id: 'a',
      type: 'list',
      ordered: false,
      items: [[{ text: 'Title' }]],
    });
    expect(convertBlock(heading, 'code')).toEqual({
      id: 'a',
      type: 'code',
      language: 'plaintext',
      code: 'Title',
    });
  });

  it('creates blocks for uploaded files by MIME type', () => {
    expect(blockForUploadedFile({ id: 'f1', name: 'a.png', mime: 'image/png' })).toMatchObject({
      type: 'image',
      fileId: 'f1',
      alt: '',
    });
    expect(blockForUploadedFile({ id: 'f2', name: 'v.mp4', mime: 'video/mp4' })).toMatchObject({
      type: 'video',
      fileId: 'f2',
    });
    expect(
      blockForUploadedFile({ id: 'f3', name: 'a.pdf', mime: 'application/pdf' }),
    ).toMatchObject({ type: 'file', fileId: 'f3', name: 'a.pdf' });
  });

  it('extracts plain text and counts words', () => {
    const content = doc(
      para('a', 'Hello world'),
      { id: 'l', type: 'list', ordered: true, items: [[{ text: 'one' }], [{ text: 'two' }]] },
      { id: 'm', type: 'math', latex: 'x^2' },
    );
    expect(docToPlainText(content)).toBe('Hello world\none\ntwo\nx^2');
    expect(countWords(content)).toBe(5);
  });

  it('detects empty documents', () => {
    expect(isDocEmpty(emptyDoc())).toBe(true);
    expect(isDocEmpty(docFromText(''))).toBe(true);
    expect(isDocEmpty(doc(para('a', '   ')))).toBe(true);
    expect(isDocEmpty(docFromText('x'))).toBe(false);
  });
});
