import type { LocalizedText } from './landing';

/** Picks the visitor's language from a `{ ru, en }` content pair (falls back to Russian). */
export function localize(text: LocalizedText, locale: string): string {
  return locale.startsWith('en') ? text.en : text.ru;
}
