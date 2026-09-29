'use client';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { quizApi, type QuestionsQuery } from '@/lib/api/endpoints/quiz';
import { getNextCursor } from '@/lib/api/pagination';
import type { QuestionInput, QuestionResponse } from '@/lib/api/schemas/quiz';
import { queryKeys } from '../query-keys';

export function useQCategories(courseId: string) {
  return useQuery({
    queryKey: queryKeys.qCategories(courseId),
    queryFn: () => quizApi.categories(courseId),
  });
}

export function useQuestions(courseId: string, params: Omit<QuestionsQuery, 'cursor'>) {
  return useInfiniteQuery({
    queryKey: queryKeys.questions(courseId, params),
    queryFn: ({ pageParam }) => quizApi.questions(courseId, { ...params, cursor: pageParam }),
    initialPageParam: null as string | null,
    getNextPageParam: getNextCursor,
  });
}

export function useQuestion(id: string | null) {
  return useQuery({
    queryKey: queryKeys.question(id ?? 'new'),
    queryFn: () => quizApi.question(id as string),
    enabled: !!id,
  });
}

export function useQuestionVersions(id: string | null) {
  return useQuery({
    queryKey: queryKeys.questionVersions(id ?? 'new'),
    queryFn: () => quizApi.versions(id as string),
    enabled: !!id,
  });
}

export function useQbankMutations(courseId: string) {
  const queryClient = useQueryClient();
  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.questionsRoot(courseId) });
    void queryClient.invalidateQueries({ queryKey: queryKeys.qCategories(courseId) });
  };
  return {
    createCategory: useMutation({
      mutationFn: (input: { name: string; parentId?: string | null }) =>
        quizApi.createCategory(courseId, input),
      onSuccess: invalidate,
    }),
    create: useMutation({
      mutationFn: (input: QuestionInput) => quizApi.createQuestion(courseId, input),
      onSuccess: (question) => {
        queryClient.setQueryData(queryKeys.question(question.id), question);
        invalidate();
      },
    }),
    update: useMutation({
      mutationFn: ({ id, input }: { id: string; input: QuestionInput }) =>
        quizApi.updateQuestion(id, input),
      onSuccess: (question) => {
        queryClient.setQueryData(queryKeys.question(question.id), question);
        void queryClient.invalidateQueries({ queryKey: queryKeys.questionVersions(question.id) });
        invalidate();
      },
    }),
    remove: useMutation({
      mutationFn: (id: string) => quizApi.deleteQuestion(id),
      onSuccess: invalidate,
    }),
    previewCheck: useMutation({
      mutationFn: ({ id, response }: { id: string; response: QuestionResponse }) =>
        quizApi.previewCheck(id, response),
    }),
  };
}
