'use client';
import { useTranslations } from 'next-intl';
import { Checkbox, Switch } from '@/components/ui/checkbox';
import { Field, Label } from '@/components/ui/field';
import { DateTimeInput, Input, NativeSelect } from '@/components/ui/input';
import {
  FORUM_TYPES,
  GRADING_METHODS,
  REVIEW_TIMINGS,
  SUBMISSION_TYPES,
  type AssignmentSettings,
  type ForumSettings,
  type QuizSettings,
} from '@/lib/api/schemas/courses';
import { fromDateTimeLocalValue, SECONDS_PER_MINUTE, toDateTimeLocalValue } from '@/lib/utils/time';

type FieldsProps<T> = { value: T; onChange: (value: T) => void };

function numberOrNull(raw: string): number | null {
  return raw === '' ? null : Number(raw);
}

function DateField({
  label,
  value,
  onChange,
  hint,
}: {
  label: string;
  value: string | null;
  onChange: (value: string | null) => void;
  hint?: string;
}) {
  return (
    <Field label={label} hint={hint}>
      <DateTimeInput
        value={toDateTimeLocalValue(value)}
        onChange={(event) => onChange(fromDateTimeLocalValue(event.target.value))}
      />
    </Field>
  );
}

function ToggleRow({
  id,
  label,
  checked,
  onChange,
}: {
  id: string;
  label: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
}) {
  return (
    <div className="flex items-center justify-between gap-3">
      <Label htmlFor={id} className="font-normal">
        {label}
      </Label>
      <Switch id={id} checked={checked} onCheckedChange={onChange} />
    </div>
  );
}

/** FR-ASSIGN-01/02 settings. */
export function AssignmentSettingsFields({ value, onChange }: FieldsProps<AssignmentSettings>) {
  const t = useTranslations('itemSettings');
  const set = <K extends keyof AssignmentSettings>(key: K, next: AssignmentSettings[K]) =>
    onChange({ ...value, [key]: next });
  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <DateField label={t('dueAt')} value={value.dueAt} onChange={(next) => set('dueAt', next)} />
        <Field label={t('maxScore')}>
          <Input
            type="number"
            min={0}
            value={value.maxScore}
            onChange={(event) => set('maxScore', Number(event.target.value))}
          />
        </Field>
        <Field label={t('submissionType')}>
          <NativeSelect
            value={value.submissionType}
            onChange={(event) =>
              set('submissionType', event.target.value as AssignmentSettings['submissionType'])
            }
          >
            {SUBMISSION_TYPES.map((option) => (
              <option key={option} value={option}>
                {t(`submissionTypes.${option}`)}
              </option>
            ))}
          </NativeSelect>
        </Field>
      </div>
      <details className="rounded-md bg-surface-muted p-4">
        <summary className="cursor-pointer text-sm font-medium">{t('advanced')}</summary>
        <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2">
          <DateField
            label={t('openAt')}
            value={value.openAt}
            onChange={(next) => set('openAt', next)}
          />
          <DateField
            label={t('closeAt')}
            value={value.closeAt}
            onChange={(next) => set('closeAt', next)}
            hint={t('closeAtHint')}
          />
          <Field label={t('allowedExtensions')} hint={t('allowedExtensionsHint')}>
            <Input
              value={value.allowedExtensions.join(', ')}
              onChange={(event) =>
                set(
                  'allowedExtensions',
                  event.target.value
                    .split(',')
                    .map((ext) => ext.trim())
                    .filter(Boolean),
                )
              }
            />
          </Field>
          <Field label={t('maxFiles')}>
            <Input
              type="number"
              min={1}
              value={value.maxFiles}
              onChange={(event) => set('maxFiles', Number(event.target.value))}
            />
          </Field>
          <Field label={t('maxFileSizeMb')}>
            <Input
              type="number"
              min={1}
              value={value.maxFileSizeMb}
              onChange={(event) => set('maxFileSizeMb', Number(event.target.value))}
            />
          </Field>
          <Field label={t('maxAttempts')} hint={t('unlimitedHint')}>
            <Input
              type="number"
              min={1}
              value={value.maxAttempts ?? ''}
              onChange={(event) => set('maxAttempts', numberOrNull(event.target.value))}
            />
          </Field>
          <div className="flex flex-col gap-3 sm:col-span-2">
            <ToggleRow
              id="group-submission"
              label={t('groupSubmission')}
              checked={value.groupSubmission}
              onChange={(next) => set('groupSubmission', next)}
            />
            <ToggleRow
              id="require-submit"
              label={t('requireSubmitButton')}
              checked={value.requireSubmitButton}
              onChange={(next) => set('requireSubmitButton', next)}
            />
            <ToggleRow
              id="auto-publish"
              label={t('autoPublishGrades')}
              checked={value.autoPublishGrades}
              onChange={(next) => set('autoPublishGrades', next)}
            />
          </div>
        </div>
      </details>
    </div>
  );
}

