'use client';
import { Plus, X } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useEffect, useState } from 'react';
import { BlockEditor } from '@/components/editor/block-editor';
import { COMPACT_BLOCK_KINDS } from '@/components/editor/block-kinds';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Field } from '@/components/ui/field';
import { Input, Textarea } from '@/components/ui/input';
import { Kbd } from '@/components/ui/kbd';
import { loadQuickComments, saveQuickComments } from '@/features/assessment/quick-comments';
import type { BlockDoc } from '@/lib/api/schemas/blockdoc';
import { createParagraph, emptyDoc } from '@/lib/blockdoc/doc';

export type GradeDraft = {
  score: string;
  feedback: BlockDoc;
  comment: string;
  returnForRevision: boolean;
};

export function emptyGradeDraft(): GradeDraft {
  return { score: '', feedback: emptyDoc(), comment: '', returnForRevision: false };
}

type Props = {
  kind: 'submission' | 'essay';
  maxScore: number | null;
  draft: GradeDraft;
  onChange: (draft: GradeDraft) => void;
  onSave: () => void;
  saving: boolean;
};

/** Right panel of the grading screen: score, feedback, quick comments, return for revision. */
export function GradePanel({ kind, maxScore, draft, onChange, onSave, saving }: Props) {
  const t = useTranslations('grading');
  const [comments, setComments] = useState<string[]>([]);
  const [newComment, setNewComment] = useState('');
  useEffect(
    () =>
      setComments(
        loadQuickComments([
          t('defaults.good'),
          t('defaults.checkFormatting'),
          t('defaults.seeComments'),
        ]),
      ),
    [t],
  );
  const scoreNumber = draft.score === '' ? null : Number(draft.score);
  const outOfRange =
    scoreNumber !== null && maxScore !== null && (scoreNumber < 0 || scoreNumber > maxScore);

  const appendComment = (text: string) => {
    if (kind === 'essay')
      return onChange({ ...draft, comment: draft.comment ? `${draft.comment}\n${text}` : text });
    onChange({
      ...draft,
      feedback: { ...draft.feedback, blocks: [...draft.feedback.blocks, createParagraph(text)] },
    });
  };
  const persistComments = (next: string[]) => {
    setComments(next);
    saveQuickComments(next);
  };

  return (
    <form
      className="flex flex-col gap-4"
      onSubmit={(event) => {
        event.preventDefault();
        if (!outOfRange) onSave();
      }}
    >
      <Field
        label={maxScore !== null ? t('scoreOutOf', { max: maxScore }) : t('score')}
        error={outOfRange ? t('scoreRange', { max: maxScore ?? 0 }) : undefined}
      >
        <Input
          type="number"
          inputMode="decimal"
          step="0.5"
          min={0}
          max={maxScore ?? undefined}
          value={draft.score}
          onChange={(event) => onChange({ ...draft, score: event.target.value })}
          className="h-12 text-lg"
          autoFocus
        />
      </Field>
      {kind === 'submission' ? (
        <div className="flex flex-col gap-1.5">
          <span className="text-sm font-medium">{t('feedback')}</span>
          <BlockEditor
            value={draft.feedback}
            onChange={(feedback) => onChange({ ...draft, feedback })}
            label={t('feedback')}
            kinds={COMPACT_BLOCK_KINDS}
          />
        </div>
      ) : (
        <Field label={t('comment')}>
          <Textarea
            value={draft.comment}
            onChange={(event) => onChange({ ...draft, comment: event.target.value })}
          />
        </Field>
      )}
      <div className="flex flex-col gap-2">
        <span className="text-sm font-medium">{t('quickComments')}</span>
        <div className="flex flex-wrap gap-1.5">
          {comments.map((comment) => (
            <span
              key={comment}
              className="inline-flex items-center rounded-full border border-border bg-surface text-xs"
            >
              <button
                type="button"
                className="px-2.5 py-1 hover:text-primary"
                onClick={() => appendComment(comment)}
              >
                {comment}
              </button>
              <button
                type="button"
                aria-label={t('removeComment', { comment })}
                className="pr-2 text-text-muted hover:text-danger"
                onClick={() => persistComments(comments.filter((entry) => entry !== comment))}
              >
                <X className="size-3" aria-hidden />
              </button>
            </span>
          ))}
        </div>
        <div className="flex gap-1">
          <Input
            aria-label={t('newComment')}
            placeholder={t('newComment')}
            value={newComment}
            onChange={(event) => setNewComment(event.target.value)}
            className="h-8 text-xs"
          />
          <Button
            type="button"
            size="icon-sm"
            variant="secondary"
            aria-label={t('addComment')}
            onClick={() => {
              if (!newComment.trim()) return;
              persistComments([...comments, newComment.trim()]);
              setNewComment('');
            }}
          >
            <Plus aria-hidden />
          </Button>
        </div>
      </div>
      {kind === 'submission' ? (
        <label className="flex items-center gap-2 text-sm">
          <Checkbox
            checked={draft.returnForRevision}
            onCheckedChange={(checked) =>
              onChange({ ...draft, returnForRevision: checked === true })
            }
          />
          {t('returnForRevision')}
        </label>
      ) : null}
      <Button type="submit" size="lg" loading={saving} disabled={outOfRange}>
        {t('saveNext')}{' '}
        <Kbd className="ml-1 bg-primary-foreground/20 text-primary-foreground">Ctrl+↵</Kbd>
      </Button>
    </form>
  );
}
