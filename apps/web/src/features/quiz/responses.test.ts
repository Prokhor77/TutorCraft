import { describe, expect, it } from 'vitest';
import type { StudentQuestionView } from '@/lib/api/schemas/quiz';
import {
  currentOrder,
  describeResponse,
  essayResponse,
  essayText,
  isAnswered,
  moveInOrder,
  setMatch,
  toggleOptionId,
} from './responses';

const base = {
  slot: 1,
  page: 0,
  points: 1,
  title: 'Q',
  body: { schemaVersion: 1 as const, blocks: [] },
  response: null,
  flagged: false,
};

describe('quiz responses', () => {
  it('detects answered responses of every type', () => {
    expect(isAnswered(null)).toBe(false);
    expect(isAnswered({ optionId: 'a' })).toBe(true);
    expect(isAnswered({ optionIds: [] })).toBe(false);
    expect(isAnswered({ value: false })).toBe(true);
    expect(isAnswered({ text: '  ' })).toBe(false);
    expect(isAnswered({ number: 0 })).toBe(true);
    expect(isAnswered(essayResponse(''))).toBe(false);
    expect(isAnswered(essayResponse('An answer'))).toBe(true);
    expect(isAnswered({ matches: { p1: '' } })).toBe(false);
    expect(isAnswered({ order: ['a'] })).toBe(true);
  });

  it('toggles multiple choice options and sets matches', () => {
    const first = toggleOptionId(null, 'a');
    expect(first).toEqual({ optionIds: ['a'] });
    expect(toggleOptionId(toggleOptionId(first, 'b'), 'a')).toEqual({ optionIds: ['b'] });
    expect(setMatch(setMatch(null, 'p1', 'x'), 'p2', 'y')).toEqual({
      matches: { p1: 'x', p2: 'y' },
    });
  });

  it('reorders ordering items starting from the shuffled server order', () => {
    const question: StudentQuestionView = {
      ...base,
      type: 'ordering',
      items: [
        { id: 'b', text: 'B' },
        { id: 'a', text: 'A' },
      ],
    };
    expect(currentOrder(question)).toEqual(['b', 'a']);
    expect(moveInOrder(['b', 'a'], 'a', -1)).toEqual(['a', 'b']);
    expect(moveInOrder(['b', 'a'], 'b', -1)).toEqual(['b', 'a']);
  });

  it('keeps essay text through the BlockDoc response', () => {
    expect(essayText(essayResponse('Line one\n\nLine two'))).toBe('Line one\n\nLine two');
  });

  it('describes responses with option labels for results', () => {
    const question: StudentQuestionView = {
      ...base,
      type: 'single_choice',
      options: [{ id: 'a', text: 'Paris' }],
    };
    const labels = { true: 'Верно', false: 'Неверно' };
    expect(describeResponse({ optionId: 'a' }, question, labels)).toBe('Paris');
    expect(describeResponse({ value: false }, undefined, labels)).toBe('Неверно');
    expect(describeResponse(null, undefined, labels)).toBe('—');
  });
});
