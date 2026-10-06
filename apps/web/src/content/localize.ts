import type { LocalizedText } from './landing';

/** Picks the visitor's language from a `{ ru, en, uz }` content entry (falls back to Russian). */
export function localize(text: LocalizedText, locale: string): string {
  if (locale.startsWith('en')) return text.en;
  if (locale.startsWith('uz')) return text.uz;
  return text.ru;
}
