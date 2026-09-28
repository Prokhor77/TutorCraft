'use client';
import { CheckCircle2, Circle } from 'lucide-react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { AssignmentSubmissions } from '@/components/assignment/teacher-assignment';
import { StudentAssignment } from '@/components/assignment/student-assignment';
import { DueLabel, ItemTypeIcon, LockedReason } from '@/components/course/item-meta';
import { ForumView } from '@/components/forum/forum-view';
import { AttemptsReport } from '@/components/quiz/attempts-report';
import { SlotsEditor } from '@/components/quiz/slots-editor';
import { StudentQuiz } from '@/components/quiz/student-quiz';
import { Button } from '@/components/ui/button';
import { ErrorState } from '@/components/ui/error-state';
import { SkeletonList } from '@/components/ui/skeleton';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { ROUTES } from '@/features/auth/routes';
import { useCourseContext } from '@/features/courses/course-context';
import { findItem, useOutline } from '@/features/courses/use-outline';
import { useItem, useItemPatcher, useManualCompletion } from '@/features/items/use-item';
import { PERMISSIONS } from '@/lib/access/permissions';
import { HTTP_STATUS, isApiProblem } from '@/lib/api/problem';
import type { ItemDetail } from '@/lib/api/schemas/courses';
import { ContentEditor } from './content-editor';
import { ItemSettingsForm } from './item-settings-form';
import { ResourceView } from './resource-views';

type TabKey = 'content' | 'submissions' | 'questions' | 'attempts' | 'discussions' | 'settings';

function teacherTabs(item: ItemDetail): TabKey[] {
  switch (item.type) {
    case 'assignment':
      return ['content', 'submissions', 'settings'];
    case 'quiz':
      return ['content', 'questions', 'attempts', 'settings'];
    case 'forum':
      return ['discussions', 'settings'];
    case 'page':
      return ['content', 'settings'];
    default:
      return ['content', 'settings'];
  }
}

function StudentBody({ item }: { item: ItemDetail }) {
  switch (item.type) {
    case 'assignment':
      return <StudentAssignment item={item} />;
    case 'quiz':
      return <StudentQuiz item={item} />;
    case 'forum':
      return <ForumView item={item} />;
    default:
      return <ResourceView item={item} />;
  }
}

function ManualCompletion({ item, complete }: { item: ItemDetail; complete: boolean }) {
  const t = useTranslations('itemView');
  const mutation = useManualCompletion(item.id, item.courseId);
  if (item.completionRule.mode !== 'manual') return null;
  return (
    <Button
      variant={complete ? 'soft' : 'secondary'}
      loading={mutation.isPending}
      onClick={() => mutation.mutate(!complete)}
      aria-pressed={complete}
    >
      {complete ? <CheckCircle2 aria-hidden /> : <Circle aria-hidden />}{' '}
      {complete ? t('markedComplete') : t('markComplete')}
    </Button>
  );
}

function TeacherBody({ item }: { item: ItemDetail }) {
  const t = useTranslations('itemView');
  const router = useRouter();
  const params = useSearchParams();
  const { course } = useCourseContext();
  const { patch } = useItemPatcher(item);
  const tabs = teacherTabs(item);
  const requested = params.get('tab') as TabKey | null;
  const initial = requested && tabs.includes(requested) ? requested : tabs[0];
  return (
    <Tabs
      defaultValue={initial}
      onValueChange={(value) =>
        router.replace(`${ROUTES.item(course.id, item.id)}?tab=${value}`, { scroll: false })
      }
    >
      <TabsList>
        {tabs.map((tab) => (
          <TabsTrigger key={tab} value={tab}>
            {t(`tabs.${tab}`)}
          </TabsTrigger>
        ))}
      </TabsList>
      <TabsContent value="content">
        <div className="flex flex-col gap-6">
          <ContentEditor
            draftKey={`item:${item.id}:content`}
            initial={item.content}
            label={t('contentLabel')}
            save={(content) => patch({ content })}
          />
          {item.type !== 'page' && item.type !== 'assignment' && item.type !== 'quiz' ? (
            <ResourceView item={{ ...item, content: null }} />
          ) : null}
        </div>
      </TabsContent>
      <TabsContent value="submissions">
        <AssignmentSubmissions item={item} />
      </TabsContent>
      <TabsContent value="questions">
        <SlotsEditor courseId={course.id} itemId={item.id} />
      </TabsContent>
      <TabsContent value="attempts">
        <AttemptsReport itemId={item.id} />
      </TabsContent>
      <TabsContent value="discussions">
        <ForumView item={item} />
      </TabsContent>
      <TabsContent value="settings">
        <ItemSettingsForm item={item} />
      </TabsContent>
    </Tabs>
  );
}

/** Item page by type (SPEC §10): teacher edit tabs or learner view; locked items explain why (UX-07). */
export function ItemPage({ itemId }: { itemId: string }) {
  const t = useTranslations('itemView');
  const tCommon = useTranslations('common');
  const { course, editMode, can } = useCourseContext();
  const item = useItem(itemId);
  const outline = useOutline(course.id);
  const outlineEntry = findItem(outline.data, itemId)?.item;

  if (item.isLoading) return <SkeletonList label={tCommon('loading')} />;
  if (item.isError || !item.data) {
    const notFound = isApiProblem(item.error) && item.error.status === HTTP_STATUS.notFound;
    if (outlineEntry && !outlineEntry.availability.available) {
      return (
        <div className="flex flex-col gap-3 rounded-lg border border-border bg-surface p-6">
          <h1 className="text-xl">{outlineEntry.title}</h1>
          <LockedReason reasons={outlineEntry.availability.reasons} />
        </div>
      );
    }
    return (
      <ErrorState
        title={notFound ? t('notFound') : t('loadError')}
        retryLabel={tCommon('retry')}
        onRetry={notFound ? undefined : () => void item.refetch()}
      />
    );
  }
  const data = item.data;
  const teacherView = editMode && can(PERMISSIONS.courseEdit);

  return (
    <div className="flex flex-col gap-6">
      <nav aria-label={t('breadcrumbs')} className="text-sm text-text-muted">
        <Link href={ROUTES.course(course.id)} className="hover:underline">
          {course.title}
        </Link>{' '}
        / <span aria-current="page">{data.title}</span>
      </nav>
      <header className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="flex items-start gap-3">
          <ItemTypeIcon type={data.type} className="size-10" />
          <div className="flex flex-col gap-1">
            <h1 className="text-2xl">{data.title}</h1>
            <DueLabel dueAt={data.dueAt} />
          </div>
        </div>
        {!teacherView ? (
          <ManualCompletion item={data} complete={outlineEntry?.completion === 'complete'} />
        ) : null}
      </header>
      {teacherView ? <TeacherBody item={data} /> : <StudentBody item={data} />}
    </div>
  );
}
