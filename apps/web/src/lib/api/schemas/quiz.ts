import { z } from 'zod';
import { blockDocSchema } from './blockdoc';
import { idSchema, instantSchema, nullableInstant } from './common';

export const QUESTION_TYPES = [
  'single_choice',
  'multiple_choice',
  'true_false',
  'short_answer',
  'numerical',
  'essay',
  'matching',
  'ordering',
] as const;
export const questionTypeSchema = z.enum(QUESTION_TYPES);
export type QuestionType = z.infer<typeof questionTypeSchema>;

// The API serialises unset optional fields as `null`; the client model uses `undefined`.
const optionalString = z
  .string()
  .nullish()
  .transform((value) => value ?? undefined);
const optionalNumber = z
  .number()
  .nullish()
  .transform((value) => value ?? undefined);

const choiceOption = z.object({
  id: z.string(),
  text: z.string(),
  correct: z.boolean(),
  feedback: optionalString,
});
export const MC_SCORING = ['all_or_nothing', 'partial', 'partial_with_penalty'] as const;

export const questionDataSchema = z.discriminatedUnion('type', [
  z.object({
    type: z.literal('single_choice'),
    options: z.array(choiceOption),
    shuffle: z.boolean(),
  }),
  z.object({
    type: z.literal('multiple_choice'),
    options: z.array(choiceOption),
    shuffle: z.boolean(),
    scoring: z.enum(MC_SCORING),
  }),
  z.object({ type: z.literal('true_false'), correct: z.boolean() }),
  z.object({
    type: z.literal('short_answer'),
    answers: z.array(z.object({ pattern: z.string(), scorePercent: z.number() })),
    caseSensitive: z.boolean(),
  }),
  z.object({
    type: z.literal('numerical'),
    answers: z.array(
      z.object({ value: z.number(), tolerance: z.number(), scorePercent: z.number() }),
    ),
  }),
  z.object({
    type: z.literal('essay'),
    responseFormat: z.enum(['text', 'text_and_files']),
    minWords: optionalNumber,
    maxWords: optionalNumber,
  }),
  z.object({
    type: z.literal('matching'),
    pairs: z.array(z.object({ id: z.string(), prompt: z.string(), answer: z.string() })),
    shuffle: z.boolean(),
  }),
  z.object({
    type: z.literal('ordering'),
    items: z.array(z.object({ id: z.string(), text: z.string() })),
  }),
]);
export type QuestionData = z.infer<typeof questionDataSchema>;
export type QuestionDataOf<T extends QuestionType> = Extract<QuestionData, { type: T }>;

export const questionResponseSchema = z.union([
  z.object({ optionId: z.string() }),
  z.object({ optionIds: z.array(z.string()) }),
  z.object({ value: z.boolean() }),
  z.object({ text: z.string() }),
  z.object({ number: z.number() }),
  z.object({ essay: blockDocSchema, fileIds: z.array(idSchema).optional() }),
  z.object({ matches: z.record(z.string()) }),
  z.object({ order: z.array(z.string()) }),
]);
export type QuestionResponse = z.infer<typeof questionResponseSchema>;

export const questionInputSchema = z.object({
  type: questionTypeSchema,
  title: z.string(),
  body: blockDocSchema,
  defaultScore: z.number(),
  categoryId: idSchema.nullable(),
  tags: z.array(z.string()),
  data: questionDataSchema,
  generalFeedback: blockDocSchema.nullable().optional(),
});
export type QuestionInput = z.infer<typeof questionInputSchema>;

export const questionSchema = questionInputSchema.extend({
  id: idSchema,
  version: z.number(),
  versionId: idSchema,
});
export type Question = z.infer<typeof questionSchema>;

export const questionSummarySchema = z.object({
  id: idSchema,
  type: questionTypeSchema,
  title: z.string(),
  tags: z.array(z.string()),
  version: z.number(),
  updatedAt: instantSchema,
  usedInQuizzes: z.number(),
});
export type QuestionSummary = z.infer<typeof questionSummarySchema>;

export const qCategorySchema = z.object({
  id: idSchema,
  parentId: idSchema.nullable(),
  name: z.string(),
  questionCount: z.number(),
});
export type QCategory = z.infer<typeof qCategorySchema>;

