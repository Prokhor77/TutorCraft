import type { QuizSlot } from '@/lib/api/schemas/quiz';

export type QuizSummary = {
  /** Questions a learner will see: fixed slots + `count` of each random slot. */
  questions: number;
  /** Sum of explicit slot points (per question for random slots). */
  points: number;
  /** True when some slot uses the question's default score (not in the slots payload): `points` is a lower bound. */
  partial: boolean;
};

/** Stitch quiz stat cards («Вопросов», «Итоговая шкала») from real slot data. */
export function quizSummary(slots: QuizSlot[]): QuizSummary {
  return slots.reduce<QuizSummary>(
    (acc, slot) => {
      const count = 'random' in slot ? slot.random.count : 1;
      return {
        questions: acc.questions + count,
        points: acc.points + (slot.points ?? 0) * count,
        partial: acc.partial || slot.points === undefined,
      };
    },
    { questions: 0, points: 0, partial: false },
  );
}
