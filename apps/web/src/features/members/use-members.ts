'use client';
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  courseInvitationsApi,
  platformUsersApi,
  schoolMembersApi,
  type CourseInvitationInput,
  type MembersQuery,
  type PlatformUsersQuery,
} from '@/lib/api/endpoints/members';
import { getNextCursor } from '@/lib/api/pagination';
import { queryKeys } from '../query-keys';

/** Active school users a course teacher can enrol (no access to the admin user list needed). */
export function useEnrollmentCandidates(courseId: string, q: string, enabled = true) {
  return useInfiniteQuery({
    queryKey: queryKeys.enrollmentCandidates(courseId, q),
    queryFn: ({ pageParam }) =>
      courseInvitationsApi.candidates(courseId, { q: q || undefined, cursor: pageParam }),
    initialPageParam: null as string | null,
    getNextPageParam: getNextCursor,
    enabled,
  });
}

export function useCourseInvitation(courseId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: CourseInvitationInput) => courseInvitationsApi.invite(courseId, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.enrollmentsRoot(courseId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.schoolMembersRoot });
    },
  });
}

export function useSchoolMembers(params: Omit<MembersQuery, 'cursor'>) {
  return useInfiniteQuery({
    queryKey: queryKeys.schoolMembers(params),
    queryFn: ({ pageParam }) => schoolMembersApi.list({ ...params, cursor: pageParam }),
    initialPageParam: null as string | null,
    getNextPageParam: getNextCursor,
  });
}

export function useSchoolMemberMutations() {
  const queryClient = useQueryClient();
  const invalidate = () =>
    void queryClient.invalidateQueries({ queryKey: queryKeys.schoolMembersRoot });
  return {
    setStatus: useMutation({
      mutationFn: ({ id, status }: { id: string; status: 'active' | 'suspended' }) =>
        schoolMembersApi.setStatus(id, status),
      onSuccess: invalidate,
    }),
    activationLink: useMutation({
      mutationFn: (id: string) => schoolMembersApi.activationLink(id),
    }),
  };
}

export function usePlatformUsers(params: Omit<PlatformUsersQuery, 'cursor'>) {
  return useInfiniteQuery({
    queryKey: queryKeys.platformUsers(params),
    queryFn: ({ pageParam }) => platformUsersApi.list({ ...params, cursor: pageParam }),
    initialPageParam: null as string | null,
    getNextPageParam: getNextCursor,
  });
}

export function usePlatformUserMutations() {
  const queryClient = useQueryClient();
  const invalidate = () =>
    void queryClient.invalidateQueries({ queryKey: queryKeys.platformUsersRoot });
  return {
    block: useMutation({
      mutationFn: ({ id, reason }: { id: string; reason?: string }) =>
        platformUsersApi.block(id, reason),
      onSuccess: invalidate,
    }),
    unblock: useMutation({
      mutationFn: (id: string) => platformUsersApi.unblock(id),
      onSuccess: invalidate,
    }),
    erase: useMutation({
      mutationFn: (id: string) => platformUsersApi.erase(id),
      onSuccess: invalidate,
    }),
  };
}
