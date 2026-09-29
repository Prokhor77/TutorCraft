import { describe, expect, it } from 'vitest';
import { isValidLayout, MAX_RANDOM_COUNT, MAX_SLOT_POINTS } from './quiz-layout';

const questionId = '0190a6f2-7b3c-7d4e-8f00-000000000001';

describe('isValidLayout', () => {
  it('accepts fixed and random slots within the server limits', () => {
    expect(
      isValidLayout([
        { questionId, page: 0 },
        { random: { tag: 'алгебра', count: 3 }, points: 2, page: 1 },
      ]),
    ).toBe(true);
  });

  it('rejects out-of-range points and a duplicated question', () => {
    expect(isValidLayout([{ questionId, page: 0, points: MAX_SLOT_POINTS + 1 }])).toBe(false);
    expect(isValidLayout([{ questionId, page: 0, points: -1 }])).toBe(false);
    expect(
      isValidLayout([
        { questionId, page: 0 },
        { questionId, page: 0 },
      ]),
    ).toBe(false);
  });

  it('rejects a random slot without a source or with a bad count', () => {
    expect(isValidLayout([{ random: { count: 2 }, page: 0 }])).toBe(false);
    expect(isValidLayout([{ random: { tag: 'x', count: MAX_RANDOM_COUNT + 1 }, page: 0 }])).toBe(
      false,
    );
  });
});
