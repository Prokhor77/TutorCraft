'use client';
import { useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, Building2, ExternalLink, GraduationCap, Layers, Library } from 'lucide-react';
import Link from 'next/link';
import { useParams, useRouter, useSearchParams } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import { Suspense, useEffect, type ReactNode } from 'react';
import { CourseMaterials, countMaterials } from '@/components/admin/course-materials';
import { CourseParticipants } from '@/components/admin/course-participants';
import { BlockRenderer } from '@/components/blockdoc/block-renderer';
import { StatusChip } from '@/components/course/status-chip';
import { Button } from '@/components/ui/button';
import { ErrorState } from '@/components/ui/error-state';
import { Panel } from '@/components/ui/page-header';
import { SkeletonList } from '@/components/ui/skeleton';
import { StatCard } from '@/components/ui/stat-card';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { switchAdminTenant } from '@/features/admin/tenant-switch';
import { usePlatformTenants } from '@/features/admin/use-admin';
import { ROUTES } from '@/features/auth/routes';
import { useCourse } from '@/features/courses/use-courses';
import { useOutline } from '@/features/courses/use-outline';
import type { PlatformTenant } from '@/lib/api/endpoints/platform';
import { HTTP_STATUS, isApiProblem } from '@/lib/api/problem';
import type { Course } from '@/lib/api/schemas/courses';
import { formatDateTime, fullName } from '@/lib/utils/format';
import { useAdminTenantStore } from '@/stores/admin-tenant-store';

const TABS = ['overview', 'materials', 'participants'] as const;
type Tab = (typeof TABS)[number];

export default function AdminCoursePage() {
  return (
    <Suspense>
      <SchoolGate />
    </Suspense>
  );
}

/**
 * The course lives in `?school=`: the console switches to that school first (course endpoints read `X-Tenant-Id`),
 * so the regular course API answers with the platform administrator's rights in it.
 */
function SchoolGate() {
  const { courseId } = useParams<{ courseId: string }>();
  const school = useSearchParams().get('school');
  const tCommon = useTranslations('common');
  const queryClient = useQueryClient();
  const tenantId = useAdminTenantStore((state) => state.tenantId);

  useEffect(() => {
    if (school) switchAdminTenant(queryClient, school);
  }, [school, queryClient]);

  if (!tenantId || (school && school !== tenantId))
    return <SkeletonList label={tCommon('loading')} />;
  return <CourseView key={`${tenantId}:${courseId}`} courseId={courseId} tenantId={tenantId} />;
}

/** Read-only view of a tutor's course for the platform administrator: details, materials and participants. */
function CourseView({ courseId, tenantId }: { courseId: string; tenantId: string }) {
  const t = useTranslations('admin.course');
  const tCourses = useTranslations('admin.courses');
  const tCommon = useTranslations('common');
  const router = useRouter();
  const params = useSearchParams();
  const course = useCourse(courseId);
  const outline = useOutline(courseId);
  const tenants = usePlatformTenants();
  const school = tenants.data?.find((tenant) => tenant.id === tenantId);
  const requested = params.get('tab') as Tab | null;
  const tab: Tab = requested && TABS.includes(requested) ? requested : 'overview';

  const back = (
    <Button asChild variant="secondary" size="sm" className="self-start">
      <Link href={`${ROUTES.adminCourses}?school=${encodeURIComponent(tenantId)}`}>
        <ArrowLeft aria-hidden /> {t('back')}
      </Link>
    </Button>
  );

  if (course.isLoading) return <SkeletonList label={tCommon('loading')} />;
  if (course.isError || !course.data) {
    const notFound = isApiProblem(course.error) && course.error.status === HTTP_STATUS.notFound;
    return (
      <div className="flex flex-col gap-4">
        {back}
        <ErrorState
          title={notFound ? t('notFound') : t('loadError')}
          retryLabel={tCommon('retry')}
          onRetry={notFound ? undefined : () => void course.refetch()}
        />
      </div>
    );
  }

  const data = course.data;
  const counts = outline.data ? countMaterials(outline.data.modules) : null;
  const changeTab = (value: string) => {
    const next = new URLSearchParams(params);
    next.set('tab', value);
    router.replace(`?${next.toString()}`, { scroll: false });
  };

  return (
    <div className="flex flex-col gap-4 md:gap-gutter">
      {back}
      <CourseHero course={data} school={school} />
      <div className="grid gap-4 sm:grid-cols-3">
        <StatCard
          label={t('statModules')}
          icon={Layers}
          value={counts ? String(counts.modules) : '—'}
        />
        <StatCard
          label={t('statItems')}
          icon={Library}
          tone="success"
          value={counts ? String(counts.items) : '—'}
        />
        <StatCard
          label={t('statSchool')}
          icon={Building2}
          tone="warning"
          value={school?.name ?? '—'}
          footer={
            school?.owner ? (
              <p className="truncate text-xs text-text-muted">
                {tCourses('ownerLine', { name: fullName(school.owner), email: school.owner.email })}
              </p>
            ) : null
          }
        />
      </div>
      <Tabs value={tab} onValueChange={changeTab} className="flex flex-col gap-4">
        <TabsList aria-label={t('tabs')} className="self-start">
          {TABS.map((key) => (
            <TabsTrigger key={key} value={key}>
              {t(`tab.${key}`)}
            </TabsTrigger>
          ))}
        </TabsList>
        <TabsContent value="overview" className="mt-0">
          <CourseOverview course={data} school={school} />
        </TabsContent>
        <TabsContent value="materials" className="mt-0">
          <CourseMaterials outline={outline} />
        </TabsContent>
        <TabsContent value="participants" className="mt-0">
          <CourseParticipants courseId={data.id} />
        </TabsContent>
      </Tabs>
    </div>
  );
}

