import { z } from 'zod';
import { idSchema } from './common';

export const MARKS = ['bold', 'italic', 'code', 'strike', 'underline'] as const;
export const markSchema = z.enum(MARKS);
export type Mark = z.infer<typeof markSchema>;

export const richTextSpanSchema = z.object({
  text: z.string(),
  marks: z.array(markSchema).optional(),
  href: z.string().optional(),
});
export const richTextSchema = z.array(richTextSpanSchema);
export type RichTextSpan = z.infer<typeof richTextSpanSchema>;
export type RichText = z.infer<typeof richTextSchema>;

export const CALLOUT_TONES = ['info', 'warning', 'success'] as const;
export const HEADING_LEVELS = [1, 2, 3] as const;

export const blockSchema = z.discriminatedUnion('type', [
  z.object({
    id: z.string(),
    type: z.literal('heading'),
    level: z.union([z.literal(1), z.literal(2), z.literal(3)]),
    text: richTextSchema,
  }),
  z.object({ id: z.string(), type: z.literal('paragraph'), text: richTextSchema }),
  z.object({
    id: z.string(),
    type: z.literal('list'),
    ordered: z.boolean(),
    items: z.array(richTextSchema),
  }),
  z.object({ id: z.string(), type: z.literal('quote'), text: richTextSchema }),
  z.object({ id: z.string(), type: z.literal('code'), language: z.string(), code: z.string() }),
  z.object({ id: z.string(), type: z.literal('math'), latex: z.string() }),
  z.object({ id: z.string(), type: z.literal('table'), rows: z.array(z.array(richTextSchema)) }),
  z.object({
    id: z.string(),
    type: z.literal('image'),
    fileId: idSchema,
    alt: z.string(),
    caption: z.string().optional(),
  }),
  z.object({ id: z.string(), type: z.literal('file'), fileId: idSchema, name: z.string() }),
  z.object({
    id: z.string(),
    type: z.literal('video'),
    fileId: idSchema.optional(),
    embedUrl: z.string().optional(),
  }),
  z.object({ id: z.string(), type: z.literal('embed'), url: z.string() }),
  z.object({
    id: z.string(),
    type: z.literal('callout'),
    tone: z.enum(CALLOUT_TONES),
    text: richTextSchema,
  }),
]);
export type Block = z.infer<typeof blockSchema>;
export type BlockType = Block['type'];
export type BlockOf<T extends BlockType> = Extract<Block, { type: T }>;

export const BLOCK_DOC_SCHEMA_VERSION = 1 as const;

/**
 * BlockDoc (DATA-05). Unknown/invalid blocks are dropped instead of failing the whole document,
 * so a newer server schema never makes content unreadable.
 */
export const blockDocSchema = z.object({
  schemaVersion: z.literal(BLOCK_DOC_SCHEMA_VERSION),
  blocks: z.array(z.unknown()).transform((raw) =>
    raw.flatMap((candidate) => {
      const parsed = blockSchema.safeParse(candidate);
      return parsed.success ? [parsed.data] : [];
    }),
  ),
});
export type BlockDoc = { schemaVersion: typeof BLOCK_DOC_SCHEMA_VERSION; blocks: Block[] };
