'use client';
import { ArrowDown, ArrowUp, Dices, ListPlus, Save, Trash2 } from 'lucide-react';
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
              <label className="flex cursor-pointer items-center gap-3 rounded-md px-2 py-2 hover:bg-surface-muted">
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

/** Quiz composition (FR-QUIZ-02): fixed questions from the bank and/or random N from a category/tag, pages, points. */
export function SlotsEditor({ courseId, itemId }: { courseId: string; itemId: string }) {
  const t = useTranslations('quizSlots');
  const tCommon = useTranslations('common');
  const slotsQuery = useQuizSlots(itemId);
  const save = useSaveSlots(itemId);
  const [slots, setSlots] = useState<QuizSlot[]>([]);
  const [titles, setTitles] = useState<Map<string, string>>(new Map());

  useEffect(() => {
    if (!slotsQuery.data) return;
    setSlots(slotsQuery.data.slots);
    setTitles(
      new Map((slotsQuery.data.questions ?? []).map((question) => [question.id, question.title])),
    );
  }, [slotsQuery.data]);

  const update = (index: number, patch: Partial<QuizSlot>) =>
    setSlots((current) =>
      current.map((slot, i) => (i === index ? ({ ...slot, ...patch } as QuizSlot) : slot)),
    );
  const move = (index: number, delta: -1 | 1) =>
    setSlots((current) => {
      const target = index + delta;
      if (target < 0 || target >= current.length) return current;
      const next = [...current];
      [next[index], next[target]] = [next[target] as QuizSlot, next[index] as QuizSlot];
      return next;
    });

  if (slotsQuery.isLoading) return <SkeletonList label={tCommon('loading')} />;
  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap gap-2">
        <AddFromBankDialog
          courseId={courseId}
          onAdd={(questions) => {
            setTitles(
              (current) =>
                new Map([
                  ...current,
                  ...questions.map((question) => [question.id, question.title] as const),
                ]),
            );
            setSlots((current) => [
              ...current,
              ...questions.map((question) => ({ questionId: question.id, page: 0 })),
            ]);
          }}
        />
        <AddRandomDialog
          courseId={courseId}
          onAdd={(slot) => setSlots((current) => [...current, slot])}
        />
        <Button
          size="sm"
          className="ml-auto"
          loading={save.isPending}
          onClick={() =>
            save.mutate(slots, { onSuccess: () => toast({ tone: 'success', title: t('saved') }) })
          }
        >
          <Save aria-hidden /> {tCommon('save')}
        </Button>
      </div>
      {slots.length === 0 ? (
        <EmptyState icon={ListPlus} title={t('emptyTitle')} description={t('emptyText')} />
      ) : null}
      <ol className="flex flex-col gap-2">
        {slots.map((slot, index) => (
          <li
            key={index}
            className="flex flex-wrap items-center gap-3 rounded-md border border-border bg-surface p-3"
          >
            <span className="w-6 text-sm font-semibold text-text-muted">{index + 1}</span>
            <span className="min-w-40 flex-1 text-sm">
              {'questionId' in slot
                ? (titles.get(slot.questionId) ?? slot.questionId)
                : t('randomSlot', {
                    count: slot.random.count,
                    source: slot.random.tag ?? slot.random.categoryId ?? t('anyCategory'),
                  })}
            </span>
            <label className="flex items-center gap-1.5 text-xs">
              {t('points')}
              <Input
                type="number"
                min={0}
                className="h-8 w-20"
                value={slot.points ?? ''}
                onChange={(event) =>
                  update(index, {
                    points: event.target.value ? Number(event.target.value) : undefined,
                  })
                }
              />
            </label>
            <label className="flex items-center gap-1.5 text-xs">
              {t('page')}
              <Input
                type="number"
                min={0}
                className="h-8 w-16"
                value={slot.page}
                onChange={(event) => update(index, { page: Number(event.target.value) })}
              />
            </label>
            <Button
              variant="ghost"
              size="icon-sm"
              disabled={index === 0}
              onClick={() => move(index, -1)}
              aria-label={t('moveUp')}
            >
              <ArrowUp aria-hidden />
            </Button>
            <Button
              variant="ghost"
              size="icon-sm"
              disabled={index === slots.length - 1}
              onClick={() => move(index, 1)}
              aria-label={t('moveDown')}
            >
              <ArrowDown aria-hidden />
            </Button>
            <Button
              variant="ghost"
              size="icon-sm"
              onClick={() => setSlots((current) => current.filter((_, i) => i !== index))}
              aria-label={t('remove')}
            >
              <Trash2 aria-hidden />
            </Button>
          </li>
        ))}
      </ol>
    </div>
  );
}
