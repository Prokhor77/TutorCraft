'use client';
import { BookOpen, Search } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useDeferredValue, useState } from 'react';
import { CourseCard } from '@/components/course/course-card';
import { CreateCourseDialog } from '@/components/course/create-course-dialog';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { Input } from '@/components/ui/input';
import { LoadMore } from '@/components/ui/load-more';
import { PageHeader } from '@/components/ui/page-header';
import { Segmented } from '@/components/ui/segmented';
import { SkeletonList } from '@/components/ui/skeleton';
import { useMe } from '@/features/auth/use-auth';
import { useCourseList, useDeleteCourse, useDuplicateCourse } from '@/features/courses/use-courses';
import { canCreateCoursesHint } from '@/lib/access/permissions';
import { flattenPages } from '@/lib/api/pagination';

type Scope = 'mine' | 'all';
const TEACHING_ROLES = new Set(['teacher']);

export default function CoursesPage() {
  const t = useTranslations('courses');
  const tCommon = useTranslations('common');
  const me = useMe();
  const [query, setQuery] = useState('');
  const [scope, setScope] = useState<Scope>('mine');
  const deferredQuery = useDeferredValue(query.trim());
  const list = useCourseList({ q: deferredQuery || undefined, mine: scope === 'mine' });
  const duplicate = useDuplicateCourse();
  const remove = useDeleteCourse();
  const courses = flattenPages(list.data?.pages);
  const canCreate = canCreateCoursesHint(me?.tenantRoles);

  return (
    <>
      <PageHeader
        title={t('pageTitle')}
        description={t('pageDescription')}
        actions={canCreate ? <CreateCourseDialog /> : null}
      >
        <div className="flex w-full flex-col gap-3 sm:flex-row sm:items-center">
          <div className="relative flex-1 sm:max-w-md">
            <Search
              className="pointer-events-none absolute left-4 top-1/2 size-4 -translate-y-1/2 text-text-muted"
              aria-hidden
            />
            <Input
              type="search"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder={t('search')}
              aria-label={t('search')}
              className="rounded-full border-transparent bg-surface-muted pl-10 shadow-inner focus:bg-surface"
            />
          </div>
          <Segmented<Scope>
            className="sm:ml-auto sm:min-w-64"
            label={t('scope')}
            value={scope}
            onChange={setScope}
            options={[
              { value: 'mine', label: t('mine') },
              { value: 'all', label: t('all') },
            ]}
          />
        </div>
      </PageHeader>
      {list.isLoading ? <SkeletonList label={tCommon('loading')} /> : null}
      {list.isError ? (
        <ErrorState
          title={t('loadError')}
          retryLabel={tCommon('retry')}
          onRetry={() => void list.refetch()}
        />
      ) : null}
      {list.isSuccess && courses.length === 0 ? (
        <EmptyState
          icon={BookOpen}
          title={deferredQuery ? t('nothingFound') : t('emptyTitle')}
          description={
            deferredQuery
              ? t('nothingFoundHint')
              : canCreate
                ? t('emptyTeacher')
                : t('emptyStudent')
          }
          action={!deferredQuery && canCreate ? <CreateCourseDialog /> : null}
        />
      ) : null}
      <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2 md:gap-gutter xl:grid-cols-3">
        {courses.map((course) => {
          const manageable = course.role !== null && TEACHING_ROLES.has(course.role);
          return (
            <li key={course.id}>
              <CourseCard
                course={course}
                onDuplicate={manageable ? () => duplicate.mutate(course.id) : undefined}
                onDelete={manageable ? () => remove.mutate(course) : undefined}
              />
            </li>
          );
        })}
      </ul>
      <LoadMore
        hasMore={!!list.hasNextPage}
        loading={list.isFetchingNextPage}
        onClick={() => void list.fetchNextPage()}
        label={tCommon('loadMore')}
      />
    </>
  );
}
