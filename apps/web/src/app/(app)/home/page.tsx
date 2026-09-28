'use client';
import { Rocket } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { StudentHome } from '@/components/dashboard/student-home';
import { TeacherHome } from '@/components/dashboard/teacher-home';
import { CreateCourseDialog } from '@/components/course/create-course-dialog';
import { EmptyState } from '@/components/ui/empty-state';
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
  const me = useMe();
  const courses = useMyCourses();
  const { teaches, learns } = roleHints(courses.data);
  const canCreate = canCreateCoursesHint(me?.tenantRoles);
  const greeting = t('greeting', { name: me?.firstName ?? '' });

  if (courses.isLoading) return <SkeletonList label={tCommon('loading')} />;
  if (!teaches && !learns) {
    return (
      <>
        <PageHeader title={greeting} />
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
      <>
        <PageHeader title={greeting} actions={canCreate ? <CreateCourseDialog /> : null} />
        <Tabs defaultValue="learning">
          <TabsList>
            <TabsTrigger value="learning">{t('tabLearning')}</TabsTrigger>
            <TabsTrigger value="teaching">{t('tabTeaching')}</TabsTrigger>
          </TabsList>
          <TabsContent value="learning">
            <StudentHome />
          </TabsContent>
          <TabsContent value="teaching">
            <TeacherHome />
          </TabsContent>
        </Tabs>
      </>
    );
  }
  return (
    <>
      <PageHeader
        title={greeting}
        description={teaches ? t('teacherSubtitle') : t('studentSubtitle')}
        actions={teaches && canCreate ? <CreateCourseDialog /> : null}
      />
      {teaches ? <TeacherHome /> : <StudentHome />}
    </>
  );
}
