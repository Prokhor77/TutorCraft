import { isAppLocale, LOCALE_COOKIE, LOCALE_COOKIE_MAX_AGE_SEC } from '@/i18n/config';

/** Writes the locale cookie read by i18n/request.ts; returns true when it changed. */
export function persistLocale(locale: string): boolean {
  if (typeof document === 'undefined' || !isAppLocale(locale)) return false;
  const current = document.documentElement.lang;
  document.cookie = `${LOCALE_COOKIE}=${locale}; path=/; max-age=${LOCALE_COOKIE_MAX_AGE_SEC}; samesite=lax`;
  return current !== locale;
}
