import { describe, expect, it } from 'vitest';
import { QUESTION_TYPES, questionDataSchema } from '@/lib/api/schemas/quiz';
import {
  defaultQuestionData,
  emptyQuestion,
  toPreviewView,
  validateQuestion,
} from './question-data';

describe('question data helpers (FR-QBANK-03/06)', () => {
  it('produces contract-valid default data for all 8 types', () => {
    for (const type of QUESTION_TYPES)
      expect(questionDataSchema.safeParse(defaultQuestionData(type)).success).toBe(true);
  });

  it('validates required parts of the answer key', () => {
    const question = emptyQuestion('single_choice', null);
    expect(validateQuestion(question)).toEqual(['title_required', 'empty_option']);
    const filled = {
      ...question,
      title: 'Q',
      data: {
        type: 'single_choice' as const,
        shuffle: true,
        options: [
          { id: 'a', text: 'A', correct: false },
          { id: 'b', text: 'B', correct: false },
        ],
      },
    };
    expect(validateQuestion(filled)).toEqual(['no_correct_option']);
  });

  it('never leaks correct answers into the preview view', () => {
    const question = { ...emptyQuestion('multiple_choice', null), title: 'Q' };
    const view = toPreviewView(question);
    expect(JSON.stringify(view)).not.toContain('correct');
    expect(view.options).toHaveLength(3);
    const ordering = toPreviewView({
      ...emptyQuestion('ordering', null),
      data: {
        type: 'ordering',
        items: [
          { id: '1', text: 'first' },
          { id: '2', text: 'second' },
        ],
      },
    });
    expect(ordering.items?.map((entry) => entry.id)).toEqual(['2', '1']);
  });
});
