'use client';
import {
  ArrowLeft,
  ClipboardCheck,
  ExternalLink,
  FileQuestion,
  MessagesSquare,
  MoreHorizontal,
  Plus,
  Timer,
} from 'lucide-react';
import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import { ContentEditor } from '@/components/items/content-editor';
import { ResourceView } from '@/components/items/resource-views';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { InlineEdit } from '@/components/ui/inline-edit';
import { SkeletonList } from '@/components/ui/skeleton';
import { ROUTES } from '@/features/auth/routes';
import { flattenModules, useOutline } from '@/features/courses/use-outline';
import { useItem, useItemPatcher } from '@/features/items/use-item';
import { quizSummary } from '@/features/quiz/quiz-summary';
import { useQuizSlots } from '@/features/quiz/use-quiz';
import type { ItemType } from '@/lib/api/schemas/common';
import type { ItemDetail } from '@/lib/api/schemas/courses';
import { SECONDS_PER_MINUTE } from '@/lib/utils/time';
import { DueLabel, ITEM_TYPE_ICONS, ItemTypeIcon } from '../item-meta';
import { ItemTypePicker } from '../item-type-picker';
import { StatusChip } from '../status-chip';

/** Quick-add types of the Stitch canvas action bar («+ Видеолекция · + Конспект · + Задание / Тест · + Ссылка»). */
export const CANVAS_ADD_TYPES: readonly ItemType[] = ['video', 'page', 'assignment', 'quiz', 'url'];

