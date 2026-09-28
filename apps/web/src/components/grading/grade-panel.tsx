'use client';
import { MessageSquareText, Plus, RotateCcw, Star, X } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useEffect, useState } from 'react';
import { BlockEditor } from '@/components/editor/block-editor';
import { COMPACT_BLOCK_KINDS } from '@/components/editor/block-kinds';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { Input, Textarea } from '@/components/ui/input';
import { Kbd } from '@/components/ui/kbd';
import { Panel } from '@/components/ui/page-header';
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
  /** Saves the draft (with optional overrides, e.g. `returnForRevision`) and moves to the next entry. */
  onSave: (overrides?: Partial<GradeDraft>) => void;
  saving: boolean;
};

function PanelTitle({ icon: Icon, children }: { icon: typeof Star; children: React.ReactNode }) {
  return (
    <span className="flex items-center gap-2">
      <span className="flex size-8 items-center justify-center rounded-full bg-primary-soft text-primary">
        <Icon className="size-4" aria-hidden />
      </span>
      {children}
    </span>
  );
}

/**
 * Grading composer (Stitch «Критерии оценивания» + «Педагогический комментарий»): a score panel with the live
 * «N / max» readout and a feedback panel with quick-comment chips, «Вернуть на доработку» and «Сохранить и далее».
 */
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
      className="flex flex-col gap-gutter"
      onSubmit={(event) => {
        event.preventDefault();
        if (!outOfRange) onSave();
      }}
    >
      <Panel
        title={<PanelTitle icon={Star}>{t('grade')}</PanelTitle>}
        actions={
          <p aria-hidden className="flex items-baseline gap-1 font-heading">
            <span className="text-3xl font-bold text-primary">{draft.score || '—'}</span>
            {maxScore !== null ? (
              <span className="text-base text-text-muted">/ {maxScore}</span>
            ) : null}
          </p>
        }
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
            className="h-14 rounded-md bg-surface-muted font-heading text-2xl font-semibold"
            autoFocus
          />
        </Field>
      </Panel>

      <Panel
        title={
          <PanelTitle icon={MessageSquareText}>
            {kind === 'submission' ? t('feedback') : t('comment')}
          </PanelTitle>
        }
      >
        {kind === 'submission' ? (
          <div className="rounded-md bg-surface-muted p-2 sm:p-3">
            <BlockEditor
              value={draft.feedback}
              onChange={(feedback) => onChange({ ...draft, feedback })}
              label={t('feedback')}
              kinds={COMPACT_BLOCK_KINDS}
            />
          </div>
        ) : (
          <Textarea
            aria-label={t('comment')}
            value={draft.comment}
            onChange={(event) => onChange({ ...draft, comment: event.target.value })}
            className="rounded-md bg-surface-muted"
          />
        )}
        <div className="flex flex-col gap-2">
          <span className="text-label-md uppercase text-text-muted">{t('quickComments')}</span>
          <div className="flex flex-wrap gap-1.5">
            {comments.map((comment) => (
              <span
                key={comment}
                className="inline-flex max-w-full items-center rounded-full bg-surface-muted text-xs"
              >
                <button
                  type="button"
                  className="truncate py-1.5 pl-3 pr-1.5 text-left hover:text-primary"
                  onClick={() => appendComment(comment)}
                >
                  {comment}
                </button>
                <button
                  type="button"
                  aria-label={t('removeComment', { comment })}
                  className="shrink-0 pr-2.5 text-text-muted hover:text-danger"
                  onClick={() => persistComments(comments.filter((entry) => entry !== comment))}
                >
                  <X className="size-3" aria-hidden />
                </button>
              </span>
            ))}
          </div>
          <div className="flex gap-1.5">
            <Input
              aria-label={t('newComment')}
              placeholder={t('newComment')}
              value={newComment}
              onChange={(event) => setNewComment(event.target.value)}
              className="h-9 rounded-full text-xs"
            />
            <Button
              type="button"
              size="icon"
              variant="secondary"
              className="size-9"
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
        <div className="flex flex-col-reverse gap-2 border-t border-border pt-4 sm:flex-row sm:items-center sm:justify-between">
          {kind === 'submission' ? (
            <Button
              type="button"
              variant="ghost"
              className="text-danger hover:bg-danger-soft hover:text-danger"
              disabled={outOfRange || saving}
              onClick={() => onSave({ returnForRevision: true })}
            >
              <RotateCcw aria-hidden /> {t('returnForRevision')}
            </Button>
          ) : (
            <span />
          )}
          <Button type="submit" size="lg" loading={saving} disabled={outOfRange}>
            {t('saveNext')}{' '}
            <Kbd className="ml-1 bg-primary-foreground/20 text-primary-foreground">Ctrl+↵</Kbd>
          </Button>
        </div>
      </Panel>
    </form>
  );
}