function CourseHero({ course, school }: { course: Course; school: PlatformTenant | undefined }) {
  const t = useTranslations('admin.course');
  return (
    <Panel>
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
        {course.coverUrl ? (
          // eslint-disable-next-line @next/next/no-img-element -- signed storage URL, not optimisable
          <img
            src={course.coverUrl}
            alt=""
            className="aspect-video w-full rounded object-cover sm:w-48"
          />
        ) : (
          <span className="flex aspect-video w-full items-center justify-center rounded bg-primary-soft text-primary sm:w-48">
            <GraduationCap className="size-10" aria-hidden />
          </span>
        )}
        <div className="flex min-w-0 flex-1 flex-col gap-2">
          <div className="flex flex-wrap items-center gap-2">
            <StatusChip visibility={course.visibility} />
            {course.shortName ? (
              <span className="font-mono text-xs text-text-muted">{course.shortName}</span>
            ) : null}
          </div>
          <h2 className="break-words text-2xl">{course.title}</h2>
          {school ? (
            <p className="text-sm text-text-muted">
              {t('inSchool', { name: school.name, slug: school.slug })}
            </p>
          ) : null}
          {school && course.visibility === 'published' ? (
            <Button asChild variant="ghost" size="sm" className="self-start">
              <a
                href={ROUTES.courseLanding(school.slug, course.slug)}
                target="_blank"
                rel="noopener noreferrer"
              >
                <ExternalLink aria-hidden /> {t('openLanding')}
              </a>
            </Button>
          ) : null}
        </div>
      </div>
    </Panel>
  );
}

function CourseOverview({
  course,
  school,
}: {
  course: Course;
  school: PlatformTenant | undefined;
}) {
  const t = useTranslations('admin.course');
  const locale = useLocale();
  const date = (value: string | null) => (value ? formatDateTime(value, locale) : t('notSet'));
  const selfEnrol = course.selfEnrol;
  const facts: { key: string; label: string; value: ReactNode }[] = [
    {
      key: 'school',
      label: t('facts.school'),
      value: school ? `${school.name} (${school.slug})` : '—',
    },
    {
      key: 'owner',
      label: t('facts.owner'),
      value: school?.owner ? `${fullName(school.owner)} · ${school.owner.email}` : t('notSet'),
    },
    {
      key: 'slug',
      label: t('facts.slug'),
      value: <span className="font-mono">{course.slug}</span>,
    },
    {
      key: 'visibility',
      label: t('facts.visibility'),
      value: <StatusChip visibility={course.visibility} />,
    },
    ...(course.visibility === 'scheduled'
      ? [{ key: 'publishAt', label: t('facts.publishAt'), value: date(course.publishAt) }]
      : []),
    { key: 'startsAt', label: t('facts.startsAt'), value: date(course.startsAt) },
    { key: 'endsAt', label: t('facts.endsAt'), value: date(course.endsAt) },
    {
      key: 'selfEnrol',
      label: t('facts.selfEnrol'),
      value: selfEnrol.enabled
        ? [
            t('selfEnrolOn'),
            selfEnrol.code ? t('selfEnrolCode', { code: selfEnrol.code }) : null,
            selfEnrol.maxStudents ? t('selfEnrolMax', { count: selfEnrol.maxStudents }) : null,
            selfEnrol.until ? t('selfEnrolUntil', { date: date(selfEnrol.until) }) : null,
          ]
            .filter(Boolean)
            .join(' · ')
        : t('selfEnrolOff'),
    },
    { key: 'groupMode', label: t('facts.groupMode'), value: t(`groupModes.${course.groupMode}`) },
  ];
  const hasDescription = !!course.description && course.description.blocks.length > 0;

  return (
    <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_22rem]">
      <Panel title={t('descriptionTitle')}>
        {hasDescription ? (
          <BlockRenderer doc={course.description} />
        ) : (
          <p className="text-sm text-text-muted">{t('noDescription')}</p>
        )}
      </Panel>
      <Panel title={t('factsTitle')} as="aside">
        <dl className="flex flex-col gap-3 text-sm">
          {facts.map((fact) => (
            <div key={fact.key} className="flex flex-col gap-0.5">
              <dt className="text-xs text-text-muted">{fact.label}</dt>
              <dd className="break-words">{fact.value}</dd>
            </div>
          ))}
        </dl>
      </Panel>
    </div>
  );
}
