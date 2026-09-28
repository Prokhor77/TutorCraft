import type {
  QuestionData,
  QuestionInput,
  QuestionType,
  StudentQuestionView,
} from '@/lib/api/schemas/quiz';
import { emptyDoc } from '@/lib/blockdoc/doc';
import { localId } from '@/lib/utils/ids';

export const DEFAULT_QUESTION_SCORE = 1;
export const FULL_SCORE_PERCENT = 100;

/** Sensible starting data per type (UX-02) — two options, one correct, etc. */
export function defaultQuestionData(type: QuestionType): QuestionData {
  const option = (correct: boolean) => ({ id: localId('o'), text: '', correct });
  switch (type) {
    case 'single_choice':
      return { type, options: [option(true), option(false)], shuffle: true };
    case 'multiple_choice':
      return {
        type,
        options: [option(true), option(false), option(false)],
        shuffle: true,
        scoring: 'partial',
      };
    case 'true_false':
      return { type, correct: true };
    case 'short_answer':
      return {
        type,
        answers: [{ pattern: '', scorePercent: FULL_SCORE_PERCENT }],
        caseSensitive: false,
      };
    case 'numerical':
      return { type, answers: [{ value: 0, tolerance: 0, scorePercent: FULL_SCORE_PERCENT }] };
    case 'essay':
      return { type, responseFormat: 'text' };
    case 'matching':
      return {
        type,
        pairs: [
          { id: localId('p'), prompt: '', answer: '' },
          { id: localId('p'), prompt: '', answer: '' },
        ],
        shuffle: true,
      };
    case 'ordering':
      return {
        type,
        items: [
          { id: localId('i'), text: '' },
          { id: localId('i'), text: '' },
        ],
      };
  }
}

export function emptyQuestion(type: QuestionType, categoryId: string | null): QuestionInput {
  return {
    type,
    title: '',
    body: emptyDoc(),
    defaultScore: DEFAULT_QUESTION_SCORE,
    categoryId,
    tags: [],
    data: defaultQuestionData(type),
    generalFeedback: null,
  };
}

export type QuestionIssue =
  'title_required' | 'no_correct_option' | 'empty_option' | 'no_answers' | 'too_few_items';

/** Client validation before save (server remains authoritative). */
export function validateQuestion(input: QuestionInput): QuestionIssue[] {
  const issues: QuestionIssue[] = [];
  if (!input.title.trim()) issues.push('title_required');
  const data = input.data;
  if (data.type === 'single_choice' || data.type === 'multiple_choice') {
    if (!data.options.some((option) => option.correct)) issues.push('no_correct_option');
    if (data.options.some((option) => !option.text.trim())) issues.push('empty_option');
  }
  if (data.type === 'short_answer' && !data.answers.some((answer) => answer.pattern.trim()))
    issues.push('no_answers');
  if (data.type === 'numerical' && data.answers.length === 0) issues.push('no_answers');
  if (
    data.type === 'matching' &&
    data.pairs.filter((pair) => pair.prompt.trim() && pair.answer.trim()).length < 2
  )
    issues.push('too_few_items');
  if (data.type === 'ordering' && data.items.filter((item) => item.text.trim()).length < 2)
    issues.push('too_few_items');
  return issues;
}

/** Builds a student-like view (without keys) for preview-check (FR-QBANK-06). */
export function toPreviewView(input: QuestionInput): StudentQuestionView {
  const base = {
    slot: 0,
    page: 0,
    points: input.defaultScore,
    type: input.type,
    title: input.title,
    body: input.body,
    response: null,
    flagged: false,
  };
  const data = input.data;
  switch (data.type) {
    case 'single_choice':
    case 'multiple_choice':
      return { ...base, options: data.options.map(({ id, text }) => ({ id, text })) };
    case 'matching':
      return {
        ...base,
        prompts: data.pairs.map(({ id, prompt }) => ({ id, text: prompt })),
        answerChoices: [...data.pairs.map((pair) => pair.answer)].sort(),
      };
    case 'ordering':
      return { ...base, items: [...data.items].reverse() };
    case 'essay':
      return { ...base, responseFormat: data.responseFormat };
    default:
      return base;
  }
}
