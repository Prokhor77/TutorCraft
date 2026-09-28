'use client';
import {
  AlignLeft,
  ArrowLeftRight,
  CircleDot,
  Hash,
  ListChecks,
  ListOrdered,
  TextCursorInput,
  ToggleLeft,
  type LucideIcon,
} from 'lucide-react';
import { useTranslations } from 'next-intl';
import type { QuestionType } from '@/lib/api/schemas/quiz';
import { cn } from '@/lib/utils/cn';

export const QUESTION_TYPE_ICONS: Record<QuestionType, LucideIcon> = {
  single_choice: CircleDot,
  multiple_choice: ListChecks,
  true_false: ToggleLeft,
  short_answer: TextCursorInput,
  numerical: Hash,
  essay: AlignLeft,
  matching: ArrowLeftRight,
  ordering: ListOrdered,
};

/** Question type icon + label («Один выбор», «Развёрнутый ответ»…) for Stitch question cards. */
export function QuestionTypeTag({ type, className }: { type: QuestionType; className?: string }) {
  const t = useTranslations('qbank');
  const Icon = QUESTION_TYPE_ICONS[type];
  return (
    <span
      className={cn(
        'inline-flex items-center gap-2 text-label-md uppercase text-text-muted',
        className,
      )}
    >
      <span className="flex size-7 items-center justify-center rounded-full bg-accent/10 text-primary">
        <Icon className="size-3.5" aria-hidden />
      </span>
      {t(`types.${type}`)}
    </span>
  );
}

/** Points indicator pill. */
export function PointsPill({ points, className }: { points: number; className?: string }) {
  const t = useTranslations('quiz');
  return (
    <span
      className={cn(
        'inline-flex h-7 items-center rounded-full bg-warning-soft px-3 text-label-md text-warning',
        className,
      )}
    >
      {t('points', { points })}
    </span>
  );
}
