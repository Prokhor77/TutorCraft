import type { QuestionResponse, StudentQuestionView } from '@/lib/api/schemas/quiz';
import { docToPlainText, docFromText } from '@/lib/blockdoc/doc';

/** Pure helpers for building/reading QuestionResponse values (contract §10). */
export function isAnswered(response: QuestionResponse | null | undefined): boolean {
  if (!response) return false;
  if ('optionId' in response) return !!response.optionId;
  if ('optionIds' in response) return response.optionIds.length > 0;
  if ('value' in response) return typeof response.value === 'boolean';
  if ('text' in response) return response.text.trim().length > 0;
  if ('number' in response) return Number.isFinite(response.number);
  if ('essay' in response) return docToPlainText(response.essay).trim().length > 0;
  if ('matches' in response) return Object.values(response.matches).some(Boolean);
  if ('order' in response) return response.order.length > 0;
  return false;
}

export function toggleOptionId(
  response: QuestionResponse | null,
  optionId: string,
): QuestionResponse {
  const current = response && 'optionIds' in response ? response.optionIds : [];
  return {
    optionIds: current.includes(optionId)
      ? current.filter((id) => id !== optionId)
      : [...current, optionId],
  };
}

export function setMatch(
  response: QuestionResponse | null,
  promptId: string,
  answer: string,
): QuestionResponse {
  const current = response && 'matches' in response ? response.matches : {};
  return { matches: { ...current, [promptId]: answer } };
}

/** Current ordering: saved response or the (server-shuffled) initial item order. */
export function currentOrder(question: StudentQuestionView): string[] {
  if (question.response && 'order' in question.response && question.response.order.length)
    return question.response.order;
  return (question.items ?? []).map((item) => item.id);
}

export function moveInOrder(order: string[], id: string, delta: -1 | 1): string[] {
  const index = order.indexOf(id);
  const target = index + delta;
  if (index === -1 || target < 0 || target >= order.length) return order;
  const next = [...order];
  [next[index], next[target]] = [next[target] as string, next[index] as string];
  return next;
}

export function essayResponse(text: string, fileIds?: string[]): QuestionResponse {
  return { essay: docFromText(text), ...(fileIds?.length ? { fileIds } : {}) };
}

export function essayText(response: QuestionResponse | null): string {
  return response && 'essay' in response ? docToPlainText(response.essay) : '';
}

/** Human-readable response for results (maps option ids to text using the attempt's question view). */
export function describeResponse(
  response: QuestionResponse | null | undefined,
  question: StudentQuestionView | undefined,
  labels: { true: string; false: string },
): string {
  if (!response) return '—';
  const optionText = (id: string) =>
    question?.options?.find((option) => option.id === id)?.text ?? id;
  const itemText = (id: string) => question?.items?.find((item) => item.id === id)?.text ?? id;
  const promptText = (id: string) =>
    question?.prompts?.find((prompt) => prompt.id === id)?.text ?? id;
  if ('optionId' in response) return optionText(response.optionId);
  if ('optionIds' in response) return response.optionIds.map(optionText).join(', ');
  if ('value' in response) return response.value ? labels.true : labels.false;
  if ('text' in response) return response.text;
  if ('number' in response) return String(response.number);
  if ('essay' in response) return docToPlainText(response.essay);
  if ('matches' in response)
    return Object.entries(response.matches)
      .map(([prompt, answer]) => `${promptText(prompt)} → ${answer}`)
      .join('; ');
  if ('order' in response) return response.order.map(itemText).join(' → ');
  return '—';
}
