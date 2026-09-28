export const LOCALES = ['ru', 'en'] as const;
export type AppLocale = (typeof LOCALES)[number];
export const DEFAULT_LOCALE: AppLocale = 'ru';
export const LOCALE_COOKIE = 'NEXT_LOCALE';
export const LOCALE_COOKIE_MAX_AGE_SEC = 60 * 60 * 24 * 365;

export function isAppLocale(value: string | undefined | null): value is AppLocale {
  return !!value && (LOCALES as readonly string[]).includes(value);
}

/** Picks the first supported language from an Accept-Language header. */
export function localeFromAcceptLanguage(header: string | null): AppLocale | null {
  if (!header) return null;
  const candidates = header
    .split(',')
    .map((part) => part.split(';')[0]?.trim().slice(0, 2).toLowerCase());
  return candidates.find(isAppLocale) ?? null;
}
