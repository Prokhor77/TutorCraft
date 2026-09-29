'use client';
import {
  Award,
  Eye,
  FileQuestion,
  ListOrdered,
  MousePointerClick,
  Plus,
  Repeat,
  Settings2,
  Shuffle,
  Timer,
} from 'lucide-react';
import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { SaveIndicator } from '@/components/editor/save-indicator';
import { QuestionForm } from '@/components/qbank/question-editor';
import { Button } from '@/components/ui/button';
import { Switch } from '@/components/ui/checkbox';
import { Input, NativeSelect } from '@/components/ui/input';
import { Segmented } from '@/components/ui/segmented';
import { ROUTES } from '@/features/auth/routes';
import { useProblemToast } from '@/features/app/use-problem-toast';
import { useAutosave } from '@/features/editor/use-autosave';
import { useItemPatcher } from '@/features/items/use-item';
import { useQCategories } from '@/features/qbank/use-qbank';
import { quizSummary } from '@/features/quiz/quiz-summary';
import { useQuizSlots } from '@/features/quiz/use-quiz';
import {
  GRADING_METHODS,
  REVIEW_TIMINGS,
  type ItemDetail,
  type QuizSettings,
} from '@/lib/api/schemas/courses';
import type { Question } from '@/lib/api/schemas/quiz';
import { cn } from '@/lib/utils/cn';
import { localId } from '@/lib/utils/ids';
import { SECONDS_PER_MINUTE } from '@/lib/utils/time';
import { SlotsEditor, toQuestionSummary, type SlotsEditorHandle } from './slots-editor';

const DEFAULT_TIME_LIMIT_MIN = 30;
const MAX_PASS_PERCENT = 100;
/** Toggles and selects save almost at once; number fields wait for the user to finish typing. */
const PARAMS_AUTOSAVE_DEBOUNCE_MS = 600;
const STICKY_PANE =
  'xl:sticky xl:top-[calc(var(--size-header)+1rem)] xl:max-h-[calc(100dvh-var(--size-header)-2rem)] xl:overflow-y-auto';

function ParamSection({
  icon: Icon,
  title,
  hint,
  control,
  children,
}: {
  icon: typeof Timer;
  title: string;
  hint?: string;
  control?: ReactNode;
  children?: ReactNode;
}) {
  return (
    <section className="flex flex-col gap-3 rounded bg-surface-muted p-4 transition-colors duration-fast focus-within:bg-surface-container">
      <div className="flex items-start justify-between gap-3">
        <div className="flex min-w-0 flex-col gap-0.5">
          <h3 className="flex items-center gap-2 font-sans text-sm font-semibold">
            <Icon className="size-4 shrink-0 text-primary" aria-hidden /> {title}
          </h3>
          {hint ? <p className="text-xs text-text-muted">{hint}</p> : null}
        </div>
        {control}
      </div>
      {children}
    </section>
  );
}

/** Mirrors the server bounds, so autosave does not send values that are still being typed (e.g. an empty field). */
function isValidParams(settings: QuizSettings): boolean {
  const { timeLimitSec, maxAttempts, passPercent } = settings;
  return (
    (timeLimitSec === null || timeLimitSec > 0) &&
    (maxAttempts === null || (Number.isInteger(maxAttempts) && maxAttempts >= 1)) &&
    (passPercent === null || (passPercent >= 0 && passPercent <= MAX_PASS_PERCENT))
  );
}

