import { z } from 'zod';
import { idSchema, instantSchema } from './common';

export const FILE_PURPOSES = [
  'content',
  'submission',
  'avatar',
  'cover',
  'video',
  'import',
] as const;
export type FilePurpose = (typeof FILE_PURPOSES)[number];

export const fileMetaSchema = z.object({
  id: idSchema,
  name: z.string(),
  size: z.number(),
  mime: z.string(),
  status: z.enum(['pending', 'ready', 'rejected']),
  url: z.string().nullable(),
  video: z
    .object({
      status: z.enum(['processing', 'ready', 'failed']),
      hlsUrl: z.string().nullable(),
      durationSec: z.number().nullable(),
    })
    .optional(),
});
export type FileMeta = z.infer<typeof fileMetaSchema>;

export const uploadTicketSchema = z.object({
  fileId: idSchema,
  uploadUrl: z.string(),
  headers: z.record(z.string()),
  expiresAt: instantSchema,
});
export type UploadTicket = z.infer<typeof uploadTicketSchema>;
