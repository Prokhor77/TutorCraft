'use client';
import { BookOpen, Building2, GraduationCap, Search } from 'lucide-react';
import Link from 'next/link';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import { Suspense, useDeferredValue, useState } from 'react';
import { AdminTablePanel, PersonCell } from '@/components/admin/admin-panel';
import { StatusChip } from '@/components/course/status-chip';
import { Badge } from '@/components/ui/badge';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { Input, NativeSelect } from '@/components/ui/input';
import { LoadMore } from '@/components/ui/load-more';
import { SkeletonList } from '@/components/ui/skeleton';
import { StatCard } from '@/components/ui/stat-card';
import { Table, TBody, TD, TH, THead, TR } from '@/components/ui/table';
import { usePlatformCourses, usePlatformTenants } from '@/features/admin/use-admin';
import { ROUTES } from '@/features/auth/routes';
import type { PlatformCourse } from '@/lib/api/endpoints/platform';
import { flattenPages } from '@/lib/api/pagination';
import { formatDate, fullName } from '@/lib/utils/format';

const SCHOOL_PARAM = 'school';

export default function AdminCoursesPage() {
  return (
    <Suspense>
      <CoursesList />
    </Suspense>
  );
}

/**
 * Courses of every school: which tutor created each one, in which school, how many students and teachers it has.
 * A row opens the read-only view of the course (description, materials, participants). `?school=` narrows the list
 * to one school (the «Курсы» action on the schools tab links here).
 */
function CoursesList() {
  const t = useTranslations('admin.courses');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();
  const school = params.get(SCHOOL_PARAM) ?? '';
  const [query, setQuery] = useState('');
  const deferredQuery = useDeferredValue(query.trim());
  const tenants = usePlatformTenants();
  const courses = usePlatformCourses({
    tenantId: school || undefined,
    q: deferredQuery || undefined,
  });
  const rows = flattenPages(courses.data?.pages);
  const tenantById = new Map((tenants.data ?? []).map((tenant) => [tenant.id, tenant]));
  const selectedSchool = school ? tenantById.get(school) : undefined;
  const number = (value: number) => new Intl.NumberFormat(locale).format(value);
  const totalCourses = selectedSchool
    ? selectedSchool.coursesCount
    : (tenants.data ?? []).reduce((sum, tenant) => sum + tenant.coursesCount, 0);
  const schoolsWithCourses = (tenants.data ?? []).filter((tenant) => tenant.coursesCount > 0);

  const setSchool = (value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set(SCHOOL_PARAM, value);
    else next.delete(SCHOOL_PARAM);
    const search = next.toString();
    router.replace(search ? `${pathname}?${search}` : pathname, { scroll: false });
  };

  return (
    <div className="flex flex-col gap-4 md:gap-gutter">
      <div className="grid gap-4 sm:grid-cols-2">
        <StatCard
          label={
            selectedSchool ? t('statCoursesOf', { name: selectedSchool.name }) : t('statCourses')
          }
          icon={BookOpen}
          value={tenants.data ? number(totalCourses) : '—'}
        />
        <StatCard
          label={t('statSchools')}
          icon={Building2}
          tone="success"
          value={tenants.data ? number(schoolsWithCourses.length) : '—'}
        />
      </div>
      <AdminTablePanel
        title={t('title')}
        count={
          rows.length > 0 ? (
            <Badge tone="primary">{t('shownCount', { count: rows.length })}</Badge>
          ) : null
        }
        description={t('hint')}
        toolbar={
          <div className="flex flex-col gap-2 md:flex-row">
            <div className="relative md:max-w-md md:flex-1">
              <Search
                className="pointer-events-none absolute left-4 top-1/2 size-4 -translate-y-1/2 text-text-muted"
                aria-hidden
              />
              <Input
                type="search"
                aria-label={t('search')}
                placeholder={t('search')}
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                className="rounded-full border-transparent bg-surface-muted pl-10"
              />
            </div>
            <NativeSelect
              aria-label={t('school')}
              value={school}
              onChange={(event) => setSchool(event.target.value)}
              className="h-10 rounded-full border-transparent bg-surface-muted md:w-64"
            >
              <option value="">{t('allSchools')}</option>
              {(tenants.data ?? []).map((tenant) => (
                <option key={tenant.id} value={tenant.id}>
                  {tenant.name}
                </option>
              ))}
            </NativeSelect>
          </div>
        }
        footer={
          <LoadMore
            hasMore={!!courses.hasNextPage}
            loading={courses.isFetchingNextPage}
            onClick={() => void courses.fetchNextPage()}
            label={tCommon('loadMore')}
          />
        }
      >
        {courses.isLoading ? (
          <div className="p-5 sm:p-6">
            <SkeletonList label={tCommon('loading')} />
          </div>
        ) : null}
        {courses.isError ? (
          <div className="p-5 sm:p-6">
            <ErrorState
              title={t('loadError')}
              retryLabel={tCommon('retry')}
              onRetry={() => void courses.refetch()}
            />
          </div>
        ) : null}
        {courses.isSuccess && rows.length === 0 ? (
          <div className="p-5 sm:p-6">
            <EmptyState
              icon={BookOpen}
              title={deferredQuery ? t('nothingFound') : t('emptyTitle')}
              description={deferredQuery ? t('nothingFoundText') : t('emptyText')}
            />
          </div>
        ) : null}
        {rows.length > 0 ? (
          <Table>
            <THead>
              <tr>
                <TH>{t('course')}</TH>
                <TH className="hidden md:table-cell">{t('school')}</TH>
                <TH className="hidden lg:table-cell">{t('author')}</TH>
                <TH className="hidden sm:table-cell">{t('students')}</TH>
                <TH className="hidden sm:table-cell">{t('status')}</TH>
                <TH className="hidden xl:table-cell">{t('created')}</TH>
              </tr>
            </THead>
            <TBody>
              {rows.map((course) => (
                <CourseRow
                  key={course.id}
                  course={course}
                  schoolName={tenantById.get(course.tenantId)?.name}
                  number={number}
                />
              ))}
            </TBody>
          </Table>
        ) : null}
      </AdminTablePanel>
    </div>
  );
}