/** Stitch «Параметры теста»: the quiz settings a tutor changes most, as toggle sections; the rest in «Все настройки». */
function QuizParams({
  item,
  settings,
  className,
}: {
  item: ItemDetail;
  settings: QuizSettings;
  className?: string;
}) {
  const t = useTranslations('quizBuilder');
  const tSettings = useTranslations('itemSettings');
  const { patch } = useItemPatcher(item);
  const showProblem = useProblemToast();
  // Seeded once: afterwards the server echo of our own saves must not overwrite what is being typed.
  const [draft, setDraft] = useState(settings);
  const autosave = useAutosave({
    value: draft,
    save: (value) => patch({ settings: value }),
    isValid: isValidParams,
    debounceMs: PARAMS_AUTOSAVE_DEBOUNCE_MS,
    onError: showProblem,
  });
  const set = <K extends keyof QuizSettings>(key: K, value: QuizSettings[K]) =>
    setDraft((current) => ({ ...current, [key]: value }));
  const minutes = draft.timeLimitSec === null ? null : draft.timeLimitSec / SECONDS_PER_MINUTE;
  return (
    <aside
      aria-labelledby="quiz-params-title"
      className={cn(
        'min-w-0 flex-col gap-3 rounded-lg border border-card-border bg-surface p-5 shadow-sm',
        STICKY_PANE,
        className,
      )}
    >
      <div className="flex items-center gap-2">
        <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-primary-soft text-primary">
          <Settings2 className="size-4" aria-hidden />
        </span>
        <h2 id="quiz-params-title" className="flex-1 text-lg">
          {t('params')}
        </h2>
      </div>
      <SaveIndicator status={autosave.status} lastSavedAt={autosave.lastSavedAt} />
      <ParamSection
        icon={Timer}
        title={t('timeLimit')}
        control={
          <Switch
            checked={minutes !== null}
            aria-label={t('timeLimit')}
            onCheckedChange={(on) =>
              set('timeLimitSec', on ? DEFAULT_TIME_LIMIT_MIN * SECONDS_PER_MINUTE : null)
            }
          />
        }
      >
        {minutes !== null ? (
          <label className="flex items-center gap-2 text-sm text-text-muted">
            <Input
              type="number"
              min={1}
              className="h-10 w-24 bg-surface"
              value={minutes}
              onChange={(event) =>
                set('timeLimitSec', Number(event.target.value || 1) * SECONDS_PER_MINUTE)
              }
            />
            {t('minutesUnit')}
          </label>
        ) : null}
      </ParamSection>
      <ParamSection icon={Repeat} title={t('attempts')} hint={tSettings('unlimitedHint')}>
        <div className="flex flex-col gap-2">
          <Input
            type="number"
            min={1}
            aria-label={tSettings('maxAttempts')}
            placeholder="∞"
            className="h-10 bg-surface"
            value={draft.maxAttempts ?? ''}
            onChange={(event) =>
              set('maxAttempts', event.target.value === '' ? null : Number(event.target.value))
            }
          />
          <NativeSelect
            aria-label={tSettings('gradingMethod')}
            className="h-10 bg-surface"
            value={draft.gradingMethod}
            onChange={(event) =>
              set('gradingMethod', event.target.value as QuizSettings['gradingMethod'])
            }
          >
            {GRADING_METHODS.map((method) => (
              <option key={method} value={method}>
                {tSettings(`gradingMethods.${method}`)}
              </option>
            ))}
          </NativeSelect>
        </div>
      </ParamSection>
      <ParamSection
        icon={Shuffle}
        title={tSettings('shuffleQuestions')}
        hint={t('shuffleAnswersHint')}
        control={
          <Switch
            checked={draft.shuffleQuestions}
            aria-label={tSettings('shuffleQuestions')}
            onCheckedChange={(on) =>
              setDraft((current) => ({ ...current, shuffleQuestions: on, shuffleAnswers: on }))
            }
          />
        }
      />
      <ParamSection icon={Eye} title={t('showAnswers')}>
        <NativeSelect
          aria-label={t('showAnswers')}
          className="h-10 bg-surface"
          value={draft.review.whenCorrectAnswers}
          onChange={(event) =>
            set('review', {
              ...draft.review,
              whenCorrectAnswers: event.target
                .value as QuizSettings['review']['whenCorrectAnswers'],
            })
          }
        >
          {REVIEW_TIMINGS.map((timing) => (
            <option key={timing} value={timing}>
              {tSettings(`reviewTimings.${timing}`)}
            </option>
          ))}
        </NativeSelect>
      </ParamSection>
      <ParamSection icon={Award} title={tSettings('passPercent')}>
        <label className="flex items-center gap-2 text-sm text-text-muted">
          <Input
            type="number"
            min={0}
            max={100}
            aria-label={tSettings('passPercent')}
            className="h-10 w-24 bg-surface"
            value={draft.passPercent ?? ''}
            onChange={(event) =>
              set('passPercent', event.target.value === '' ? null : Number(event.target.value))
            }
          />
          %
        </label>
      </ParamSection>
      <Button asChild variant="secondary">
        <Link href={ROUTES.itemSettings(item.courseId, item.id)}>
          <Settings2 aria-hidden /> {t('allSettings')}
        </Link>
      </Button>
    </aside>
  );
}

