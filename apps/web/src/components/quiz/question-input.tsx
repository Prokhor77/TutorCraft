'use client';
import { ArrowDown, ArrowUp } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Input, NativeSelect, Textarea } from '@/components/ui/input';
import {
  currentOrder,
  essayResponse,
  essayText,
  moveInOrder,
  setMatch,
  toggleOptionId,
} from '@/features/quiz/responses';
import type { QuestionResponse, StudentQuestionView } from '@/lib/api/schemas/quiz';
import { cn } from '@/lib/utils/cn';

type Props = {
  question: StudentQuestionView;
  response: QuestionResponse | null;
  /** debounce=true for typing (text/number/essay), false for discrete choices. */
  onChange: (response: QuestionResponse, debounce: boolean) => void;
  disabled?: boolean;
};

const choiceClass =
  'flex min-h-12 cursor-pointer items-center gap-3 rounded-lg border border-border bg-surface px-4 py-3 text-base transition-colors hover:border-primary/60 has-[:checked]:border-primary has-[:checked]:bg-primary-soft has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-focus-ring';

/** Large, single-column answer areas per question type (SPEC §10 «Прохождение теста»). */
export function QuestionInput({ question, response, onChange, disabled }: Props) {
  const t = useTranslations('quiz');
  const name = `q-${question.slot}`;
  const [numberDraft, setNumberDraft] = useState(
    response && 'number' in response ? String(response.number) : '',
  );

  switch (question.type) {
    case 'single_choice':
      return (
        <fieldset className="flex flex-col gap-2" disabled={disabled}>
          <legend className="sr-only">{question.title}</legend>
          {(question.options ?? []).map((option) => (
            <label key={option.id} className={choiceClass}>
              <input
                type="radio"
                name={name}
                className="size-5 accent-[rgb(var(--primary))]"
                checked={!!response && 'optionId' in response && response.optionId === option.id}
                onChange={() => onChange({ optionId: option.id }, false)}
              />
              {option.text}
            </label>
          ))}
        </fieldset>
      );
    case 'multiple_choice':
      return (
        <fieldset className="flex flex-col gap-2" disabled={disabled}>
          <legend className="mb-1 text-sm text-text-muted">{t('chooseAll')}</legend>
          {(question.options ?? []).map((option) => {
            const checked =
              !!response && 'optionIds' in response && response.optionIds.includes(option.id);
            return (
              <label
                key={option.id}
                className={cn(choiceClass, checked && 'border-primary bg-primary-soft')}
              >
                <Checkbox
                  checked={checked}
                  onCheckedChange={() => onChange(toggleOptionId(response, option.id), false)}
                />
                {option.text}
              </label>
            );
          })}
        </fieldset>
      );
    case 'true_false':
      return (
        <fieldset className="grid grid-cols-2 gap-2" disabled={disabled}>
          <legend className="sr-only">{question.title}</legend>
          {[true, false].map((value) => (
            <label key={String(value)} className={cn(choiceClass, 'justify-center')}>
              <input
                type="radio"
                name={name}
                className="size-5 accent-[rgb(var(--primary))]"
                checked={!!response && 'value' in response && response.value === value}
                onChange={() => onChange({ value }, false)}
              />
              {value ? t('true') : t('false')}
            </label>
          ))}
        </fieldset>
      );
    case 'short_answer':
      return (
        <Input
          aria-label={t('yourAnswer')}
          className="h-12 text-base"
          disabled={disabled}
          defaultValue={response && 'text' in response ? response.text : ''}
          onChange={(event) => onChange({ text: event.target.value }, true)}
        />
      );
    case 'numerical':
      return (
        <Input
          aria-label={t('yourAnswer')}
          className="h-12 text-base"
          inputMode="decimal"
          disabled={disabled}
          value={numberDraft}
          onChange={(event) => {
            setNumberDraft(event.target.value);
            const parsed = Number(event.target.value.replace(',', '.'));
            if (event.target.value.trim() !== '' && Number.isFinite(parsed))
              onChange({ number: parsed }, true);
          }}
        />
      );
    case 'essay':
      return (
        <Textarea
          aria-label={t('yourAnswer')}
          className="min-h-48 text-base"
          disabled={disabled}
          defaultValue={essayText(response)}
          onChange={(event) => onChange(essayResponse(event.target.value), true)}
        />
      );
    case 'matching':
      return (
        <div className="flex flex-col gap-3">
          {(question.prompts ?? []).map((prompt) => (
            <label
              key={prompt.id}
              className="flex flex-col gap-1.5 sm:flex-row sm:items-center sm:gap-4"
            >
              <span className="flex-1 text-base">{prompt.text}</span>
              <NativeSelect
                className="h-12 sm:w-64"
                disabled={disabled}
                value={response && 'matches' in response ? (response.matches[prompt.id] ?? '') : ''}
                onChange={(event) =>
                  onChange(setMatch(response, prompt.id, event.target.value), false)
                }
              >
                <option value="">{t('choose')}</option>
                {(question.answerChoices ?? []).map((choice) => (
                  <option key={choice} value={choice}>
                    {choice}
                  </option>
                ))}
              </NativeSelect>
            </label>
          ))}
        </div>
      );
    case 'ordering': {
      const order = currentOrder(question);
      const byId = new Map((question.items ?? []).map((item) => [item.id, item.text]));
      const effective = response && 'order' in response ? response.order : order;
      return (
        <ol className="flex flex-col gap-2" aria-label={t('orderHint')}>
          {effective.map((id, index) => (
            <li
              key={id}
              className="flex items-center gap-2 rounded-lg border border-border bg-surface px-3 py-2"
            >
              <span className="w-6 text-sm font-semibold text-text-muted">{index + 1}.</span>
              <span className="flex-1 text-base">{byId.get(id)}</span>
              <Button
                variant="ghost"
                size="icon-sm"
                disabled={disabled || index === 0}
                aria-label={t('moveUp', { text: byId.get(id) ?? '' })}
                onClick={() => onChange({ order: moveInOrder(effective, id, -1) }, false)}
              >
                <ArrowUp aria-hidden />
              </Button>
              <Button
                variant="ghost"
                size="icon-sm"
                disabled={disabled || index === effective.length - 1}
                aria-label={t('moveDown', { text: byId.get(id) ?? '' })}
                onClick={() => onChange({ order: moveInOrder(effective, id, 1) }, false)}
              >
                <ArrowDown aria-hidden />
              </Button>
            </li>
          ))}
        </ol>
      );
    }
  }
}
