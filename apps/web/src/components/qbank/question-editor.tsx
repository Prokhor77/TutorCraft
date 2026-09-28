'use client';
import { CheckCircle2, XCircle } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useEffect, useState } from 'react';
import { BlockEditor } from '@/components/editor/block-editor';
import { COMPACT_BLOCK_KINDS } from '@/components/editor/block-kinds';
import { QuestionInput } from '@/components/quiz/question-input';
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

/** Question editor (all 8 types), versions (FR-QBANK-05) and preview-check (FR-QBANK-06). */
export function QuestionEditor({
  courseId,
  questionId,
  open,
  onOpenChange,
  categories,
  defaultCategoryId,
}: Props) {
  const t = useTranslations('qbank');
  const tCommon = useTranslations('common');
  const locale = useLocale();
  const existing = useQuestion(open ? questionId : null);
  const versions = useQuestionVersions(open ? questionId : null);
  const { create, update } = useQbankMutations(courseId);
  const [input, setInput] = useState<QuestionInputData>(() =>
    emptyQuestion('single_choice', defaultCategoryId),
  );
  const [showIssues, setShowIssues] = useState(false);

  useEffect(() => {
    if (!open) return;
    setShowIssues(false);
    if (!questionId) return setInput(emptyQuestion('single_choice', defaultCategoryId));
    if (existing.data) {
      const { id: _id, version: _version, versionId: _versionId, ...rest } = existing.data;
      setInput(rest);
    }
  }, [open, questionId, existing.data, defaultCategoryId]);

  const issues = validateQuestion(input);
  const set = <K extends keyof QuestionInputData>(key: K, value: QuestionInputData[K]) =>
    setInput((current) => ({ ...current, [key]: value }));
  const save = () => {
    setShowIssues(true);
    if (issues.length > 0) return;
    const onSuccess = () => {
      toast({ tone: 'success', title: questionId ? t('savedNewVersion') : t('created') });
      onOpenChange(false);
    };
    if (questionId) update.mutate({ id: questionId, input }, { onSuccess });
    else create.mutate(input, { onSuccess });
  };

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        title={questionId ? t('editTitle') : t('createTitle')}
        description={questionId ? t('versionHint') : undefined}
        closeLabel={tCommon('close')}
        className="md:w-[44rem]"
      >
        <div className="flex flex-col gap-4">
          {showIssues && issues.length > 0 ? (
            <Alert tone="danger" title={t('fixIssues')}>
              <ul className="list-disc pl-4">
                {issues.map((issue) => (
                  <li key={issue}>{t(`issues.${issue}`)}</li>
                ))}
              </ul>
            </Alert>
          ) : null}
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field label={t('type')}>
              <NativeSelect
                value={input.type}
                disabled={!!questionId}
                onChange={(event) =>
                  setInput((current) => ({
                    ...current,
                    type: event.target.value as QuestionType,
                    data: defaultQuestionData(event.target.value as QuestionType),
                  }))
                }
              >
                {QUESTION_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {t(`types.${type}`)}
                  </option>
                ))}
              </NativeSelect>
            </Field>
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
          </div>
          <Field label={t('title')} required>
            <Input value={input.title} onChange={(event) => set('title', event.target.value)} />
          </Field>
          <div className="flex flex-col gap-1.5">
            <span className="text-sm font-medium">{t('body')}</span>
            <BlockEditor
              value={input.body}
              onChange={(body) => set('body', body)}
              label={t('body')}
              kinds={COMPACT_BLOCK_KINDS}
            />
          </div>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field label={t('defaultScore')}>
              <Input
                type="number"
                min={0}
                step="0.5"
                value={input.defaultScore}
                onChange={(event) => set('defaultScore', Number(event.target.value))}
              />
            </Field>
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
          <fieldset className="flex flex-col gap-2 rounded-md border border-border p-4">
            <legend className="px-1 text-sm font-medium">{t('answerKey')}</legend>
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
          <Button onClick={save} loading={create.isPending || update.isPending}>
            {tCommon('save')}
          </Button>
        </div>
      </SheetContent>
    </Sheet>
  );
}