export const questionVersionSchema = z.object({
  version: z.number(),
  createdAt: instantSchema,
  id: idSchema,
});
export const previewCheckResultSchema = z.object({
  score: z.number(),
  maxScore: z.number(),
  correct: z.boolean(),
});
export type PreviewCheckResult = z.infer<typeof previewCheckResultSchema>;

// The API sends `null` for unset points / page / random filters; normalise to the client shape.
const slotPoints = optionalNumber;
const slotPage = z
  .number()
  .nullish()
  .transform((value) => value ?? 0);
const fixedSlot = z.object({
  questionId: idSchema,
  points: slotPoints,
  page: slotPage,
});
const randomSlot = z.object({
  random: z.object({
    categoryId: idSchema.nullish().transform((value) => value ?? undefined),
    tag: optionalString,
    count: z.number(),
  }),
  points: slotPoints,
  page: slotPage,
});
export const quizSlotSchema = z.union([fixedSlot, randomSlot]);
export type QuizSlot = z.infer<typeof quizSlotSchema>;

/** Client-side `page` value meaning "place by `questionsPerPage`"; the API encodes it as `null` (explicit pages are ≥ 1). */
export const AUTO_PAGE = 0;

/** Client slot → PUT /items/{id}/quiz/slots body item (inverse of the `null → 0` read normalisation). */
export function toQuizSlotPayload(slot: QuizSlot) {
  return { ...slot, page: slot.page > AUTO_PAGE ? slot.page : null };
}
/** GET /items/{id}/quiz/slots → "то же + разрешённые вопросы": assumed `{ slots, questions?: QuestionSummary[] }`. */
export const quizSlotsResponseSchema = z.object({
  slots: z.array(quizSlotSchema),
  questions: z.array(questionSummarySchema).optional(),
});
export type QuizSlotsResponse = z.infer<typeof quizSlotsResponseSchema>;

export const studentQuestionViewSchema = z.object({
  slot: z.number(),
  page: z.number(),
  points: z.number(),
  type: questionTypeSchema,
  title: z.string(),
  body: blockDocSchema,
  options: z.array(z.object({ id: z.string(), text: z.string() })).optional(),
  prompts: z.array(z.object({ id: z.string(), text: z.string() })).optional(),
  answerChoices: z.array(z.string()).optional(),
  items: z.array(z.object({ id: z.string(), text: z.string() })).optional(),
  responseFormat: z.enum(['text', 'text_and_files']).optional(),
  response: questionResponseSchema.nullable(),
  flagged: z.boolean(),
});
export type StudentQuestionView = z.infer<typeof studentQuestionViewSchema>;

export const ATTEMPT_STATES = ['in_progress', 'finished', 'abandoned'] as const;
export const attemptSchema = z.object({
  id: idSchema,
  itemId: idSchema,
  number: z.number(),
  state: z.enum(ATTEMPT_STATES),
  startedAt: instantSchema,
  timeDue: nullableInstant,
  serverNow: instantSchema,
  questions: z.array(studentQuestionViewSchema),
  totalPages: z.number(),
});
export type Attempt = z.infer<typeof attemptSchema>;

export const saveAnswerResultSchema = z.object({ savedAt: instantSchema });

export const attemptResultSchema = z.object({
  id: idSchema,
  state: z.literal('finished'),
  score: z.number().nullable(),
  maxScore: z.number(),
  percent: z.number().nullable(),
  passed: z.boolean().nullable(),
  needsManualGrading: z.boolean(),
  questions: z.array(
    z.object({
      slot: z.number(),
      title: z.string(),
      score: z.number().nullable(),
      points: z.number(),
      correct: z.boolean().nullable(),
      response: questionResponseSchema.nullable(),
      correctResponse: questionResponseSchema.nullish().transform((value) => value ?? undefined),
      feedback: optionalString,
    }),
  ),
});
export type AttemptResult = z.infer<typeof attemptResultSchema>;

export const attemptSummarySchema = z.object({
  id: idSchema,
  userId: idSchema,
  userName: z.string(),
  number: z.number(),
  state: z.string(),
  startedAt: instantSchema,
  finishedAt: nullableInstant,
  score: z.number().nullable(),
  maxScore: z.number(),
});
export type AttemptSummary = z.infer<typeof attemptSummarySchema>;
