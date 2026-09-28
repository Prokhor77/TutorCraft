'use client';
import {
  DndContext,
  KeyboardSensor,
  PointerSensor,
  closestCenter,
  useSensor,
  useSensors,
  type DragEndEvent,
} from '@dnd-kit/core';
import {
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { ArrowDown, ArrowUp, Dices, GripVertical, ListPlus, Save, Trash2 } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useEffect, useState } from 'react';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogContent, DialogFooter, DialogTrigger } from '@/components/ui/dialog';
import { EmptyState } from '@/components/ui/empty-state';
import { Field } from '@/components/ui/field';
import { Input, NativeSelect } from '@/components/ui/input';
import { SkeletonList } from '@/components/ui/skeleton';
import { toast } from '@/components/ui/toast';
import { useQCategories, useQuestions } from '@/features/qbank/use-qbank';
import { useQuizSlots, useSaveSlots } from '@/features/quiz/use-quiz';
import { flattenPages } from '@/lib/api/pagination';
import type { QuestionSummary, QuizSlot } from '@/lib/api/schemas/quiz';
import { cn } from '@/lib/utils/cn';
import { localId } from '@/lib/utils/ids';
import { PointsPill, QUESTION_TYPE_ICONS, QuestionTypeTag } from './question-type';

const DEFAULT_RANDOM_COUNT = 5;

