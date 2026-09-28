/**
 * Per-browser list of recently inserted formulas (teacher convenience). Browser storage can be
 * unavailable (private mode, blocked site data), so every access degrades to an empty list.
 */
export const RECENT_FORMULAS_KEY = 'tutorcraft.math.recent';
export const MAX_RECENT_FORMULAS = 16;

function storage(): Storage | null {
  try {
    return typeof window === 'undefined' ? null : window.localStorage;
  } catch {
    return null;
  }
}

function errorName(error: unknown): string {
  return error instanceof Error ? error.name : 'unknown';
}

export function readRecentFormulas(): string[] {
  try {
    const raw = storage()?.getItem(RECENT_FORMULAS_KEY);
    const parsed: unknown = raw ? JSON.parse(raw) : [];
    if (!Array.isArray(parsed)) return [];
    return parsed
      .filter((item): item is string => typeof item === 'string')
      .slice(0, MAX_RECENT_FORMULAS);
  } catch (error) {
    console.warn('[math] recent formulas unreadable', errorName(error));
    return [];
  }
}

export function rememberFormula(latex: string): string[] {
  const trimmed = latex.trim();
  if (!trimmed) return readRecentFormulas();
  const next = [trimmed, ...readRecentFormulas().filter((item) => item !== trimmed)].slice(
    0,
    MAX_RECENT_FORMULAS,
  );
  try {
    storage()?.setItem(RECENT_FORMULAS_KEY, JSON.stringify(next));
  } catch (error) {
    console.warn('[math] recent formulas not saved', errorName(error));
  }
  return next;
}
