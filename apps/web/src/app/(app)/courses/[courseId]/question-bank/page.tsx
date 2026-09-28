'use client';
import {
  ChevronRight,
  Folder,
  FolderPlus,
  FolderTree,
  Library,
  ListChecks,
  Pencil,
  Plus,
  Search,
  SlidersHorizontal,
  Timer,
  Trash2,
} from 'lucide-react';
import Link from 'next/link';
import { useLocale, useTranslations } from 'next-intl';
import { useDeferredValue, useState } from 'react';
import { ItemTypeIcon } from '@/components/course/item-meta';
import { StatusChip } from '@/components/course/status-chip';
import { QuestionEditor } from '@/components/qbank/question-editor';
import { QuestionTypeTag } from '@/components/quiz/question-type';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/ui/empty-state';
import { Input, NativeSelect } from '@/components/ui/input';
import { LoadMore } from '@/components/ui/load-more';
import { Breadcrumbs, PageHeader } from '@/components/ui/page-header';
import { StatCard, StatGrid } from '@/components/ui/stat-card';
import { ROUTES } from '@/features/auth/routes';
import { flattenModules, useOutline } from '@/features/courses/use-outline';
import { SkeletonList } from '@/components/ui/skeleton';
import { useCourseContext } from '@/features/courses/course-context';
import { useQbankMutations, useQCategories, useQuestions } from '@/features/qbank/use-qbank';
import { flattenPages } from '@/lib/api/pagination';
import { QUESTION_TYPES, type QCategory } from '@/lib/api/schemas/quiz';
import { cn } from '@/lib/utils/cn';
import { formatRelative } from '@/lib/utils/format';

function CategoryTree({
  categories,
  selected,
  onSelect,
  parentId = null,
  depth = 0,
}: {
  categories: QCategory[];
  selected: string | null;
  onSelect: (id: string | null) => void;
  parentId?: string | null;
  depth?: number;
}) {
  const children = categories.filter((category) => category.parentId === parentId);
  if (children.length === 0) return null;
  return (
    <ul className="flex flex-col gap-0.5">
      {children.map((category) => (
        <li key={category.id}>
          <button
            type="button"
            onClick={() => onSelect(category.id)}
            aria-current={selected === category.id ? 'true' : undefined}
            style={{ paddingLeft: `${0.75 + depth}rem` }}
            className={cn(
              'flex w-full items-center gap-2 rounded-full py-2 pr-3 text-left text-sm transition-colors duration-fast hover:bg-surface-muted focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20',
              selected === category.id &&
                'bg-primary-soft font-semibold text-primary hover:bg-primary-soft',
            )}
          >
            <Folder className="size-4 shrink-0" aria-hidden />
            <span className="flex-1 truncate">{category.name}</span>
            <span className="rounded-full bg-surface px-2 text-label-md text-text-muted">
              {category.questionCount}
            </span>
          </button>
          <CategoryTree
            categories={categories}
            selected={selected}
            onSelect={onSelect}
            parentId={category.id}
            depth={depth + 1}
          />
        </li>
      ))}
    </ul>
  );
}