function CourseRow({
  course,
  schoolName,
  number,
}: {
  course: PlatformCourse;
  schoolName: string | undefined;
  number: (value: number) => string;
}) {
  const t = useTranslations('admin.courses');
  const locale = useLocale();
  const href = ROUTES.adminCourse(course.tenantId, course.id);
  const author = course.author;
  return (
    <TR>
      <TD className="min-w-56">
        <Link
          href={href}
          className="group flex min-w-0 items-center gap-3 rounded focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20"
        >
          {course.coverUrl ? (
            // eslint-disable-next-line @next/next/no-img-element -- signed storage URL, not optimisable
            <img
              src={course.coverUrl}
              alt=""
              className="size-10 shrink-0 rounded object-cover"
              loading="lazy"
            />
          ) : (
            <span className="flex size-10 shrink-0 items-center justify-center rounded bg-primary-soft text-primary">
              <GraduationCap className="size-5" aria-hidden />
            </span>
          )}
          <span className="flex min-w-0 flex-col">
            <span className="truncate font-semibold group-hover:text-primary">{course.title}</span>
            <span className="truncate text-xs text-text-muted">
              {[course.shortName, schoolName].filter(Boolean).join(' · ') || course.slug}
            </span>
            <span className="text-xs text-text-muted sm:hidden">
              {t('studentsCount', { count: course.studentsCount })}
            </span>
          </span>
        </Link>
      </TD>
      <TD className="hidden md:table-cell">
        <span className="truncate text-sm">{schoolName ?? '—'}</span>
      </TD>
      <TD className="hidden lg:table-cell">
        {author ? (
          <PersonCell name={fullName(author)} secondary={author.email} />
        ) : (
          <span className="text-text-muted">{t('noAuthor')}</span>
        )}
      </TD>
      <TD className="hidden tabular-nums sm:table-cell">
        <span className="flex flex-col">
          <span>{number(course.studentsCount)}</span>
          {course.staffCount > 1 ? (
            <span className="text-xs text-text-muted">
              {t('staffCount', { count: course.staffCount })}
            </span>
          ) : null}
        </span>
      </TD>
      <TD className="hidden sm:table-cell">
        <StatusChip visibility={course.visibility} />
      </TD>
      <TD className="hidden whitespace-nowrap text-xs text-text-muted xl:table-cell">
        {formatDate(course.createdAt, locale)}
      </TD>
    </TR>
  );
}
