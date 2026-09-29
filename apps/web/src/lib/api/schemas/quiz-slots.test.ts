import { describe, expect, it } from 'vitest';
import { AUTO_PAGE, quizSlotSchema, toQuizSlotPayload } from './quiz';

const questionId = '0190a6f2-7b3c-7d4e-8f00-000000000001';

describe('toQuizSlotPayload', () => {
  it('sends the automatic page as null (the API rejects page < 1)', () => {
    expect(toQuizSlotPayload({ questionId, page: AUTO_PAGE })).toEqual({ questionId, page: null });
  });

  it('keeps an explicit page', () => {
    expect(toQuizSlotPayload({ questionId, page: 2, points: 3 })).toEqual({
      questionId,
      page: 2,
      points: 3,
    });
  });

  it('round-trips a slot read from the API with page = null', () => {
    const slot = quizSlotSchema.parse({ questionId, points: null, page: null });
    expect(toQuizSlotPayload(slot).page).toBeNull();
  });
});
