'use client';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import { toast } from '@/components/ui/toast';
import { assessmentApi } from '@/lib/api/endpoints/assessment';
import {
  gradebookApi,
  type ExportFormat,
  type GradebookSetupInput,
} from '@/lib/api/endpoints/gradebook';
import type { GradeCell, Gradebook } from '@/lib/api/schemas/gradebook';
import { saveBlob } from '@/lib/utils/download';
import { queryKeys } from '../query-keys';

export function useGradebook(courseId: string, groupId?: string) {
  return useQuery({
    queryKey: queryKeys.gradebook(courseId, groupId),
    queryFn: () => gradebookApi.get(courseId, groupId),
  });
}

export function useGradebookSetup(courseId: string) {
  return useQuery({
    queryKey: queryKeys.gradebookSetup(courseId),
    queryFn: () => gradebookApi.setup(courseId),
  });
}

export function useGradeHistory(gradeId: string | null) {
  return useQuery({
    queryKey: queryKeys.gradeHistory(gradeId ?? 'none'),
    queryFn: () => gradebookApi.history(gradeId as string),
    enabled: !!gradeId,
  });
}

export type CellTarget = { gradeItemId: string; userId: string; cell: GradeCell | undefined };

function patchCell(
  book: Gradebook | undefined,
  target: CellTarget,
  next: Partial<GradeCell>,
): Gradebook | undefined {
  if (!book) return book;
  return {
    ...book,
    rows: book.rows.map((row) =>
      row.userId !== target.userId
        ? row
        : {
            ...row,
            cells: {
              ...row.cells,
              [target.gradeItemId]: {
                ...(row.cells[target.gradeItemId] ?? {
                  gradeId: null,
                  score: null,
                  overridden: false,
                  locked: false,
                  published: false,
                  version: 0,
                }),
                ...next,
              },
            },
          },
    ),
  };
}

/**
 * FR-GRADE-04 inline edit: PATCH /grades/{id} with If-Match when a grade exists, else PUT cells.
 * On 412 the gradebook is refetched and the user is told someone else changed the grade.
 */
export function useGradeCellMutation(courseId: string, groupId?: string) {
  const queryClient = useQueryClient();
  const key = queryKeys.gradebook(courseId, groupId);
  return useMutation({
    mutationFn: async ({
      target,
      score,
      locked,
    }: {
      target: CellTarget;
      score: number | null;
      locked?: boolean;
    }) => {
      if (target.cell?.gradeId)
        return gradebookApi.updateGrade(target.cell.gradeId, {
          score,
          locked,
          version: target.cell.version,
        });
      return gradebookApi.putCell(courseId, {
        gradeItemId: target.gradeItemId,
        userId: target.userId,
        score,
      });
    },
    onMutate: ({ target, score, locked }) => {
      const previous = queryClient.getQueryData<Gradebook>(key);
      queryClient.setQueryData<Gradebook>(key, (book) =>
        patchCell(book, target, {
          score,
          ...(locked === undefined ? {} : { locked }),
          overridden: true,
        }),
      );
      return { previous };
    },
    // 412 conflict.version is toasted globally (describeProblem); roll back and refetch the latest cell.
    onError: (_error, _vars, context) => {
      if (context?.previous) queryClient.setQueryData(key, context.previous);
    },
    onSettled: () =>
      void queryClient.invalidateQueries({ queryKey: queryKeys.gradebookRoot(courseId) }),
  });
}

export function useGradebookMutations(courseId: string) {
  const queryClient = useQueryClient();
  const t = useTranslations('gradebook');
  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.gradebookRoot(courseId) });
    void queryClient.invalidateQueries({ queryKey: queryKeys.gradebookSetup(courseId) });
  };
  return {
    saveSetup: useMutation({
      mutationFn: (input: GradebookSetupInput) => gradebookApi.saveSetup(courseId, input),
      onSuccess: (setup) => {
        queryClient.setQueryData(queryKeys.gradebookSetup(courseId), setup);
        invalidate();
      },
    }),
    addManualItem: useMutation({
      mutationFn: (input: { name: string; maxScore: number; categoryId?: string | null }) =>
        gradebookApi.createManualItem(courseId, input),
      onSuccess: invalidate,
    }),
    publishItem: useMutation({
      mutationFn: (itemId: string) => assessmentApi.publishGrades(itemId),
      onSuccess: ({ published }) => {
        toast({ tone: 'success', title: t('published', { count: published }) });
        invalidate();
      },
    }),
    exportFile: useMutation({
      mutationFn: async (format: ExportFormat) => {
        const { blob, fileName } = await gradebookApi.export(courseId, format);
        saveBlob(blob, fileName ?? `gradebook.${format}`);
      },
    }),
  };
}

/** FR-REPORT-01 student × item completion (shared by the progress matrix, analytics cards and inspector stats). */
export function useProgressReport(courseId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.progressReport(courseId),
    queryFn: () => gradebookApi.progressReport(courseId),
    enabled: enabled && !!courseId,
  });
}
