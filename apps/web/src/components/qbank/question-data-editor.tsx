'use client';
import { ArrowDown, ArrowUp, Plus, Trash2 } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { Button } from '@/components/ui/button';
import { Checkbox, Radio, Switch } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/field';
import { MathTextInput } from '@/components/math/math-text-input';
import { Input, NativeSelect } from '@/components/ui/input';
import { FULL_SCORE_PERCENT } from '@/features/qbank/question-data';
import { MC_SCORING, type QuestionData } from '@/lib/api/schemas/quiz';
import { localId } from '@/lib/utils/ids';

type Props = { value: QuestionData; onChange: (value: QuestionData) => void };

function swap<T>(list: T[], index: number, delta: -1 | 1): T[] {
  const target = index + delta;
  if (target < 0 || target >= list.length) return list;
  const next = [...list];
  [next[index], next[target]] = [next[target] as T, next[index] as T];
  return next;
}

function ShuffleToggle({
  checked,
  onChange,
}: {
  checked: boolean;
  onChange: (value: boolean) => void;
}) {
  const t = useTranslations('qbank');
  return (
    <div className="flex items-center gap-2">
      <Switch id="shuffle" checked={checked} onCheckedChange={onChange} />
      <Label htmlFor="shuffle">{t('shuffle')}</Label>
    </div>
  );
}