/** «Тесты курса»: quiz items of the outline, each opening its quiz builder (Stitch «Конструктор тестов»). */
function CourseQuizzes({ courseId }: { courseId: string }) {
  const t = useTranslations('qbank');
  const outline = useOutline(courseId);
  const quizzes = flattenModules(outline.data?.modules ?? []).flatMap((module) =>
    module.items
      .filter((item) => item.type === 'quiz')
      .map((item) => ({ ...item, moduleTitle: module.title })),
  );
  if (quizzes.length === 0) return null;
  return (
    <section aria-labelledby="course-quizzes" className="flex flex-col gap-3">
      <h2 id="course-quizzes" className="text-lg">
        {t('courseQuizzes')}
      </h2>
      <ul className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
        {quizzes.map((quiz) => (
          <li key={quiz.id}>
            <Link
              href={`${ROUTES.item(courseId, quiz.id)}?tab=questions`}
              className="lift flex h-full items-start gap-3 rounded-lg border border-card-border bg-surface p-5 shadow-sm hover:border-card-border-hover hover:shadow-md focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20"
            >
              <ItemTypeIcon type="quiz" className="size-10 rounded-full" />
              <span className="flex min-w-0 flex-1 flex-col gap-1">
                <span className="truncate font-heading text-base font-semibold">{quiz.title}</span>
                <span className="truncate text-xs text-text-muted">{quiz.moduleTitle}</span>
                <StatusChip visibility={quiz.visibility} className="self-start" />
              </span>
              <ChevronRight className="mt-2 size-4 shrink-0 text-text-muted" aria-hidden />
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}

/** Question bank (FR-QBANK-01/02): categories tree, filters by text/type/tag, editor. */
export default function QuestionBankPage() {
  const t = useTranslations('qbank');
  const tCommon = useTranslations('common');
  const tShell = useTranslations('shell');
  const locale = useLocale();
  const { course } = useCourseContext();
  const categories = useQCategories(course.id);
  const { createCategory, remove } = useQbankMutations(course.id);
  const [categoryId, setCategoryId] = useState<string | null>(null);
  const [type, setType] = useState('');
  const [tag, setTag] = useState('');
  const [query, setQuery] = useState('');
  const deferredQuery = useDeferredValue(query.trim());
  const questions = useQuestions(course.id, {
    categoryId: categoryId ?? undefined,
    type: type || undefined,
    tag: tag || undefined,
    q: deferredQuery || undefined,
  });
  const [editor, setEditor] = useState<{ open: boolean; id: string | null }>({
    open: false,
    id: null,
  });
  const [newCategory, setNewCategory] = useState('');
  const list = flattenPages(questions.data?.pages);

  const outline = useOutline(course.id);
  const quizCount = flattenModules(outline.data?.modules ?? [])
    .flatMap((module) => module.items)
    .filter((item) => item.type === 'quiz').length;
  // Unfiltered first page (shared cache with the list when no filter is set) → honest bank size.
  const allQuestions = useQuestions(course.id, {});
  const bankLoaded = flattenPages(allQuestions.data?.pages).length;
  const bankCount = allQuestions.hasNextPage ? `${bankLoaded}+` : bankLoaded;
  const filtered = categoryId !== null || !!type || !!tag || !!deferredQuery;

  return (
    <div className="flex flex-col gap-gutter">
      <PageHeader
        className="mb-0"
        breadcrumbs={
          <Breadcrumbs
            label={tShell('breadcrumbs')}
            items={[
              { label: course.title, href: ROUTES.course(course.id) },
              { label: t('pageTitle') },
            ]}
          />
        }
        title={t('pageTitle')}
        description={t('pageDescription')}
        actions={
          <Button onClick={() => setEditor({ open: true, id: null })}>
            <Plus aria-hidden /> {t('newQuestion')}
          </Button>
        }
      />
      <StatGrid className="lg:grid-cols-3">
        <StatCard label={t('statQuizzes')} icon={Timer} value={quizCount} />
        <StatCard
          label={t('statBankQuestions')}
          icon={ListChecks}
          tone="warning"
          value={allQuestions.isLoading ? '…' : bankCount}
        />
        <StatCard
          label={t('statCategories')}
          icon={FolderTree}
          tone="success"
          value={categories.data?.length ?? 0}
        />
      </StatGrid>
      <CourseQuizzes courseId={course.id} />
      <div className="grid grid-cols-1 items-start gap-gutter lg:grid-cols-[17rem_minmax(0,1fr)]">
        <aside
          aria-labelledby="qbank-categories"
          className="flex flex-col gap-3 rounded-lg border border-card-border bg-surface p-4 shadow-sm lg:sticky lg:top-[calc(var(--size-header)+1rem)]"
        >
          <h2 id="qbank-categories" className="flex items-center gap-2 px-2 text-lg">
            <FolderTree className="size-5 text-primary" aria-hidden /> {t('categories')}
          </h2>
          <button
            type="button"
            onClick={() => setCategoryId(null)}
            aria-current={categoryId === null ? 'true' : undefined}
            className={cn(
              'flex items-center gap-2 rounded-full px-3 py-2 text-left text-sm transition-colors duration-fast hover:bg-surface-muted focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20',
              categoryId === null &&
                'bg-primary-soft font-semibold text-primary hover:bg-primary-soft',
            )}
          >
            <Library className="size-4 shrink-0" aria-hidden />
            {t('allQuestions')}
          </button>
          <CategoryTree
            categories={categories.data ?? []}
            selected={categoryId}
            onSelect={setCategoryId}
          />
          <form
            className="flex items-center gap-1.5 border-t border-border pt-3"
            onSubmit={(event) => {
              event.preventDefault();
              if (!newCategory.trim()) return;
              createCategory.mutate(
                { name: newCategory.trim(), parentId: categoryId },
                { onSuccess: () => setNewCategory('') },
              );
            }}
          >
            <Input
              aria-label={t('newCategory')}
              placeholder={t('newCategory')}
              value={newCategory}
              onChange={(event) => setNewCategory(event.target.value)}
              className="h-9 text-sm"
            />
            <Button
              type="submit"
              size="icon-sm"
              variant="secondary"
              aria-label={t('addCategory')}
              loading={createCategory.isPending}
            >
              <FolderPlus aria-hidden />
            </Button>
          </form>
        </aside>
        <section aria-labelledby="qbank-questions" className="flex min-w-0 flex-col gap-4">
          <div className="flex flex-col gap-3 rounded-lg border border-card-border bg-surface p-4 shadow-sm">
            <div className="flex items-center gap-2 px-1">
              <SlidersHorizontal className="size-4 text-primary" aria-hidden />
              <h2 id="qbank-questions" className="flex-1 text-lg">
                {t('questionsTitle')}
              </h2>
              {questions.isSuccess ? (
                <span className="rounded-full bg-surface-muted px-2.5 py-0.5 text-label-md text-text-muted">
                  {questions.hasNextPage ? `${list.length}+` : list.length}
                  {filtered ? ` · ${t('filtered')}` : ''}
                </span>
              ) : null}
            </div>
            <div className="flex flex-col gap-2 sm:flex-row">
              <div className="relative flex-1">
                <Search
                  className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-text-muted"
                  aria-hidden
                />
                <Input
                  type="search"
                  aria-label={t('search')}
                  placeholder={t('search')}
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                  className="pl-10"
                />
              </div>
              <NativeSelect
                aria-label={t('filterType')}
                value={type}
                onChange={(event) => setType(event.target.value)}
                className="sm:w-52"
              >
                <option value="">{t('allTypes')}</option>
                {QUESTION_TYPES.map((option) => (
                  <option key={option} value={option}>
                    {t(`types.${option}`)}
                  </option>
                ))}
              </NativeSelect>
              <Input
                aria-label={t('filterTag')}
                placeholder={t('filterTag')}
                value={tag}
                onChange={(event) => setTag(event.target.value)}
                className="sm:w-36"
              />
            </div>
          </div>
          {questions.isLoading ? <SkeletonList label={tCommon('loading')} /> : null}
          {questions.isSuccess && list.length === 0 ? (
            <EmptyState
              icon={Library}
              title={t('emptyTitle')}
              description={t('emptyText')}
              action={
                <Button onClick={() => setEditor({ open: true, id: null })}>
                  <Plus aria-hidden /> {t('newQuestion')}
                </Button>
              }
            />
          ) : null}
          <ul className="flex flex-col gap-3">
            {list.map((question) => (
              <li
                key={question.id}
                className="group flex items-center gap-3 rounded-lg border border-card-border bg-surface py-4 pl-5 pr-3 shadow-sm transition-[box-shadow,border-color] duration-fast focus-within:border-card-border-hover hover:border-card-border-hover hover:shadow-md sm:pl-6"
              >
                <button
                  type="button"
                  className="flex min-w-0 flex-1 flex-col gap-2 rounded-sm text-left focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20"
                  onClick={() => setEditor({ open: true, id: question.id })}
                >
                  <QuestionTypeTag type={question.type} />
                  <span className="truncate font-heading text-lg font-semibold group-hover:text-primary">
                    {question.title}
                  </span>
                  <span className="flex flex-wrap items-center gap-1.5 text-xs text-text-muted">
                    <span className="rounded-full bg-surface-muted px-2 py-0.5 text-label-md">
                      v{question.version}
                    </span>
                    {formatRelative(question.updatedAt, locale)} ·{' '}
                    {t('usedIn', { count: question.usedInQuizzes })}
                    {question.tags.map((entry) => (
                      <Badge key={entry} tone="info">
                        {entry}
                      </Badge>
                    ))}
                  </span>
                </button>
                <Button
                  variant="ghost"
                  size="icon-sm"
                  className="hidden sm:inline-flex"
                  aria-label={t('editQuestion', { title: question.title })}
                  onClick={() => setEditor({ open: true, id: question.id })}
                >
                  <Pencil aria-hidden />
                </Button>
                <Button
                  variant="ghost"
                  size="icon-sm"
                  className="hover:bg-danger-soft hover:text-danger"
                  aria-label={t('deleteQuestion', { title: question.title })}
                  onClick={() => remove.mutate(question.id)}
                >
                  <Trash2 aria-hidden />
                </Button>
              </li>
            ))}
          </ul>
          <LoadMore
            hasMore={!!questions.hasNextPage}
            loading={questions.isFetchingNextPage}
            onClick={() => void questions.fetchNextPage()}
            label={tCommon('loadMore')}
          />
        </section>
        <QuestionEditor
          courseId={course.id}
          questionId={editor.id}
          open={editor.open}
          onOpenChange={(open) => setEditor((current) => ({ ...current, open }))}
          categories={categories.data ?? []}
          defaultCategoryId={categoryId}
        />
      </div>
    </div>
  );
}
