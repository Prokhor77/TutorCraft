'use client';
import { ArrowLeft, CheckCircle2, Circle, LayoutPanelLeft, Lock } from 'lucide-react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useTranslations } from 'next-intl';
import type { ReactNode } from 'react';
import { AssignmentSubmissions } from '@/components/assignment/teacher-assignment';
import { StudentAssignment } from '@/components/assignment/student-assignment';
import {
  CompletionBadge,
  DueLabel,
  ITEM_TYPE_ICONS,
  LockedReason,
} from '@/components/course/item-meta';
import { StatusChip } from '@/components/course/status-chip';
import { ForumView } from '@/components/forum/forum-view';
import { AttemptsReport } from '@/components/quiz/attempts-report';
import { QuizBuilder } from '@/components/quiz/quiz-builder';
import { StudentQuiz } from '@/components/quiz/student-quiz';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { ErrorState } from '@/components/ui/error-state';
import { Breadcrumbs, PageHeader, Panel } from '@/components/ui/page-header';
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
import { hasItemFacts, ItemFacts } from './item-facts';
import { ItemPager } from './item-pager';
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
      return (
        <Panel className="md:p-8">
          <ResourceView item={item} />
        </Panel>
      );
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

function BackToCourse() {
  const t = useTranslations('itemView');
  const { course } = useCourseContext();
  return (
    <Button asChild variant="secondary" size="sm">
      <Link href={ROUTES.course(course.id)}>
        <ArrowLeft aria-hidden /> {t('backToCourse')}
      </Link>
    </Button>
  );
}

/** Heading of the content panel per item type (shared with the builder canvas). */
function contentHeading(type: ItemDetail['type']): string {
  switch (type) {
    case 'page':
      return 'contentPage';
    case 'assignment':
      return 'contentAssignment';
    case 'quiz':
      return 'contentQuiz';
    default:
      return 'contentDefault';
  }
}

function useTeacherTab(item: ItemDetail) {
  const router = useRouter();
  const params = useSearchParams();
  const { course } = useCourseContext();
  const tabs = teacherTabs(item);
  const requested = params.get('tab') as TabKey | null;
  const initial = requested && tabs.includes(requested) ? requested : tabs[0];
  return {
    tabs,
    initial,
    onChange: (value: string) =>
      router.replace(`${ROUTES.item(course.id, item.id)}?tab=${value}`, { scroll: false }),
  };
}

function TeacherPanels({ item }: { item: ItemDetail }) {
  const t = useTranslations('itemView');
  const tWorkspace = useTranslations('workspace');
  const { patch } = useItemPatcher(item);
  const hasResource = item.type !== 'page' && item.type !== 'assignment' && item.type !== 'quiz';
  return (
    <>
      <TabsContent value="content" className="mt-0">
        <SideLayout aside={<ItemFacts item={item} author />}>
          <Panel title={tWorkspace(contentHeading(item.type))}>
            <ContentEditor
              draftKey={`item:${item.id}:content`}
              initial={item.content}
              label={t('contentLabel')}
              save={(content) => patch({ content })}
            />
          </Panel>
          {hasResource ? (
            <Panel title={tWorkspace('material')}>
              <ResourceView item={{ ...item, content: null }} />
            </Panel>
          ) : null}
        </SideLayout>
      </TabsContent>
      <TabsContent value="submissions" className="mt-0">
        <AssignmentSubmissions item={item} />
      </TabsContent>
      <TabsContent value="questions" className="mt-0">
        <QuizBuilder item={item} />
      </TabsContent>
      <TabsContent value="attempts" className="mt-0">
        <AttemptsReport itemId={item.id} />
      </TabsContent>
      <TabsContent value="discussions" className="mt-0">
        <ForumView item={item} />
      </TabsContent>
      <TabsContent value="settings" className="mt-0">
        <ItemSettingsForm item={item} />
      </TabsContent>
    </>
  );
}

/** Main column + 20rem side panel on wide screens (Stitch canvas + inspector); stacks on smaller ones. */
function SideLayout({ aside, children }: { aside: ReactNode; children: ReactNode }) {
  if (!aside) return <div className="flex flex-col gap-4">{children}</div>;
  return (
    <div className="grid grid-cols-1 items-start gap-4 xl:grid-cols-[minmax(0,1fr)_20rem]">
      <div className="flex min-w-0 flex-col gap-4">{children}</div>
      <div className="xl:sticky xl:top-[calc(var(--size-header)+1rem)]">{aside}</div>
    </div>
  );
}

function ItemBreadcrumbs({ title, moduleTitle }: { title: string; moduleTitle?: string }) {
  const tShell = useTranslations('shell');
  const { course } = useCourseContext();
  return (
    <Breadcrumbs
      label={tShell('breadcrumbs')}
      items={[
        { label: tShell('myCourses'), href: ROUTES.courses },
        { label: course.title, href: ROUTES.course(course.id) },
        ...(moduleTitle ? [{ label: moduleTitle }] : []),
        { label: title },
      ]}
    />
  );
}

