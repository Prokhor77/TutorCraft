import type { Page } from './schemas/common';

export const DEFAULT_PAGE_LIMIT = 25;
export const MAX_PAGE_LIMIT = 100;

export type CursorParams = { cursor?: string | null; limit?: number };

/** Shared option for TanStack `useInfiniteQuery` with contract `Page<T>`. */
export function getNextCursor<T>(lastPage: Page<T>): string | undefined {
  return lastPage.nextCursor ?? undefined;
}

export function flattenPages<T>(pages: Page<T>[] | undefined): T[] {
  return pages?.flatMap((page) => page.items) ?? [];
}

/** Walks every page (for small exports / client-side bulk ops). Guarded against runaway loops. */
export async function fetchAllPages<T>(
  fetchPage: (cursor: string | null) => Promise<Page<T>>,
  maxPages = MAX_PAGE_LIMIT,
): Promise<T[]> {
  const items: T[] = [];
  let cursor: string | null = null;
  for (let pageIndex = 0; pageIndex < maxPages; pageIndex += 1) {
    const page = await fetchPage(cursor);
    items.push(...page.items);
    if (!page.nextCursor) break;
    cursor = page.nextCursor;
  }
  return items;
}
