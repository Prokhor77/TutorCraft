'use client';
import {
  Award,
  Eye,
  FileQuestion,
  ListOrdered,
  MousePointerClick,
  Repeat,
  Save,
  Settings2,
  Shuffle,
  Timer,
} from 'lucide-react';
import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { useEffect, useState, type ReactNode } from 'react';
import { QuestionForm } from '@/components/qbank/question-editor';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Switch } from '@/components/ui/checkbox';
import { Input, NativeSelect } from '@/components/ui/input';
import { StatCard, StatGrid } from '@/components/ui/stat-card';
import { toast } from '@/components/ui/toast';
import { ROUTES } from '@/features/auth/routes';
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
import { SECONDS_PER_MINUTE } from '@/lib/utils/time';
import { SlotsEditor } from './slots-editor';

const DEFAULT_TIME_LIMIT_MIN = 30;

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
    <section className="flex flex-col gap-3 rounded bg-surface-muted p-4">
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

/** Stitch «Параметры теста»: the quiz settings a tutor changes most, as toggle sections; the rest in «Все настройки». */
function QuizParams({ item, settings }: { item: ItemDetail; settings: QuizSettings }) {
  const t = useTranslations('quizBuilder');
  const tSettings = useTranslations('itemSettings');
  const { patch, isSaving } = useItemPatcher(item);
  const [draft, setDraft] = useState(settings);
  useEffect(() => setDraft(settings), [settings]);
  const set = <K extends keyof QuizSettings>(key: K, value: QuizSettings[K]) =>
    setDraft((current) => ({ ...current, [key]: value }));
  const dirty = JSON.stringify(draft) !== JSON.stringify(settings);
  const minutes = draft.timeLimitSec === null ? null : draft.timeLimitSec / SECONDS_PER_MINUTE;
  return (
    <Card className="flex flex-col gap-3 p-5">
      <h2 className="flex items-center gap-2 text-xl">
        <Settings2 className="size-5 text-primary" aria-hidden /> {t('params')}
      </h2>
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
              className="h-10 w-24"
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
            className="h-10"
            value={draft.maxAttempts ?? ''}
            onChange={(event) =>
              set('maxAttempts', event.target.value === '' ? null : Number(event.target.value))
            }
          />
          <NativeSelect
            aria-label={tSettings('gradingMethod')}
            className="h-10"
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
          className="h-10"
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
        <Input
          type="number"
          min={0}
          max={100}
          aria-label={tSettings('passPercent')}
          className="h-10"
          value={draft.passPercent ?? ''}
          onChange={(event) =>
            set('passPercent', event.target.value === '' ? null : Number(event.target.value))
          }
        />
      </ParamSection>
      <Button
        variant="success"
        disabled={!dirty}
        loading={isSaving}
        onClick={() =>
          void patch({ settings: draft })
            .then(() => toast({ tone: 'success', title: t('paramsSaved') }))
            .catch(() => undefined)
        }
      >
        <Save aria-hidden /> {t('saveParams')}
      </Button>
      <Button asChild variant="secondary">
        <Link href={ROUTES.itemSettings(item.courseId, item.id)}>
          <Settings2 aria-hidden /> {t('allSettings')}
        </Link>
      </Button>
    </Card>
  );
}

/**
 * Quiz builder (Stitch «Конструктор тестов и квизов»): stat cards from real slot/settings data, question structure
 * (sortable slots) on the left, the selected bank question's editor in the center, «Параметры теста» on the right.
 */
export function QuizBuilder({ item }: { item: ItemDetail }) {
  const t = useTranslations('quizBuilder');
  const slots = useQuizSlots(item.id);
  const categories = useQCategories(item.courseId);
  const [selected, setSelected] = useState<{ id: string; number: number } | null>(null);
  // Open the first bank question by default so the editor is never an empty stage.
  useEffect(() => {
    if (selected || !slots.data) return;
    const index = slots.data.slots.findIndex((slot) => 'questionId' in slot);
    const first = slots.data.slots[index];
    if (first && 'questionId' in first) setSelected({ id: first.questionId, number: index + 1 });
  }, [slots.data, selected]);
  if (item.settings.kind !== 'quiz') return null;
  const settings = item.settings;
  const summary = quizSummary(slots.data?.slots ?? []);
  const minutes =
    settings.timeLimitSec === null ? null : Math.round(settings.timeLimitSec / SECONDS_PER_MINUTE);
  return (
    <div className="flex flex-col gap-gutter">
      <StatGrid>
        <StatCard
          label={t('statQuestions')}
          icon={ListOrdered}
          value={summary.questions}
          unit={t('questionsUnit', { count: summary.questions })}
        />
        <StatCard
          label={t('statPoints')}
          icon={Award}
          tone="warning"
          value={summary.partial ? `≥ ${summary.points}` : summary.points}
          unit={t('pointsUnit')}
          footer={summary.partial ? t('pointsPartial') : undefined}
        />
        <StatCard
          label={t('statTime')}
          icon={Timer}
          tone="success"
          value={minutes ?? '∞'}
          unit={minutes ? t('minutesUnit') : t('noLimit')}
        />
        <StatCard
          label={t('statAttempts')}
          icon={Repeat}
          value={settings.maxAttempts ?? '∞'}
          unit={
            settings.maxAttempts ? t('attemptsUnit', { count: settings.maxAttempts }) : t('noLimit')
          }
        />
      </StatGrid>
      <div className="grid grid-cols-1 items-start gap-gutter xl:grid-cols-[minmax(0,var(--size-tree))_minmax(0,1fr)_var(--size-inspector)]">
        <Card className="flex flex-col gap-4 p-4 xl:sticky xl:top-[calc(var(--size-header)+1rem)] xl:max-h-[calc(100dvh-var(--size-header)-2rem)] xl:overflow-y-auto">
          <h2 className="flex items-center gap-2 text-lg">
            <FileQuestion className="size-5 text-primary" aria-hidden /> {t('structure')}
          </h2>
          <SlotsEditor
            courseId={item.courseId}
            itemId={item.id}
            compact
            selectedQuestionId={selected?.id ?? null}
            onSelectQuestion={(id, number) => setSelected({ id, number })}
          />
        </Card>
        <div className="min-w-0">
          {selected ? (
            <QuestionForm
              key={selected.id}
              variant="card"
              number={selected.number}
              courseId={item.courseId}
              questionId={selected.id}
              categories={categories.data ?? []}
              defaultCategoryId={null}
            />
          ) : (
            <Card className="flex flex-col items-center gap-3 px-6 py-14 text-center">
              <span className="flex size-12 items-center justify-center rounded-full bg-accent/10 text-primary">
                <MousePointerClick className="size-6" aria-hidden />
              </span>
              <p className="font-heading text-lg font-semibold">{t('selectTitle')}</p>
              <p className="max-w-sm text-sm text-text-muted">{t('selectText')}</p>
            </Card>
          )}
        </div>
        <QuizParams item={item} settings={settings} />
      </div>
    </div>
  );
}
