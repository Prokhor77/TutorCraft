'use client';
import { Rocket } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { HomeCourses } from '@/components/dashboard/home-courses';
import { StudentHome } from '@/components/dashboard/student-home';
import { TeacherHome } from '@/components/dashboard/teacher-home';
import { CreateCourseDialog } from '@/components/course/create-course-dialog';
import { Badge } from '@/components/ui/badge';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { PageHeader } from '@/components/ui/page-header';
import { SkeletonList } from '@/components/ui/skeleton';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useMe } from '@/features/auth/use-auth';
import { roleHints, useMyCourses } from '@/features/courses/use-my-courses';
import { canCreateCoursesHint } from '@/lib/access/permissions';

/** Role-aware home: «Мои задачи» for learners, teaching dashboard for tutors, both as tabs when needed. */
export default function HomePage() {
  const t = useTranslations('home');
  const tCommon = useTranslations('common');
  const tRoles = useTranslations('roles');
  const locale = useLocale();
  const me = useMe();
  const courses = useMyCourses();
  const { teaches, learns } = roleHints(courses.data);
  const canCreate = canCreateCoursesHint(me?.tenantRoles);
  const greeting = t('greeting', { name: me?.firstName ?? '' });
  const today = new Intl.DateTimeFormat(locale, {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  }).format(new Date());
  const roleChips = (
    <>
      {teaches ? (
        <Badge tone="primary" dot>
          {tRoles('teacher')}
        </Badge>
      ) : null}
      {learns ? (
        <Badge tone="success" dot>
          {tRoles('student')}
        </Badge>
      ) : null}
    </>
  );

  if (courses.isLoading || courses.isError) {
    return (
      <>
        <PageHeader eyebrow={today} title={greeting} />
        {courses.isError ? (
          <ErrorState
            title={t('loadError')}
            retryLabel={tCommon('retry')}
            onRetry={() => void courses.refetch()}
          />
        ) : (
          <SkeletonList label={tCommon('loading')} />
        )}
      </>
    );
  }
  if (!teaches && !learns) {
    return (
      <>
        <PageHeader eyebrow={today} title={greeting} />
        <EmptyState
          icon={Rocket}
          title={canCreate ? t('onboardingTitle') : t('noCoursesTitle')}
          description={canCreate ? t('onboardingText') : t('noCoursesText')}
          action={canCreate ? <CreateCourseDialog /> : null}
        />
      </>
    );
  }
  if (teaches && learns) {
    return (
      <Tabs defaultValue="learning">
        <PageHeader
          eyebrow={today}
          title={greeting}
          meta={roleChips}
          actions={canCreate ? <CreateCourseDialog /> : null}
        >
          <TabsList>
            <TabsTrigger value="learning">{t('tabLearning')}</TabsTrigger>
            <TabsTrigger value="teaching">{t('tabTeaching')}</TabsTrigger>
          </TabsList>
        </PageHeader>
        <TabsContent value="learning" className="mt-0">
          <StudentHome />
        </TabsContent>
        <TabsContent value="teaching" className="mt-0">
          <TeacherHome />
        </TabsContent>
        <HomeCourses courses={courses.data ?? []} className="mt-4 md:mt-gutter" />
      </Tabs>
    );
  }
  return (
    <>
      <PageHeader
        eyebrow={today}
        title={greeting}
        meta={roleChips}
        description={teaches ? t('teacherSubtitle') : t('studentSubtitle')}
        actions={teaches && canCreate ? <CreateCourseDialog /> : null}
      />
      {teaches ? <TeacherHome /> : <StudentHome />}
      <HomeCourses courses={courses.data ?? []} className="mt-4 md:mt-gutter" />
    </>
  );
}