/** Heading of the content card per item type. */
function contentHeading(type: ItemType): string {
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

/** «Модуль 2 · Элемент 3» from the cached outline (modules numbered in tree order). */
function usePositionLabel(
  courseId: string,
  moduleId: string | undefined,
  itemId: string,
): { module: number; element: number } | null {
  const outline = useOutline(courseId);
  const modules = flattenModules(outline.data?.modules ?? []);
  const moduleIndex = modules.findIndex((module) => module.id === moduleId);
  const elementIndex = modules[moduleIndex]?.items.findIndex((entry) => entry.id === itemId) ?? -1;
  if (moduleIndex < 0 || elementIndex < 0) return null;
  return { module: moduleIndex + 1, element: elementIndex + 1 };
}

function QuizCard({ item }: { item: ItemDetail }) {
  const t = useTranslations('workspace');
  const slots = useQuizSlots(item.id);
  const summary = slots.data ? quizSummary(slots.data.slots) : null;
  return (
    <Card className="flex flex-col gap-4 p-6">
      <div className="flex items-center gap-2">
        <FileQuestion className="size-5 text-primary" aria-hidden />
        <h3 className="text-lg">{t('quizQuestions')}</h3>
      </div>
      {summary ? (
        <p className="text-sm text-text-muted">
          {t('quizSummary', {
            questions: summary.questions,
            points: summary.points,
            partial: summary.partial ? 'yes' : 'no',
          })}
        </p>
      ) : null}
      <Button asChild className="self-start">
        <Link href={`${ROUTES.item(item.courseId, item.id)}?tab=questions`}>
          <FileQuestion aria-hidden /> {t('openQuizBuilder')}
        </Link>
      </Button>
    </Card>
  );
}

function LinkCard({
  icon: Icon,
  title,
  text,
  href,
  action,
}: {
  icon: typeof ClipboardCheck;
  title: string;
  text: string;
  href: string;
  action: string;
}) {
  return (
    <Card className="flex flex-col gap-3 p-6">
      <div className="flex items-center gap-2">
        <Icon className="size-5 text-primary" aria-hidden />
        <h3 className="text-lg">{title}</h3>
      </div>
      <p className="text-sm text-text-muted">{text}</p>
      <Button asChild variant="secondary" className="self-start">
        <Link href={href}>{action}</Link>
      </Button>
    </Card>
  );
}

function WorkspaceBody({ item }: { item: ItemDetail }) {
  const t = useTranslations('workspace');
  const { patch } = useItemPatcher(item);
  const itemHref = ROUTES.item(item.courseId, item.id);
  const hasResource = !['page', 'assignment', 'quiz', 'forum'].includes(item.type);
  return (
    <>
      {hasResource ? (
        <Card className="flex flex-col gap-4 p-6">
          <h3 className="text-lg">{t('material')}</h3>
          <ResourceView item={{ ...item, content: null }} />
        </Card>
      ) : null}
      {item.type !== 'forum' ? (
        <Card className="flex flex-col gap-4 p-6">
          <h3 className="text-lg">{t(contentHeading(item.type))}</h3>
          <ContentEditor
            key={item.id}
            draftKey={`item:${item.id}:content`}
            initial={item.content}
            label={t(contentHeading(item.type))}
            save={(content) => patch({ content })}
          />
        </Card>
      ) : null}
      {item.type === 'quiz' ? <QuizCard item={item} /> : null}
      {item.type === 'assignment' ? (
        <LinkCard
          icon={ClipboardCheck}
          title={t('submissionsTitle')}
          text={t('submissionsText')}
          href={`${itemHref}?tab=submissions`}
          action={t('openSubmissions')}
        />
      ) : null}
      {item.type === 'forum' ? (
        <LinkCard
          icon={MessagesSquare}
          title={t('forumTitle')}
          text={t('forumText')}
          href={`${itemHref}?tab=discussions`}
          action={t('openForum')}
        />
      ) : null}
    </>
  );
}

/**
 * Builder canvas for the selected item (Stitch center stage, ≤ 860px): header card with position, status,
 * deadline and time-limit chips and inline rename, then the type's working blocks.
 */
export function ItemWorkspace({
  courseId,
  itemId,
  onBack,
}: {
  courseId: string;
  itemId: string;
  onBack: () => void;
}) {
  const t = useTranslations('workspace');
  const tTypes = useTranslations('itemTypes');
  const tCommon = useTranslations('common');
  const item = useItem(itemId);
  const { patch } = useItemPatcher(item.data);
  const position = usePositionLabel(courseId, item.data?.moduleId, itemId);
  if (item.isLoading || !item.data) return <SkeletonList label={tCommon('loading')} rows={4} />;
  const data = item.data;
  const timeLimit =
    data.settings.kind === 'quiz' && data.settings.timeLimitSec
      ? Math.round(data.settings.timeLimitSec / SECONDS_PER_MINUTE)
      : null;
  return (
    <div className="flex flex-col gap-4">
      <Card className="flex flex-col gap-4 p-6">
        <div className="flex flex-wrap items-center gap-2">
          {position ? (
            <Badge tone="primary">
              {t('position', { module: position.module, element: position.element })}
            </Badge>
          ) : null}
          <StatusChip visibility={data.visibility} />
          {timeLimit ? (
            <Badge tone="neutral">
              <Timer aria-hidden /> {t('minutes', { minutes: timeLimit })}
            </Badge>
          ) : null}
          <DueLabel dueAt={data.dueAt} className="text-xs" />
        </div>
        <div className="flex items-start gap-3">
          <ItemTypeIcon type={data.type} className="size-11 rounded-full" />
          <div className="flex min-w-0 flex-1 flex-col gap-1">
            <span className="text-label-md uppercase text-text-muted">{tTypes(data.type)}</span>
            <h2 className="text-xl md:text-2xl">
              <InlineEdit
                value={data.title}
                label={t('rename', { title: data.title })}
                onSave={(title) => void patch({ title }).catch(() => undefined)}
              />
            </h2>
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button variant="ghost" size="sm" onClick={onBack}>
            <ArrowLeft aria-hidden /> {t('backToStructure')}
          </Button>
          <Button asChild variant="secondary" size="sm">
            <Link href={ROUTES.item(data.courseId, data.id)}>
              <ExternalLink aria-hidden /> {t('openPage')}
            </Link>
          </Button>
        </div>
      </Card>
      <WorkspaceBody item={data} />
    </div>
  );
}

/**
 * Glass action bar under the canvas (Stitch): quick-add the most common elements right after the selected one;
 * on phones a single «Добавить элемент» button opens the full type picker.
 */
export function CanvasAddBar({ onAdd }: { onAdd: (type: ItemType) => void }) {
  const t = useTranslations('workspace');
  const tTypes = useTranslations('itemTypes');
  const [moreOpen, setMoreOpen] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  return (
    <div className="sticky bottom-[calc(var(--size-bottom-nav)+env(safe-area-inset-bottom)+0.75rem)] z-20 md:bottom-4">
      <div
        role="toolbar"
        aria-label={t('addBar')}
        className="glass hidden items-center justify-center gap-1 rounded-full border border-card-border p-1.5 shadow-lg sm:flex"
      >
        {CANVAS_ADD_TYPES.map((type) => {
          const Icon = ITEM_TYPE_ICONS[type];
          return (
            <Button key={type} variant="ghost" size="sm" onClick={() => onAdd(type)}>
              <Plus aria-hidden />
              <Icon aria-hidden /> {tTypes(type)}
            </Button>
          );
        })}
        <ItemTypePicker open={moreOpen} onOpenChange={setMoreOpen} onPick={onAdd}>
          <Button variant="secondary" size="sm" aria-label={t('moreTypes')}>
            <MoreHorizontal aria-hidden />
          </Button>
        </ItemTypePicker>
      </div>
      <div className="sm:hidden">
        <ItemTypePicker open={mobileOpen} onOpenChange={setMobileOpen} onPick={onAdd}>
          <Button size="lg" className="w-full shadow-lg">
            <Plus aria-hidden /> {t('addElement')}
          </Button>
        </ItemTypePicker>
      </div>
    </div>
  );
}
