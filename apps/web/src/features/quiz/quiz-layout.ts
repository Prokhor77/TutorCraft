import { AUTO_PAGE, type QuizSlot } from '@/lib/api/schemas/quiz';

/** Mirrors the core-api `QuizLayout` limits, so autosave never sends a composition the server will reject. */
export const MAX_SLOTS = 200;
export const MAX_SLOT_POINTS = 1000;
export const MAX_RANDOM_COUNT = 100;

function isValidSlot(slot: QuizSlot): boolean {
  if (slot.points !== undefined && (slot.points < 0 || slot.points > MAX_SLOT_POINTS)) return false;
  if (!Number.isInteger(slot.page) || slot.page < AUTO_PAGE) return false;
  if ('questionId' in slot) return true;
  const { count, categoryId, tag } = slot.random;
  return count >= 1 && count <= MAX_RANDOM_COUNT && (!!categoryId || !!tag?.trim());
}

export function isValidLayout(slots: QuizSlot[]): boolean {
  const questionIds = slots.flatMap((slot) => ('questionId' in slot ? [slot.questionId] : []));
  return (
    slots.length <= MAX_SLOTS &&
    new Set(questionIds).size === questionIds.length &&
    slots.every(isValidSlot)
  );
}
