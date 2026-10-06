import 'server-only';

/**
 * Public origin of the site and the indexing switch. Both are read at request time (not build time) so the
 * standalone image honours the runtime environment, like `CORE_API_URL`.
 *
 * - `SITE_URL` — canonical origin, e.g. `https://tutorcraft.ru` (falls back to `PUBLIC_BASE_URL`, then localhost).
 * - `SEO_INDEXING` — `true` / `false`. When unset, indexing is allowed only for a real domain: a bare IP or
 *   localhost would otherwise get indexed and later compete with the domain as a duplicate.
 */

const DEFAULT_SITE_URL = 'http://localhost:3000';
const IP_OR_LOCAL_HOST = /^(localhost|127\.\d+\.\d+\.\d+|\d+\.\d+\.\d+\.\d+|\[[0-9a-f:]+\])$/i;

export const SITE_NAME = 'TutorCraft';

export function siteUrl(): string {
  const raw = process.env.SITE_URL || process.env.PUBLIC_BASE_URL || DEFAULT_SITE_URL;
  return raw.replace(/\/+$/, '');
}

export function indexingAllowed(): boolean {
  const flag = process.env.SEO_INDEXING?.trim().toLowerCase();
  if (flag === 'true') return true;
  if (flag === 'false') return false;
  return !IP_OR_LOCAL_HOST.test(new URL(siteUrl()).hostname);
}

/** Absolute URL for a site path; the path is normalised to a leading slash and no trailing slash. */
export function absoluteUrl(path: string): string {
  return `${siteUrl()}${normalizePath(path)}`;
}

export function normalizePath(path: string): string {
  const withSlash = path.startsWith('/') ? path : `/${path}`;
  return withSlash.length > 1 ? withSlash.replace(/\/+$/, '') : withSlash;
}
