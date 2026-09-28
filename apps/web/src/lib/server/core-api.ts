import 'server-only';

const DEFAULT_CORE_API_URL = 'http://localhost:8080';

/** Read at request time (not build time) so the standalone image honours runtime CORE_API_URL. */
export function coreApiUrl(): string {
  return (process.env.CORE_API_URL ?? DEFAULT_CORE_API_URL).replace(/\/+$/, '');
}