function AddFromBankDialog({
  courseId,
  onAdd,
}: {
  courseId: string;
  onAdd: (questions: QuestionSummary[]) => void;
}) {
  const t = useTranslations('quizSlots');
  const tCommon = useTranslations('common');
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [selected, setSelected] = useState<Map<string, QuestionSummary>>(new Map());
  const questions = useQuestions(courseId, { q: query || undefined });
  const list = flattenPages(questions.data?.pages);
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="secondary" size="sm">
          <ListPlus aria-hidden /> {t('addFromBank')}
        </Button>
      </DialogTrigger>
      <DialogContent title={t('addFromBank')} closeLabel={tCommon('close')} className="max-w-2xl">
        <Input
          type="search"
          aria-label={t('search')}
          placeholder={t('search')}
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
        <ul className="flex max-h-80 flex-col gap-1 overflow-y-auto">
          {list.length === 0 ? (
            <li className="py-6 text-center text-sm text-text-muted">{t('bankEmpty')}</li>
          ) : null}
          {list.map((question) => (
            <li key={question.id}>
              <label className="flex cursor-pointer items-center gap-3 rounded px-2 py-2 hover:bg-surface-muted">
                <Checkbox
                  checked={selected.has(question.id)}
                  onCheckedChange={(checked) =>
                    setSelected((current) => {
                      const next = new Map(current);
                      if (checked) next.set(question.id, question);
                      else next.delete(question.id);
                      return next;
                    })
                  }
                />
                <span className="flex-1 text-sm">{question.title}</span>
                <span className="text-xs text-text-muted">{question.type}</span>
              </label>
            </li>
          ))}
        </ul>
        <DialogFooter>
          <Button
            disabled={selected.size === 0}
            onClick={() => {
              onAdd([...selected.values()]);
              setSelected(new Map());
              setOpen(false);
            }}
          >
            {t('addSelected', { count: selected.size })}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function AddRandomDialog({
  courseId,
  onAdd,
}: {
  courseId: string;
  onAdd: (slot: QuizSlot) => void;
}) {
  const t = useTranslations('quizSlots');
  const tCommon = useTranslations('common');
  const categories = useQCategories(courseId);
  const [open, setOpen] = useState(false);
  const [categoryId, setCategoryId] = useState('');
  const [tag, setTag] = useState('');
  const [count, setCount] = useState(DEFAULT_RANDOM_COUNT);
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="secondary" size="sm">
          <Dices aria-hidden /> {t('addRandom')}
        </Button>
      </DialogTrigger>
      <DialogContent
        title={t('addRandom')}
        description={t('randomHint')}
        closeLabel={tCommon('close')}
      >
        <Field label={t('category')}>
          <NativeSelect value={categoryId} onChange={(event) => setCategoryId(event.target.value)}>
            <option value="">{t('anyCategory')}</option>
            {categories.data?.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name} ({category.questionCount})
              </option>
            ))}
          </NativeSelect>
        </Field>
        <Field label={t('tag')}>
          <Input value={tag} onChange={(event) => setTag(event.target.value)} />
        </Field>
        <Field label={t('count')}>
          <Input
            type="number"
            min={1}
            value={count}
            onChange={(event) => setCount(Number(event.target.value))}
          />
        </Field>
        <DialogFooter>
          <Button
            onClick={() => {
              onAdd({
                random: { categoryId: categoryId || undefined, tag: tag || undefined, count },
                page: 0,
              });
              setOpen(false);
            }}
          >
            {t('add')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

type SlotRow = { key: string; slot: QuizSlot };
const DRAG_DISTANCE_PX = 6;

type SlotCardProps = {
  row: SlotRow;
  index: number;
  total: number;
  meta: QuestionSummary | undefined;
  compact?: boolean;
  selected?: boolean;
  onSelect?: () => void;
  onChange: (patch: Partial<QuizSlot>) => void;
  onMove: (delta: -1 | 1) => void;
  onRemove: () => void;
};

function SlotActions({
  index,
  total,
  onMove,
  onRemove,
  className,
}: Pick<SlotCardProps, 'index' | 'total' | 'onMove' | 'onRemove'> & { className?: string }) {
  const t = useTranslations('quizSlots');
  return (
    <div className={cn('flex shrink-0 gap-1', className)}>
      <Button
        variant="ghost"
        size="icon-sm"
        disabled={index === 0}
        onClick={() => onMove(-1)}
        aria-label={t('moveUp')}
      >
        <ArrowUp aria-hidden />
      </Button>
      <Button
        variant="ghost"
        size="icon-sm"
        disabled={index === total - 1}
        onClick={() => onMove(1)}
        aria-label={t('moveDown')}
      >
        <ArrowDown aria-hidden />
      </Button>
      <Button variant="ghost" size="icon-sm" onClick={onRemove} aria-label={t('remove')}>
        <Trash2 aria-hidden />
      </Button>
    </div>
  );
}

function SlotCard({
  row,
  index,
  total,
  meta,
  compact,
  selected,
  onSelect,
  onChange,
  onMove,
  onRemove,
}: SlotCardProps) {
  const t = useTranslations('quizSlots');
  const tTypes = useTranslations('qbank.types');
  const sortable = useSortable({ id: row.key });
  const { slot } = row;
  const title =
    'questionId' in slot
      ? (meta?.title ?? slot.questionId)
      : t('randomSlot', {
          count: slot.random.count,
          source: slot.random.tag ?? slot.random.categoryId ?? t('anyCategory'),
        });
  const TypeIcon = meta ? QUESTION_TYPE_ICONS[meta.type] : Dices;
  const titleNode = onSelect ? (
    <button
      type="button"
      onClick={onSelect}
      aria-pressed={selected}
      className={cn(
        'rounded-sm text-left font-heading font-semibold hover:text-primary focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20',
        compact ? 'line-clamp-2 text-base' : 'text-lg',
        selected && 'text-primary',
      )}
    >
      {title}
    </button>
  ) : (
    <p className={cn('font-heading font-semibold', compact ? 'line-clamp-2 text-base' : 'text-lg')}>
      {title}
    </p>
  );
  const pointsInput = (
    <label className="flex items-center gap-2 text-sm text-text-muted">
      {t('points')}
      <Input
        type="number"
        min={0}
        className={compact ? 'h-8 w-16 px-2' : 'h-9 w-24'}
        value={slot.points ?? ''}
        onChange={(event) =>
          onChange({ points: event.target.value ? Number(event.target.value) : undefined })
        }
      />
    </label>
  );
  const grip = (
    <button
      type="button"
      className="cursor-grab touch-none rounded-full p-1 text-text-muted hover:bg-accent/10 hover:text-primary focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20 active:cursor-grabbing"
      aria-label={t('drag', { index: index + 1 })}
      {...sortable.attributes}
      {...sortable.listeners}
    >
      <GripVertical className={compact ? 'size-4' : 'size-5'} aria-hidden />
    </button>
  );
  return (
    <li
      ref={sortable.setNodeRef}
      style={{
        transform: CSS.Transform.toString(sortable.transform),
        transition: sortable.transition,
      }}
      className={cn(
        'border bg-surface shadow-sm transition-[box-shadow,border-color] duration-fast hover:shadow-md',
        compact ? 'flex flex-col gap-2 rounded-md p-3' : 'flex items-start gap-3 rounded-lg p-5',
        selected
          ? 'border-accent/50 bg-primary-soft/40 ring-1 ring-accent/30'
          : 'border-card-border',
        sortable.isDragging && 'z-10 scale-[1.02] shadow-lg',
      )}
    >
      {compact ? (
        <>
          {/* Stitch structure card: «Q1 · Один вариант» chip, points chip, title, actions. */}
          <div className="flex items-center gap-1.5">
            {grip}
            <span
              className={cn(
                'flex min-w-0 items-center gap-1.5 rounded-full px-2.5 py-1 text-label-md',
                selected ? 'bg-primary text-primary-foreground' : 'bg-primary-soft text-primary',
              )}
            >
              <TypeIcon className="size-3.5 shrink-0" aria-hidden />
              <span className="truncate">
                {t('short', { index: index + 1 })} · {meta ? tTypes(meta.type) : t('random')}
              </span>
            </span>
            {slot.points !== undefined ? (
              <PointsPill points={slot.points} className="ml-auto h-6 shrink-0 px-2" />
            ) : null}
          </div>
          {titleNode}
          <div className="flex items-center justify-between gap-2">
            {pointsInput}
            <SlotActions index={index} total={total} onMove={onMove} onRemove={onRemove} />
          </div>
        </>
      ) : (
        <>
          <span className="mt-0.5">{grip}</span>
          <div className="flex min-w-0 flex-1 flex-col gap-3">
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-label-md uppercase text-text-muted">
                {t('number', { index: index + 1 })}
              </span>
              {meta ? (
                <QuestionTypeTag type={meta.type} />
              ) : (
                <span className="inline-flex items-center gap-2 text-label-md uppercase text-text-muted">
                  <span className="flex size-7 items-center justify-center rounded-full bg-accent/10 text-primary">
                    <Dices className="size-3.5" aria-hidden />
                  </span>
                  {t('random')}
                </span>
              )}
              {slot.points !== undefined ? <PointsPill points={slot.points} /> : null}
            </div>
            {titleNode}
            <div className="flex flex-wrap items-center gap-3">
              {pointsInput}
              <label className="flex items-center gap-2 text-sm text-text-muted">
                {t('page')}
                <Input
                  type="number"
                  min={0}
                  className="h-9 w-20"
                  value={slot.page}
                  onChange={(event) => onChange({ page: Number(event.target.value) })}
                />
              </label>
            </div>
          </div>
          <SlotActions
            index={index}
            total={total}
            onMove={onMove}
            onRemove={onRemove}
            className="flex-col sm:flex-row"
          />
        </>
      )}
    </li>
  );
}

/** Quiz composition (FR-QUIZ-02): Stitch question cards with drag grip; fixed bank questions and random picks. */
export function SlotsEditor({
  courseId,
  itemId,
  compact,
  selectedQuestionId,
  onSelectQuestion,
}: {
  courseId: string;
  itemId: string;
  /** Narrow left pane of the quiz builder. */
  compact?: boolean;
  selectedQuestionId?: string | null;
  /** Fixed bank questions become selectable (opens the question editor). */
  onSelectQuestion?: (questionId: string, number: number) => void;
}) {
  const t = useTranslations('quizSlots');
  const tCommon = useTranslations('common');
  const slotsQuery = useQuizSlots(itemId);
  const save = useSaveSlots(itemId);
  const [rows, setRows] = useState<SlotRow[]>([]);
  const [questions, setQuestions] = useState<Map<string, QuestionSummary>>(new Map());
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: DRAG_DISTANCE_PX } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  useEffect(() => {
    if (!slotsQuery.data) return;
    setRows(slotsQuery.data.slots.map((slot) => ({ key: localId('slot'), slot })));
    setQuestions(
      new Map((slotsQuery.data.questions ?? []).map((question) => [question.id, question])),
    );
  }, [slotsQuery.data]);

  const update = (key: string, patch: Partial<QuizSlot>) =>
    setRows((current) =>
      current.map((row) =>
        row.key === key ? { ...row, slot: { ...row.slot, ...patch } as QuizSlot } : row,
      ),
    );
  const move = (from: number, to: number) =>
    setRows((current) => {
      if (to < 0 || to >= current.length) return current;
      const next = [...current];
      const [moved] = next.splice(from, 1);
      next.splice(to, 0, moved as SlotRow);
      return next;
    });
  const onDragEnd = ({ active, over }: DragEndEvent) => {
    if (!over || active.id === over.id) return;
    move(
      rows.findIndex((row) => row.key === active.id),
      rows.findIndex((row) => row.key === over.id),
    );
  };

  if (slotsQuery.isLoading) return <SkeletonList label={tCommon('loading')} />;
  return (
    <div className="flex flex-col gap-4">
      <div className={cn('flex flex-wrap gap-2', compact && 'order-last')}>
        <AddFromBankDialog
          courseId={courseId}
          onAdd={(added) => {
            setQuestions(
              (current) =>
                new Map([...current, ...added.map((question) => [question.id, question] as const)]),
            );
            setRows((current) => [
              ...current,
              ...added.map((question) => ({
                key: localId('slot'),
                slot: { questionId: question.id, page: 0 },
              })),
            ]);
          }}
        />
        <AddRandomDialog
          courseId={courseId}
          onAdd={(slot) => setRows((current) => [...current, { key: localId('slot'), slot }])}
        />
        <Button
          className={compact ? 'w-full' : 'ml-auto'}
          loading={save.isPending}
          onClick={() =>
            save.mutate(
              rows.map((row) => row.slot),
              { onSuccess: () => toast({ tone: 'success', title: t('saved') }) },
            )
          }
        >
          <Save aria-hidden /> {tCommon('save')}
        </Button>
      </div>
      {rows.length === 0 ? (
        <EmptyState icon={ListPlus} title={t('emptyTitle')} description={t('emptyText')} />
      ) : null}
      <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={onDragEnd}>
        <SortableContext items={rows.map((row) => row.key)} strategy={verticalListSortingStrategy}>
          <ol className="flex flex-col gap-3">
            {rows.map((row, index) => (
              <SlotCard
                key={row.key}
                row={row}
                index={index}
                total={rows.length}
                meta={'questionId' in row.slot ? questions.get(row.slot.questionId) : undefined}
                compact={compact}
                selected={'questionId' in row.slot && row.slot.questionId === selectedQuestionId}
                onSelect={
                  onSelectQuestion && 'questionId' in row.slot
                    ? () =>
                        'questionId' in row.slot && onSelectQuestion(row.slot.questionId, index + 1)
                    : undefined
                }
                onChange={(patch) => update(row.key, patch)}
                onMove={(delta) => move(index, index + delta)}
                onRemove={() =>
                  setRows((current) => current.filter((entry) => entry.key !== row.key))
                }
              />
            ))}
          </ol>
        </SortableContext>
      </DndContext>
    </div>
  );
}
