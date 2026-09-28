/** Quick comment templates for the grading screen, stored per browser (no contract endpoint yet — FR-ASSIGN-09 is P1). */
const STORAGE_KEY = 'tc:quick-comments:v1';

export function loadQuickComments(defaults: string[]): string[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    const parsed: unknown = raw ? JSON.parse(raw) : null;
    return Array.isArray(parsed) && parsed.every((entry) => typeof entry === 'string')
      ? parsed
      : defaults;
  } catch {
    return defaults;
  }
}

export function saveQuickComments(comments: string[]): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(comments));
  } catch (error) {
    console.warn('[quick-comments] not saved', error instanceof Error ? error.name : 'unknown');
  }
}

/** Essay queue ids are `attemptId:slot` (contract §8). */
export function parseEssayId(id: string): { attemptId: string; slot: number } | null {
  const separator = id.lastIndexOf(':');
  if (separator <= 0) return null;
  const slot = Number(id.slice(separator + 1));
  return Number.isInteger(slot) ? { attemptId: id.slice(0, separator), slot } : null;
}