/** Type-specific answer key editors for the 8 MVP question types (FR-QBANK-03). */
export function QuestionDataEditor({ value, onChange }: Props) {
  const t = useTranslations('qbank');
  switch (value.type) {
    case 'single_choice':
    case 'multiple_choice': {
      const setOption = (index: number, patch: Partial<(typeof value.options)[number]>) =>
        onChange({
          ...value,
          options: value.options.map((option, i) => {
            if (i === index) return { ...option, ...patch };
            if (value.type === 'single_choice' && patch.correct)
              return { ...option, correct: false };
            return option;
          }),
        });
      return (
        <div className="flex flex-col gap-3">
          <ul className="flex flex-col gap-2">
            {value.options.map((option, index) => (
              <li
                key={option.id}
                className="flex flex-col gap-2 rounded-md border-[1.5px] border-border p-3 sm:flex-row sm:items-center"
              >
                <label className="flex items-center gap-2 text-xs">
                  {value.type === 'single_choice' ? (
                    <Radio
                      name="correct-option"
                      checked={option.correct}
                      onChange={() => setOption(index, { correct: true })}
                    />
                  ) : (
                    <Checkbox
                      checked={option.correct}
                      onCheckedChange={(checked) => setOption(index, { correct: checked === true })}
                    />
                  )}
                  {t('correct')}
                </label>
                <MathTextInput
                  ariaLabel={t('optionText', { index: index + 1 })}
                  value={option.text}
                  onChange={(text) => setOption(index, { text })}
                  className="sm:flex-1"
                />
                <MathTextInput
                  ariaLabel={t('optionFeedback', { index: index + 1 })}
                  placeholder={t('feedbackOptional')}
                  value={option.feedback ?? ''}
                  onChange={(feedback) => setOption(index, { feedback: feedback || undefined })}
                  className="sm:w-56"
                />
                <Button
                  variant="ghost"
                  size="icon-sm"
                  disabled={value.options.length <= 2}
                  aria-label={t('removeOption')}
                  onClick={() =>
                    onChange({ ...value, options: value.options.filter((_, i) => i !== index) })
                  }
                >
                  <Trash2 aria-hidden />
                </Button>
              </li>
            ))}
          </ul>
          <div className="flex flex-wrap items-center gap-4">
            <Button
              variant="secondary"
              size="sm"
              onClick={() =>
                onChange({
                  ...value,
                  options: [...value.options, { id: localId('o'), text: '', correct: false }],
                })
              }
            >
              <Plus aria-hidden /> {t('addOption')}
            </Button>
            <ShuffleToggle
              checked={value.shuffle}
              onChange={(shuffle) => onChange({ ...value, shuffle })}
            />
            {value.type === 'multiple_choice' ? (
              <NativeSelect
                aria-label={t('scoring')}
                value={value.scoring}
                onChange={(event) =>
                  onChange({ ...value, scoring: event.target.value as (typeof MC_SCORING)[number] })
                }
                className="w-auto"
              >
                {MC_SCORING.map((scoring) => (
                  <option key={scoring} value={scoring}>
                    {t(`scorings.${scoring}`)}
                  </option>
                ))}
              </NativeSelect>
            ) : null}
          </div>
        </div>
      );
    }
    case 'true_false':
      return (
        <fieldset className="flex gap-4">
          <legend className="mb-2 text-sm font-medium">{t('correctAnswer')}</legend>
          {[true, false].map((option) => (
            <label key={String(option)} className="flex items-center gap-2">
              <Radio
                name="tf"
                checked={value.correct === option}
                onChange={() => onChange({ ...value, correct: option })}
              />
              {option ? t('true') : t('false')}
            </label>
          ))}
        </fieldset>
      );
    case 'short_answer':
      return (
        <div className="flex flex-col gap-3">
          <p className="text-xs text-text-muted">{t('wildcardHint')}</p>
          {value.answers.map((answer, index) => (
            <div key={index} className="flex items-center gap-2">
              <Input
                aria-label={t('pattern', { index: index + 1 })}
                value={answer.pattern}
                onChange={(event) =>
                  onChange({
                    ...value,
                    answers: value.answers.map((a, i) =>
                      i === index ? { ...a, pattern: event.target.value } : a,
                    ),
                  })
                }
                className="flex-1"
              />
              <Input
                aria-label={t('scorePercent')}
                type="number"
                min={0}
                max={FULL_SCORE_PERCENT}
                value={answer.scorePercent}
                onChange={(event) =>
                  onChange({
                    ...value,
                    answers: value.answers.map((a, i) =>
                      i === index ? { ...a, scorePercent: Number(event.target.value) } : a,
                    ),
                  })
                }
                className="w-24"
              />
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label={t('remove')}
                onClick={() =>
                  onChange({ ...value, answers: value.answers.filter((_, i) => i !== index) })
                }
              >
                <Trash2 aria-hidden />
              </Button>
            </div>
          ))}
          <div className="flex flex-wrap items-center gap-4">
            <Button
              variant="secondary"
              size="sm"
              onClick={() =>
                onChange({
                  ...value,
                  answers: [...value.answers, { pattern: '', scorePercent: FULL_SCORE_PERCENT }],
                })
              }
            >
              <Plus aria-hidden /> {t('addAnswer')}
            </Button>
            <div className="flex items-center gap-2">
              <Switch
                id="case-sensitive"
                checked={value.caseSensitive}
                onCheckedChange={(caseSensitive) => onChange({ ...value, caseSensitive })}
              />
              <Label htmlFor="case-sensitive">{t('caseSensitive')}</Label>
            </div>
          </div>
        </div>
      );
    case 'numerical':
      return (
        <div className="flex flex-col gap-3">
          {value.answers.map((answer, index) => (
            <div key={index} className="flex flex-wrap items-center gap-2">
              <Input
                aria-label={t('value')}
                type="number"
                value={answer.value}
                onChange={(event) =>
                  onChange({
                    ...value,
                    answers: value.answers.map((a, i) =>
                      i === index ? { ...a, value: Number(event.target.value) } : a,
                    ),
                  })
                }
                className="w-32"
              />
              <span className="text-sm">±</span>
              <Input
                aria-label={t('tolerance')}
                type="number"
                min={0}
                value={answer.tolerance}
                onChange={(event) =>
                  onChange({
                    ...value,
                    answers: value.answers.map((a, i) =>
                      i === index ? { ...a, tolerance: Number(event.target.value) } : a,
                    ),
                  })
                }
                className="w-24"
              />
              <Input
                aria-label={t('scorePercent')}
                type="number"
                min={0}
                max={FULL_SCORE_PERCENT}
                value={answer.scorePercent}
                onChange={(event) =>
                  onChange({
                    ...value,
                    answers: value.answers.map((a, i) =>
                      i === index ? { ...a, scorePercent: Number(event.target.value) } : a,
                    ),
                  })
                }
                className="w-24"
              />
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label={t('remove')}
                onClick={() =>
                  onChange({ ...value, answers: value.answers.filter((_, i) => i !== index) })
                }
              >
                <Trash2 aria-hidden />
              </Button>
            </div>
          ))}
          <Button
            variant="secondary"
            size="sm"
            className="self-start"
            onClick={() =>
              onChange({
                ...value,
                answers: [
                  ...value.answers,
                  { value: 0, tolerance: 0, scorePercent: FULL_SCORE_PERCENT },
                ],
              })
            }
          >
            <Plus aria-hidden /> {t('addAnswer')}
          </Button>
        </div>
      );
    case 'essay':
      return (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          <label className="flex flex-col gap-1 text-sm">
            {t('responseFormat')}
            <NativeSelect
              value={value.responseFormat}
              onChange={(event) =>
                onChange({
                  ...value,
                  responseFormat: event.target.value as 'text' | 'text_and_files',
                })
              }
            >
              <option value="text">{t('formats.text')}</option>
              <option value="text_and_files">{t('formats.text_and_files')}</option>
            </NativeSelect>
          </label>
          <label className="flex flex-col gap-1 text-sm">
            {t('minWords')}
            <Input
              type="number"
              min={0}
              value={value.minWords ?? ''}
              onChange={(event) =>
                onChange({
                  ...value,
                  minWords: event.target.value ? Number(event.target.value) : undefined,
                })
              }
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            {t('maxWords')}
            <Input
              type="number"
              min={0}
              value={value.maxWords ?? ''}
              onChange={(event) =>
                onChange({
                  ...value,
                  maxWords: event.target.value ? Number(event.target.value) : undefined,
                })
              }
            />
          </label>
        </div>
      );
    case 'matching':
      return (
        <div className="flex flex-col gap-3">
          {value.pairs.map((pair, index) => (
            <div key={pair.id} className="flex items-center gap-2">
              <MathTextInput
                ariaLabel={t('prompt', { index: index + 1 })}
                placeholder={t('promptPlaceholder')}
                value={pair.prompt}
                onChange={(prompt) =>
                  onChange({
                    ...value,
                    pairs: value.pairs.map((p, i) => (i === index ? { ...p, prompt } : p)),
                  })
                }
                className="flex-1"
              />
              <span aria-hidden>→</span>
              <MathTextInput
                ariaLabel={t('answerFor', { index: index + 1 })}
                placeholder={t('answerPlaceholder')}
                value={pair.answer}
                onChange={(answer) =>
                  onChange({
                    ...value,
                    pairs: value.pairs.map((p, i) => (i === index ? { ...p, answer } : p)),
                  })
                }
                className="flex-1"
              />
              <Button
                variant="ghost"
                size="icon-sm"
                disabled={value.pairs.length <= 2}
                aria-label={t('remove')}
                onClick={() =>
                  onChange({ ...value, pairs: value.pairs.filter((_, i) => i !== index) })
                }
              >
                <Trash2 aria-hidden />
              </Button>
            </div>
          ))}
          <div className="flex items-center gap-4">
            <Button
              variant="secondary"
              size="sm"
              onClick={() =>
                onChange({
                  ...value,
                  pairs: [...value.pairs, { id: localId('p'), prompt: '', answer: '' }],
                })
              }
            >
              <Plus aria-hidden /> {t('addPair')}
            </Button>
            <ShuffleToggle
              checked={value.shuffle}
              onChange={(shuffle) => onChange({ ...value, shuffle })}
            />
          </div>
        </div>
      );
    case 'ordering':
      return (
        <div className="flex flex-col gap-3">
          <p className="text-xs text-text-muted">{t('orderingHint')}</p>
          {value.items.map((item, index) => (
            <div key={item.id} className="flex items-center gap-2">
              <span className="w-6 text-sm text-text-muted">{index + 1}.</span>
              <MathTextInput
                ariaLabel={t('itemText', { index: index + 1 })}
                value={item.text}
                onChange={(text) =>
                  onChange({
                    ...value,
                    items: value.items.map((it, i) => (i === index ? { ...it, text } : it)),
                  })
                }
                className="flex-1"
              />
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label={t('moveUp')}
                disabled={index === 0}
                onClick={() => onChange({ ...value, items: swap(value.items, index, -1) })}
              >
                <ArrowUp aria-hidden />
              </Button>
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label={t('moveDown')}
                disabled={index === value.items.length - 1}
                onClick={() => onChange({ ...value, items: swap(value.items, index, 1) })}
              >
                <ArrowDown aria-hidden />
              </Button>
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label={t('remove')}
                disabled={value.items.length <= 2}
                onClick={() =>
                  onChange({ ...value, items: value.items.filter((_, i) => i !== index) })
                }
              >
                <Trash2 aria-hidden />
              </Button>
            </div>
          ))}
          <Button
            variant="secondary"
            size="sm"
            className="self-start"
            onClick={() =>
              onChange({ ...value, items: [...value.items, { id: localId('i'), text: '' }] })
            }
          >
            <Plus aria-hidden /> {t('addItem')}
          </Button>
        </div>
      );
  }
}
