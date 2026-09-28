import { describe, expect, it } from 'vitest';
import { quizSummary } from './quiz-summary';

describe('quizSummary (quiz builder stat cards)', () => {
  it('counts fixed and random questions and sums explicit points', () => {
    expect(
      quizSummary([
        { questionId: 'q1', points: 2, page: 1 },
        { random: { count: 3 }, points: 1, page: 1 },
      ]),
    ).toEqual({ questions: 4, points: 5, partial: false });
  });

  it('flags a lower bound when a slot relies on the default score', () => {
    expect(quizSummary([{ questionId: 'q1', page: 1 }])).toEqual({
      questions: 1,
      points: 0,
      partial: true,
    });
    expect(quizSummary([])).toEqual({ questions: 0, points: 0, partial: false });
  });
});