/**
 * Item page by type (SPEC §10) in the Stitch panel language: a header card (breadcrumbs, type eyebrow, headline title,
 * status/deadline chips, pill tabs for authors) above stacked 2rem panels. Teachers get edit tabs, learners the
 * reader view; locked items explain why (UX-07).
 */
export function ItemPage({ itemId }: { itemId: string }) {
  const t = useTranslations('itemView');
  const tCommon = useTranslations('common');
  const tTypes = useTranslations('itemTypes');
  const tWorkspace = useTranslations('workspace');
  const { course, editMode, can } = useCourseContext();
  const item = useItem(itemId);
  const outline = useOutline(course.id);
  const found = findItem(outline.data, itemId);
  const outlineEntry = found?.item;
  const moduleTitle = found?.module.title;

  if (item.isLoading) return <SkeletonList label={tCommon('loading')} />;
  if (item.isError || !item.data) {
    const notFound = isApiProblem(item.error) && item.error.status === HTTP_STATUS.notFound;
    if (outlineEntry && !outlineEntry.availability.available) {
      const Icon = ITEM_TYPE_ICONS[outlineEntry.type];
      return (
        <div className="flex flex-col gap-4">
          <PageHeader
            breadcrumbs={<ItemBreadcrumbs title={outlineEntry.title} moduleTitle={moduleTitle} />}
            eyebrow={
              <span className="inline-flex items-center gap-1.5">
                <Icon className="size-4" aria-hidden /> {tTypes(outlineEntry.type)}
              </span>
            }
            title={outlineEntry.title}
            meta={
              <Badge tone="neutral">
                <Lock aria-hidden /> {t('lockedChip')}
              </Badge>
            }
            actions={<BackToCourse />}
            className="mb-0"
          />
          <Panel>
            <LockedReason reasons={outlineEntry.availability.reasons} />
          </Panel>
          <ItemPager courseId={course.id} itemId={itemId} outline={outline.data} />
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
  const Icon = ITEM_TYPE_ICONS[data.type];
  const complete = outlineEntry?.completion === 'complete';

  const header = (tabs?: ReactNode) => (
    <PageHeader
      breadcrumbs={<ItemBreadcrumbs title={data.title} moduleTitle={moduleTitle} />}
      eyebrow={
        <span className="inline-flex items-center gap-1.5">
          <Icon className="size-4" aria-hidden /> {tTypes(data.type)}
        </span>
      }
      title={data.title}
      meta={
        <div className="flex flex-wrap items-center gap-2">
          {teacherView ? <StatusChip visibility={data.visibility} /> : null}
          {!teacherView && complete && data.completionRule.mode !== 'manual' ? (
            <CompletionBadge />
          ) : null}
          {data.dueAt ? (
            <span className="inline-flex items-center rounded-full bg-surface-muted px-2.5 py-0.5">
              <DueLabel dueAt={data.dueAt} />
            </span>
          ) : null}
        </div>
      }
      actions={
        teacherView ? (
          <Button asChild variant="secondary" size="sm">
            <Link href={`${ROUTES.course(course.id)}?item=${data.id}`}>
              <LayoutPanelLeft aria-hidden /> {tWorkspace('openInBuilder')}
            </Link>
          </Button>
        ) : (
          <div className="flex flex-wrap items-center gap-2">
            <BackToCourse />
            <ManualCompletion item={data} complete={complete} />
          </div>
        )
      }
      className="mb-0"
    >
      {tabs}
    </PageHeader>
  );

  if (!teacherView)
    return (
      <div className="flex flex-col gap-4">
        {header()}
        <SideLayout
          aside={
            // The quiz intro shows its own «Правила попытки» panel, so facts would repeat there.
            data.type !== 'quiz' && hasItemFacts(data, false) ? (
              <ItemFacts item={data} author={false} />
            ) : null
          }
        >
          <StudentBody item={data} />
        </SideLayout>
        <ItemPager courseId={course.id} itemId={data.id} outline={outline.data} />
      </div>
    );
  return <TeacherView item={data} header={header} />;
}

function TeacherView({
  item,
  header,
}: {
  item: ItemDetail;
  header: (tabs?: ReactNode) => ReactNode;
}) {
  const t = useTranslations('itemView');
  const { tabs, initial, onChange } = useTeacherTab(item);
  return (
    <Tabs defaultValue={initial} onValueChange={onChange} className="flex flex-col gap-4">
      {header(
        <TabsList aria-label={t('tabsLabel')}>
          {tabs.map((tab) => (
            <TabsTrigger key={tab} value={tab}>
              {t(`tabs.${tab}`)}
            </TabsTrigger>
          ))}
        </TabsList>,
      )}
      <TeacherPanels item={item} />
    </Tabs>
  );
}
