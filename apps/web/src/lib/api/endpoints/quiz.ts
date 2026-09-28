import { z } from 'zod';
import { http } from '../http';
import { pageSchema } from '../schemas/common';
import {
  attemptResultSchema,
  attemptSchema,
  attemptSummarySchema,
  previewCheckResultSchema,
  qCategorySchema,
  questionSchema,
  questionSummarySchema,
  questionVersionSchema,
  quizSlotsResponseSchema,
  saveAnswerResultSchema,
  type QuestionInput,
  type QuestionResponse,
  type QuizSlot,
} from '../schemas/quiz';

export type QuestionsQuery = {
  categoryId?: string;
  tag?: string;
  type?: string;
  q?: string;
  cursor?: string | null;
};
export type QuizOverrideInput = {
  userId?: string;
  groupId?: string;
  openAt?: string;
  closeAt?: string;
  timeLimitSec?: number;
  maxAttempts?: number;
};

export const quizApi = {
  categories: (courseId: string) =>
    http.request(`/courses/${courseId}/question-bank/categories`, {
      schema: z.array(qCategorySchema),
    }),
  createCategory: (courseId: string, body: { name: string; parentId?: string | null }) =>
    http.request(`/courses/${courseId}/question-bank/categories`, {
      method: 'POST',
      body,
      schema: qCategorySchema,
    }),
  questions: (courseId: string, query: QuestionsQuery) =>
    http.request(`/courses/${courseId}/questions`, {
      query,
      schema: pageSchema(questionSummarySchema),
    }),
  createQuestion: (courseId: string, body: QuestionInput) =>
    http.request(`/courses/${courseId}/questions`, {
      method: 'POST',
      body,
      schema: questionSchema,
    }),
  question: (id: string) => http.request(`/questions/${id}`, { schema: questionSchema }),
  updateQuestion: (id: string, body: QuestionInput) =>
    http.request(`/questions/${id}`, { method: 'PUT', body, schema: questionSchema }),
  deleteQuestion: (id: string) => http.request(`/questions/${id}`, { method: 'DELETE' }),
  versions: (id: string) =>
    http.request(`/questions/${id}/versions`, { schema: z.array(questionVersionSchema) }),
  previewCheck: (id: string, response: QuestionResponse) =>
    http.request(`/questions/${id}/preview-check`, {
      method: 'POST',
      body: { response },
      schema: previewCheckResultSchema,
    }),
  slots: (itemId: string) =>
    http.request(`/items/${itemId}/quiz/slots`, { schema: quizSlotsResponseSchema }),
  saveSlots: (itemId: string, slots: QuizSlot[]) =>
    http.request(`/items/${itemId}/quiz/slots`, { method: 'PUT', body: { slots } }),
  startAttempt: (itemId: string) =>
    http.request(`/items/${itemId}/attempts`, { method: 'POST', schema: attemptSchema }),
  attempt: (id: string) => http.request(`/attempts/${id}`, { schema: attemptSchema }),
  saveAnswer: (
    attemptId: string,
    slot: number,
    body: { response: QuestionResponse; flagged?: boolean },
  ) =>
    http.request(`/attempts/${attemptId}/answers/${slot}`, {
      method: 'PUT',
      body,
      schema: saveAnswerResultSchema,
    }),
  finish: (attemptId: string, idempotencyKey: string) =>
    http.request(`/attempts/${attemptId}/finish`, {
      method: 'POST',
      idempotencyKey,
      schema: attemptResultSchema,
    }),
  result: (attemptId: string) =>
    http.request(`/attempts/${attemptId}/result`, { schema: attemptResultSchema }),
  attempts: (itemId: string, cursor?: string | null) =>
    http.request(`/items/${itemId}/attempts`, {
      query: { cursor },
      schema: pageSchema(attemptSummarySchema),
    }),
  regrade: (itemId: string) =>
    http.request(`/items/${itemId}/regrade`, {
      method: 'POST',
      schema: z.object({ regraded: z.number() }),
    }),
  override: (itemId: string, body: QuizOverrideInput) =>
    http.request(`/items/${itemId}/overrides`, { method: 'POST', body }),
  gradeEssay: (attemptId: string, slot: number, body: { score: number; comment?: string }) =>
    http.request(`/attempts/${attemptId}/answers/${slot}/grade`, { method: 'POST', body }),
};
