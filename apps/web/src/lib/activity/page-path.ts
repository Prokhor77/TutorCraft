/** Mask used for path segments that may carry secrets (invite tokens and the like). */
export const MASKED_SEGMENT = ':token';

const MAX_PAGE_LENGTH = 300;
/** Opaque identifiers this long are treated as secrets; UUIDs (36 chars) stay visible. */
const SECRET_SEGMENT_LENGTH = 40;
/** Routes whose next segment is a secret: `/join/[token]`. */
const SECRET_PARENTS = new Set(['join']);

/** Sanitized path of the page the browser is on (undefined during SSR). */
export function currentPagePath(): string | undefined {
  if (typeof window === 'undefined') return undefined;
  return sanitizePagePath(window.location.pathname);
}

/**
 * UI page as recorded in the activity log: path only (query strings may hold reset tokens), secret segments masked,
 * length capped to what the API accepts.
 */
export function sanitizePagePath(pathname: string): string {
  const [path = '/'] = pathname.split(/[?#]/, 1);
  const segments = path.split('/').map((segment, index, all) => {
    if (!segment) return segment;
    const parent = index > 0 ? all[index - 1] : undefined;
    if (parent && SECRET_PARENTS.has(parent)) return MASKED_SEGMENT;
    return segment.length >= SECRET_SEGMENT_LENGTH ? MASKED_SEGMENT : segment;
  });
  const sanitized = segments.join('/') || '/';
  return sanitized.startsWith('/') ? sanitized.slice(0, MAX_PAGE_LENGTH) : '/';
}