/** Stitch header stat chip («Вопросы · 8 пунктов», «Хронометраж · 35 минут») — values from slots/settings only. */
function SummaryChip({
  icon: Icon,
  label,
  value,
  unit,
  tone = 'primary',
  hint,
}: {
  icon: typeof Timer;
  label: string;
  value: ReactNode;
  unit: string;
  tone?: 'primary' | 'warning' | 'success';
  hint?: string;
}) {
  return (
    <div className="flex min-w-0 items-center gap-3 rounded-md border border-card-border bg-surface py-2 pl-2 pr-3 shadow-sm sm:rounded-full sm:pr-5">
      <span
        className={cn(
          'flex size-9 shrink-0 items-center justify-center rounded-full',
          tone === 'primary' && 'bg-primary-soft text-primary',
          tone === 'warning' && 'bg-warning-soft text-warning',
          tone === 'success' && 'bg-success-soft text-success',
        )}
      >
        <Icon className="size-4" aria-hidden />
      </span>
      <dl className="flex min-w-0 flex-col">
        <dt className="truncate text-label-sm uppercase text-text-muted">{label}</dt>
        <dd className="font-heading text-base font-semibold leading-tight sm:truncate">
          {value} <span className="font-sans text-sm font-normal text-text-muted">{unit}</span>
          {hint ? <span className="sr-only"> ({hint})</span> : null}
        </dd>
      </dl>
    </div>
  );
}

type Pane = 'editor' | 'structure' | 'params';
type EditorSession = { key: string; questionId: string | null; number: number };

/**
 * Quiz builder (Stitch «Конструктор тестов и квизов»): summary chips from real slot/settings data, then a three-pane
 * workbench — question structure (sortable slots) · selected bank question's editor · «Параметры теста» inspector.
 * Below xl the panes collapse into one column switched by a segmented control.
 */
