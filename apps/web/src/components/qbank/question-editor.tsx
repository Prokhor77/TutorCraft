'use client';
import { CheckCircle2, ListChecks, Save, XCircle } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useEffect, useState } from 'react';
import { BlockEditor } from '@/components/editor/block-editor';
import { COMPACT_BLOCK_KINDS } from '@/components/editor/block-kinds';
import { QuestionInput } from '@/components/quiz/question-input';
import { QUESTION_TYPE_ICONS } from '@/components/quiz/question-type';
import { SkeletonList } from '@/components/ui/skeleton';
import { cn } from '@/lib/utils/cn';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Sheet, SheetContent } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Input, NativeSelect } from '@/components/ui/input';
import { toast } from '@/components/ui/toast';
import {
  emptyQuestion,
  toPreviewView,
  validateQuestion,
  defaultQuestionData,
} from '@/features/qbank/question-data';
import { useQbankMutations, useQuestion, useQuestionVersions } from '@/features/qbank/use-qbank';
import {
  QUESTION_TYPES,
  type PreviewCheckResult,
  type Question,
  type QCategory,
  type QuestionInput as QuestionInputData,
  type QuestionResponse,
  type QuestionType,
} from '@/lib/api/schemas/quiz';
import { formatDateTime } from '@/lib/utils/format';
import { useLocale } from 'next-intl';
import { QuestionDataEditor } from './question-data-editor';

type Props = {
  courseId: string;
  questionId: string | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  categories: QCategory[];
  defaultCategoryId: string | null;
  /** Called with the saved question before the sheet closes. */
  onSaved?: (question: Question) => void;
};

function PreviewCheck({ questionId, input }: { questionId: string; input: QuestionInputData }) {
  const t = useTranslations('qbank');
  const { previewCheck } = useQbankMutations('');
  const [response, setResponse] = useState<QuestionResponse | null>(null);
  const [result, setResult] = useState<PreviewCheckResult | null>(null);
  return (
    <Card className="flex flex-col gap-3 p-4">
      <h3 className="text-base">{t('preview')}</h3>
      <QuestionInput
        question={toPreviewView(input)}
        response={response}
        onChange={(next) => setResponse(next)}
      />
      <div className="flex items-center gap-3">
        <Button
          size="sm"
          variant="secondary"
          disabled={!response}
          loading={previewCheck.isPending}
          onClick={() =>
            response && previewCheck.mutate({ id: questionId, response }, { onSuccess: setResult })
          }
        >
          {t('check')}
        </Button>
        {result ? (
          <span
            className={
              result.correct
                ? 'flex items-center gap-1.5 text-sm text-success'
                : 'flex items-center gap-1.5 text-sm text-danger'
            }
            role="status"
          >
            {result.correct ? (
              <CheckCircle2 className="size-4" aria-hidden />
            ) : (
              <XCircle className="size-4" aria-hidden />
            )}
            {t('checkResult', { score: result.score, max: result.maxScore })}
          </span>
        ) : null}
      </div>
    </Card>
  );
}

type FormProps = {
  courseId: string;
  /** null = create a new question. */
  questionId: string | null;
  categories: QCategory[];
  defaultCategoryId: string | null;
  /** Called after a successful create / new version. */
  onSaved?: (question: Question) => void;
  /** Shows a «Cancel» button next to save (quiz builder create flow). */
  onCancel?: () => void;
  /** Number shown in the Stitch card header («3 · Редактор задания»). */
  number?: number;
  /** `card` = inline Stitch question card (quiz builder, bank); `plain` = inside a sheet. */
  variant?: 'card' | 'plain';
};