/** FR-QUIZ-01/05 settings. */
export function QuizSettingsFields({ value, onChange }: FieldsProps<QuizSettings>) {
  const t = useTranslations('itemSettings');
  const set = <K extends keyof QuizSettings>(key: K, next: QuizSettings[K]) =>
    onChange({ ...value, [key]: next });
  const reviewKeys = [
    'whenScore',
    'whenCorrectness',
    'whenCorrectAnswers',
    'whenFeedback',
  ] as const;
  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <DateField
          label={t('openAt')}
          value={value.openAt}
          onChange={(next) => set('openAt', next)}
        />
        <DateField
          label={t('closeAt')}
          value={value.closeAt}
          onChange={(next) => set('closeAt', next)}
        />
        <Field label={t('timeLimitMinutes')} hint={t('unlimitedHint')}>
          <Input
            type="number"
            min={1}
            value={value.timeLimitSec === null ? '' : value.timeLimitSec / SECONDS_PER_MINUTE}
            onChange={(event) =>
              set(
                'timeLimitSec',
                event.target.value ? Number(event.target.value) * SECONDS_PER_MINUTE : null,
              )
            }
          />
        </Field>
        <Field label={t('maxAttempts')} hint={t('unlimitedHint')}>
          <Input
            type="number"
            min={1}
            value={value.maxAttempts ?? ''}
            onChange={(event) => set('maxAttempts', numberOrNull(event.target.value))}
          />
        </Field>
        <Field label={t('gradingMethod')}>
          <NativeSelect
            value={value.gradingMethod}
            onChange={(event) =>
              set('gradingMethod', event.target.value as QuizSettings['gradingMethod'])
            }
          >
            {GRADING_METHODS.map((method) => (
              <option key={method} value={method}>
                {t(`gradingMethods.${method}`)}
              </option>
            ))}
          </NativeSelect>
        </Field>
        <Field label={t('passPercent')}>
          <Input
            type="number"
            min={0}
            max={100}
            value={value.passPercent ?? ''}
            onChange={(event) => set('passPercent', numberOrNull(event.target.value))}
          />
        </Field>
      </div>
      <details className="rounded-md bg-surface-muted p-4">
        <summary className="cursor-pointer text-sm font-medium">{t('advanced')}</summary>
        <div className="mt-4 flex flex-col gap-4">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field label={t('questionsPerPage')}>
              <Input
                type="number"
                min={0}
                value={value.questionsPerPage}
                onChange={(event) => set('questionsPerPage', Number(event.target.value))}
              />
            </Field>
            <Field label={t('maxScore')}>
              <Input
                type="number"
                min={0}
                value={value.maxScore}
                onChange={(event) => set('maxScore', Number(event.target.value))}
              />
            </Field>
          </div>
          <ToggleRow
            id="shuffle-q"
            label={t('shuffleQuestions')}
            checked={value.shuffleQuestions}
            onChange={(next) => set('shuffleQuestions', next)}
          />
          <ToggleRow
            id="shuffle-a"
            label={t('shuffleAnswers')}
            checked={value.shuffleAnswers}
            onChange={(next) => set('shuffleAnswers', next)}
          />
          <fieldset className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <legend className="mb-2 text-sm font-medium">{t('review')}</legend>
            {reviewKeys.map((key) => (
              <Field key={key} label={t(`reviewKeys.${key}`)}>
                <NativeSelect
                  value={value.review[key]}
                  onChange={(event) =>
                    set('review', {
                      ...value.review,
                      [key]: event.target.value as (typeof REVIEW_TIMINGS)[number],
                    })
                  }
                >
                  {REVIEW_TIMINGS.map((timing) => (
                    <option key={timing} value={timing}>
                      {t(`reviewTimings.${timing}`)}
                    </option>
                  ))}
                </NativeSelect>
              </Field>
            ))}
          </fieldset>
        </div>
      </details>
    </div>
  );
}

export function ForumSettingsFields({ value, onChange }: FieldsProps<ForumSettings>) {
  const t = useTranslations('itemSettings');
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
      <Field label={t('forumType')}>
        <NativeSelect
          value={value.forumType}
          onChange={(event) =>
            onChange({ ...value, forumType: event.target.value as ForumSettings['forumType'] })
          }
        >
          {FORUM_TYPES.map((type) => (
            <option key={type} value={type}>
              {t(`forumTypes.${type}`)}
            </option>
          ))}
        </NativeSelect>
      </Field>
      <Field label={t('editWindowMinutes')}>
        <Input
          type="number"
          min={0}
          value={value.editWindowMinutes}
          onChange={(event) =>
            onChange({ ...value, editWindowMinutes: Number(event.target.value) })
          }
        />
      </Field>
    </div>
  );
}

export function CompletionTriggers({
  value,
  onChange,
}: {
  value: string[];
  onChange: (value: string[]) => void;
}) {
  const t = useTranslations('itemSettings');
  const triggers = ['viewed', 'submitted', 'graded', 'passed', 'posted'] as const;
  return (
    <div className="flex flex-wrap gap-4">
      {triggers.map((trigger) => (
        <label key={trigger} className="flex items-center gap-2 text-sm">
          <Checkbox
            checked={value.includes(trigger)}
            onCheckedChange={(checked) =>
              onChange(checked ? [...value, trigger] : value.filter((entry) => entry !== trigger))
            }
          />
          {t(`triggers.${trigger}`)}
        </label>
      ))}
    </div>
  );
}
