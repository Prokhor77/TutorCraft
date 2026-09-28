'use client';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  enrollmentApi,
  type AutoGroupInput,
  type EnrollmentPatch,
  type EnrollmentsQuery,
} from '@/lib/api/endpoints/enrollment';
import { getNextCursor } from '@/lib/api/pagination';
import type { CourseRole } from '@/lib/api/schemas/common';
import { queryKeys } from '../query-keys';

export function useEnrollments(courseId: string, params: Omit<EnrollmentsQuery, 'cursor'>) {
  return useInfiniteQuery({
    queryKey: queryKeys.enrollments(courseId, params),
    queryFn: ({ pageParam }) => enrollmentApi.list(courseId, { ...params, cursor: pageParam }),
    initialPageParam: null as string | null,
    getNextPageParam: getNextCursor,
  });
}

export function useGroups(courseId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.groups(courseId),
    queryFn: () => enrollmentApi.groups(courseId),
    enabled,
  });
}

export function useInviteLinks(courseId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.inviteLinks(courseId),
    queryFn: () => enrollmentApi.inviteLinks(courseId),
    enabled,
  });
}

export function useEnrollmentMutations(courseId: string) {
  const queryClient = useQueryClient();
  const invalidateEnrollments = () =>
    void queryClient.invalidateQueries({ queryKey: queryKeys.enrollmentsRoot(courseId) });
  const invalidateGroups = () => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.groups(courseId) });
    invalidateEnrollments();
  };
  return {
    enrol: useMutation({
      mutationFn: (input: { userIds: string[]; role: CourseRole }) =>
        enrollmentApi.enrol(courseId, input),
      onSuccess: invalidateEnrollments,
    }),
    update: useMutation({
      mutationFn: ({ id, patch }: { id: string; patch: EnrollmentPatch }) =>
        enrollmentApi.update(id, patch),
      onSuccess: invalidateEnrollments,
    }),
    remove: useMutation({
      mutationFn: (id: string) => enrollmentApi.remove(id),
      onSuccess: invalidateEnrollments,
    }),
    createInviteLink: useMutation({
      mutationFn: (input: { role: CourseRole; expiresAt?: string; maxUses?: number }) =>
        enrollmentApi.createInviteLink(courseId, input),
      onSuccess: () =>
        void queryClient.invalidateQueries({ queryKey: queryKeys.inviteLinks(courseId) }),
    }),
    revokeInviteLink: useMutation({
      mutationFn: (id: string) => enrollmentApi.revokeInviteLink(id),
      onSuccess: () =>
        void queryClient.invalidateQueries({ queryKey: queryKeys.inviteLinks(courseId) }),
    }),
    createGroup: useMutation({
      mutationFn: (name: string) => enrollmentApi.createGroup(courseId, name),
      onSuccess: invalidateGroups,
    }),
    renameGroup: useMutation({
      mutationFn: ({ id, name }: { id: string; name: string }) =>
        enrollmentApi.updateGroup(id, { name }),
      onSuccess: invalidateGroups,
    }),
    deleteGroup: useMutation({
      mutationFn: (id: string) => enrollmentApi.deleteGroup(id),
      onSuccess: invalidateGroups,
    }),
    setMembers: useMutation({
      mutationFn: ({ id, userIds }: { id: string; userIds: string[] }) =>
        enrollmentApi.setGroupMembers(id, userIds),
      onSuccess: invalidateGroups,
    }),
    autoGroups: useMutation({
      mutationFn: (input: AutoGroupInput) => enrollmentApi.autoGroups(courseId, input),
      onSuccess: invalidateGroups,
    }),
  };
}