/** Type pills («Один ответ · Несколько ответов · …»); locked once the question exists (versions keep the type). */
function TypePills({
  value,
  disabled,
  onChange,
}: {
  value: QuestionType;
  disabled: boolean;
  onChange: (type: QuestionType) => void;
}) {
  const t = useTranslations('qbank');
  return (
    <div
      role="radiogroup"
      aria-label={t('type')}
      className="scrollbar-none -mx-1 flex gap-1.5 overflow-x-auto px-1"
    >
      {QUESTION_TYPES.map((type) => {
        const Icon = QUESTION_TYPE_ICONS[type];
        const active = value === type;
        return (
          <button
            key={type}
            type="button"
            role="radio"
            aria-checked={active}
            disabled={disabled && !active}
            onClick={() => onChange(type)}
            className={cn(
              'flex h-9 shrink-0 items-center gap-1.5 whitespace-nowrap rounded-full px-3.5 text-label-lg transition-colors duration-fast focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20 disabled:opacity-40',
              active
                ? 'bg-primary text-primary-foreground shadow-sm'
                : 'bg-surface-muted text-text-muted hover:text-primary',
            )}
          >
            <Icon className="size-4" aria-hidden />
            {t(`types.${type}`)}
          </button>
        );
      })}
    </div>
  );
}