export function QuizBuilder({ item }: { item: ItemDetail }) {
  const t = useTranslations('quizBuilder');
  const tSlots = useTranslations('quizSlots');
  const slots = useQuizSlots(item.id);
  const categories = useQCategories(item.courseId);
  const slotsEditor = useRef<SlotsEditorHandle>(null);
  // The editor stage. `questionId: null` = a new question; autosave creates it (and puts it into this quiz) as soon
  // as it is valid, and the session `key` stays the same so the form is not remounted while the tutor types.
  const [editor, setEditor] = useState<EditorSession | null>(null);
  const [pane, setPane] = useState<Pane>('editor');
  const startCreate = () => {
    setEditor({
      key: localId('question'),
      questionId: null,
      number: slotsEditor.current?.nextNumber() ?? 1,
    });
    setPane('editor');
  };
  const onQuestionSaved = (question: Question) => {
    const summary = toQuestionSummary(question);
    if (editor?.questionId === null) {
      slotsEditor.current?.addQuestions([summary], { notify: false });
      setEditor({ ...editor, questionId: question.id });
    } else {
      slotsEditor.current?.refreshQuestion(summary);
    }
  };
  // Open the first bank question by default so the editor is never an empty stage.
  useEffect(() => {
    if (editor || !slots.data) return;
    const index = slots.data.slots.findIndex((slot) => 'questionId' in slot);
    const first = slots.data.slots[index];
    if (first && 'questionId' in first)
      setEditor({ key: first.questionId, questionId: first.questionId, number: index + 1 });
  }, [slots.data, editor]);
  if (item.settings.kind !== 'quiz') return null;
  const settings = item.settings;
  const summary = quizSummary(slots.data?.slots ?? []);
  const minutes =
    settings.timeLimitSec === null ? null : Math.round(settings.timeLimitSec / SECONDS_PER_MINUTE);
  const pointsUnknown = summary.partial && summary.points === 0;
  const paneClass = (target: Pane) => (pane === target ? 'flex' : 'hidden xl:flex');
  return (
    <div className="flex flex-col gap-gutter">
      <div
        role="group"
        aria-label={t('summary')}
        className="grid grid-cols-2 gap-2 sm:flex sm:flex-wrap sm:gap-3"
      >
        <SummaryChip
          icon={ListOrdered}
          label={t('statQuestions')}
          value={summary.questions}
          unit={t('questionsUnit', { count: summary.questions })}
        />
        <SummaryChip
          icon={Award}
          tone="warning"
          label={t('statPoints')}
          value={pointsUnknown ? '—' : summary.partial ? `≥ ${summary.points}` : summary.points}
          unit={pointsUnknown ? t('pointsDefault') : t('pointsUnit')}
          hint={summary.partial ? t('pointsPartial') : undefined}
        />
        <SummaryChip
          icon={Timer}
          tone="success"
          label={t('statTime')}
          value={minutes ?? '∞'}
          unit={minutes ? t('minutesUnit') : t('noLimit')}
        />
        <SummaryChip
          icon={Repeat}
          label={t('statAttempts')}
          value={settings.maxAttempts ?? '∞'}
          unit={
            settings.maxAttempts ? t('attemptsUnit', { count: settings.maxAttempts }) : t('noLimit')
          }
        />
      </div>
      <Segmented<Pane>
        className="flex w-full xl:hidden"
        label={t('panes')}
        value={pane}
        onChange={setPane}
        options={[
          { value: 'editor', label: t('paneEditor') },
          { value: 'structure', label: t('paneStructure') },
          { value: 'params', label: t('paneParams') },
        ]}
      />
      <div className="grid grid-cols-1 items-start gap-gutter xl:grid-cols-[minmax(0,var(--size-tree))_minmax(0,1fr)_var(--size-inspector)]">
        <section
          aria-labelledby="quiz-structure-title"
          className={cn(
            'min-w-0 flex-col gap-4 rounded-lg border border-card-border bg-surface p-4 shadow-sm',
            STICKY_PANE,
            paneClass('structure'),
          )}
        >
          <div className="flex items-center gap-2 px-1 pt-1">
            <h2 id="quiz-structure-title" className="min-w-0 flex-1 text-lg">
              {t('structure')}
            </h2>
            <span className="shrink-0 rounded-full bg-surface-muted px-2.5 py-0.5 text-label-md text-text-muted">
              {t('inQuiz', { count: summary.questions })}
            </span>
          </div>
          <SlotsEditor
            courseId={item.courseId}
            itemId={item.id}
            compact
            handleRef={slotsEditor}
            selectedQuestionId={editor?.questionId ?? null}
            onSelectQuestion={(id, number) => {
              setEditor({ key: id, questionId: id, number });
              setPane('editor');
            }}
            onCreateQuestion={startCreate}
          />
        </section>
        <div className={cn('min-w-0 flex-col', paneClass('editor'))}>
          {editor ? (
            <QuestionForm
              key={editor.key}
              variant="card"
              autosave
              number={editor.number}
              courseId={item.courseId}
              questionId={editor.questionId}
              categories={categories.data ?? []}
              defaultCategoryId={null}
              onCancel={editor.questionId === null ? () => setEditor(null) : undefined}
              onSaved={onQuestionSaved}
            />
          ) : (
            <section className="flex flex-col items-center gap-3 rounded-lg border-2 border-dashed border-accent/25 bg-surface px-6 py-14 text-center">
              <span className="flex size-12 items-center justify-center rounded-full bg-accent/10 text-primary">
                <MousePointerClick className="size-6" aria-hidden />
              </span>
              <p className="font-heading text-lg font-semibold">{t('selectTitle')}</p>
              <p className="max-w-sm text-sm text-text-muted">{t('selectText')}</p>
              <div className="flex flex-wrap justify-center gap-2">
                <Button size="sm" onClick={startCreate}>
                  <Plus aria-hidden /> {tSlots('newQuestion')}
                </Button>
                <Button
                  variant="secondary"
                  size="sm"
                  className="xl:hidden"
                  onClick={() => setPane('structure')}
                >
                  <FileQuestion aria-hidden /> {t('paneStructure')}
                </Button>
              </div>
            </section>
          )}
        </div>
        <QuizParams item={item} settings={settings} className={paneClass('params')} />
      </div>
    </div>
  );
}
