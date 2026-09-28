import type { ItemType } from '@/lib/api/schemas/common';
import { SECONDS_PER_MINUTE } from '@/lib/utils/time';

/** Key fields shown in quick creation (UX-01: ≤ 5 fields; server fills the rest with AC-2 defaults). */
export type QuickCreateValues = {
  title: string;
  dueAt?: string | null;
  closeAt?: string | null;
  maxScore?: number;
  submissionType?: 'file' | 'text' | 'both' | 'none';
  timeLimitMinutes?: number | null;
  maxAttempts?: number | null;
  url?: string;
  embedUrl?: string;
  fileId?: string | null;
  forumType?: 'general' | 'qa' | 'announcements';
};

export const QUICK_FIELDS: Record<ItemType, readonly (keyof QuickCreateValues)[]> = {
  page: [],
  file: ['fileId'],
  url: ['url'],
  folder: [],
  video: ['fileId', 'embedUrl'],
  assignment: ['dueAt', 'submissionType', 'maxScore'],
  quiz: ['closeAt', 'timeLimitMinutes', 'maxAttempts'],
  forum: ['forumType'],
};

/** Builds the partial `settings` payload for POST /modules/{id}/items. Unset fields are omitted. */
export function quickSettings(
  type: ItemType,
  values: QuickCreateValues,
): Record<string, unknown> | undefined {
  const defined = (entries: Record<string, unknown>) =>
    Object.fromEntries(
      Object.entries(entries).filter(
        ([, value]) => value !== undefined && value !== '' && value !== null,
      ),
    );
  switch (type) {
    case 'assignment':
      return {
        kind: type,
        ...defined({
          dueAt: values.dueAt,
          submissionType: values.submissionType,
          maxScore: values.maxScore,
        }),
      };
    case 'quiz':
      return {
        kind: type,
        ...defined({
          closeAt: values.closeAt,
          timeLimitSec: values.timeLimitMinutes
            ? values.timeLimitMinutes * SECONDS_PER_MINUTE
            : undefined,
          maxAttempts: values.maxAttempts,
        }),
      };
    case 'url':
      return { kind: type, url: values.url ?? '' };
    case 'file':
      return { kind: type, fileId: values.fileId ?? null };
    case 'video':
      return { kind: type, fileId: values.fileId ?? null, embedUrl: values.embedUrl || null };
    case 'forum':
      return { kind: type, ...defined({ forumType: values.forumType }) };
    case 'folder':
      return { kind: type, fileIds: [] };
    default:
      return undefined;
  }
}