/** Question form (all 8 types), versions (FR-QBANK-05) and preview-check (FR-QBANK-06). */
export function QuestionForm({
  courseId,
  questionId,
  categories,
  defaultCategoryId,
  onSaved,
  onCancel,
  number,
  variant = 'plain',
}: FormProps) {
  const t = useTranslations('qbank');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const existing = useQuestion(questionId);
  const versions = useQuestionVersions(questionId);
  const { create, update } = useQbankMutations(courseId);
  const [input, setInput] = useState<QuestionInputData>(() =>
    emptyQuestion('single_choice', defaultCategoryId),
  );
  const [showIssues, setShowIssues] = useState(false);

  useEffect(() => {
    setShowIssues(false);
    if (!questionId) return setInput(emptyQuestion('single_choice', defaultCategoryId));
    if (existing.data) {
      const { id: _id, version: _version, versionId: _versionId, ...rest } = existing.data;
      setInput(rest);
    }
  }, [questionId, existing.data, defaultCategoryId]);

  const issues = validateQuestion(input);
  const set = <K extends keyof QuestionInputData>(key: K, value: QuestionInputData[K]) =>
    setInput((current) => ({ ...current, [key]: value }));
  const save = () => {
    setShowIssues(true);
    if (issues.length > 0) return;
    const onSuccess = (saved: Question) => {
      toast({ tone: 'success', title: questionId ? t('savedNewVersion') : t('created') });
      onSaved?.(saved);
    };
    if (questionId) update.mutate({ id: questionId, input }, { onSuccess });
    else create.mutate(input, { onSuccess });
  };
  if (questionId && existing.isLoading) return <SkeletonList label={tCommon('loading')} rows={4} />;

  const body = (
    <div className="flex flex-col gap-5">
      {variant === 'card' ? (
        <div className="flex flex-wrap items-center gap-3">
          {number ? (
            <span className="flex size-9 items-center justify-center rounded-full bg-primary font-heading text-base font-bold text-primary-foreground">
              {number}
            </span>
          ) : null}
          <h2 className="flex-1 text-xl">{questionId ? t('editTitle') : t('createTitle')}</h2>
          <label className="flex items-center gap-2 rounded-full bg-primary-soft py-1 pl-3.5 pr-1 text-label-md text-primary">
            {t('weight')}
            <Input
              type="number"
              min={0}
              step="0.5"
              className="h-8 w-16 rounded-full border-0 px-2 text-center"
              value={input.defaultScore}
              onChange={(event) => set('defaultScore', Number(event.target.value))}
            />
            <span className="pr-2">{t('pointsUnit')}</span>
          </label>
        </div>
      ) : null}
      {showIssues && issues.length > 0 ? (
        <Alert tone="danger" title={t('fixIssues')}>
          <ul className="list-disc pl-4">
            {issues.map((issue) => (
              <li key={issue}>{t(`issues.${issue}`)}</li>
            ))}
          </ul>
        </Alert>
      ) : null}
      <TypePills
        value={input.type}
        disabled={!!questionId}
        onChange={(type) =>
          setInput((current) => ({ ...current, type, data: defaultQuestionData(type) }))
        }
      />
      <Field label={t('title')} required>
        <Input value={input.title} onChange={(event) => set('title', event.target.value)} />
      </Field>
      <div className="flex flex-col gap-1.5">
        <span className="text-sm font-medium">{t('body')}</span>
        <BlockEditor
          value={input.body}
          onChange={(value) => set('body', value)}
          label={t('body')}
          kinds={COMPACT_BLOCK_KINDS}
        />
      </div>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Field label={t('category')}>
          <NativeSelect
            value={input.categoryId ?? ''}
            onChange={(event) => set('categoryId', event.target.value || null)}
          >
            <option value="">{t('noCategory')}</option>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </NativeSelect>
        </Field>
        {variant === 'card' ? null : (
          <Field label={t('defaultScore')}>
            <Input
              type="number"
              min={0}
              step="0.5"
              value={input.defaultScore}
              onChange={(event) => set('defaultScore', Number(event.target.value))}
            />
          </Field>
        )}
        <Field label={t('tags')} hint={t('tagsHint')}>
          <Input
            value={input.tags.join(', ')}
            onChange={(event) =>
              set(
                'tags',
                event.target.value
                  .split(',')
                  .map((tag) => tag.trim())
                  .filter(Boolean),
              )
            }
          />
        </Field>
      </div>
      <fieldset className="flex flex-col gap-3 rounded-md bg-surface-muted p-4 md:p-5">
        <legend className="float-left mb-1 flex items-center gap-2 font-heading text-base font-semibold">
          <ListChecks className="size-5 text-primary" aria-hidden /> {t('answerKey')}
        </legend>
        <QuestionDataEditor value={input.data} onChange={(data) => set('data', data)} />
      </fieldset>
      {questionId ? <PreviewCheck questionId={questionId} input={input} /> : null}
      {versions.data && versions.data.length > 0 ? (
        <details className="text-sm">
          <summary className="cursor-pointer font-medium">
            {t('versions', { count: versions.data.length })}
          </summary>
          <ul className="mt-2 flex flex-col gap-1 text-text-muted">
            {versions.data.map((version) => (
              <li key={version.id}>
                v{version.version} · {formatDateTime(version.createdAt, locale)}
              </li>
            ))}
          </ul>
        </details>
      ) : null}
      <div className="flex justify-end gap-2">
        {onCancel ? (
          <Button variant="ghost" onClick={onCancel}>
            {tCommon('cancel')}
          </Button>
        ) : null}
        <Button onClick={save} loading={create.isPending || update.isPending}>
          <Save aria-hidden /> {questionId ? t('applyChanges') : t('createQuestion')}
        </Button>
      </div>
    </div>
  );
  return variant === 'card' ? (
    <section className="rounded-lg border border-card-border bg-surface p-5 shadow-sm md:p-7">
      {body}
    </section>
  ) : (
    body
  );
}

/** Sheet wrapper of {@link QuestionForm} (question bank on narrow screens, create flow). */
export function QuestionEditor({
  courseId,
  questionId,
  open,
  onOpenChange,
  categories,
  defaultCategoryId,
  onSaved,
}: Props) {
  const t = useTranslations('qbank');
  const tCommon = useTranslations('common');
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        title={questionId ? t('editTitle') : t('createTitle')}
        description={questionId ? t('versionHint') : undefined}
        closeLabel={tCommon('close')}
        className="md:w-[44rem]"
      >
        {open ? (
          <QuestionForm
            courseId={courseId}
            questionId={questionId}
            categories={categories}
            defaultCategoryId={defaultCategoryId}
            onSaved={(question) => {
              onSaved?.(question);
              onOpenChange(false);
            }}
          />
        ) : null}
      </SheetContent>
    </Sheet>
  );
}
