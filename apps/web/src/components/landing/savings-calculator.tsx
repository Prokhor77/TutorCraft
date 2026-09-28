'use client';
import { Clock, FileQuestion, MessagesSquare, PiggyBank } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { useId, useState } from 'react';
import { LANDING } from '@/content/landing';
import { formatLandingPrice } from '@/features/landing/money';
import { calculateSavings, clampStudents, declension } from '@/features/landing/savings';

const assumptions = LANDING.savings;

/** Stitch «Калькулятор экономии времени»: pure math in features/landing/savings.ts, shown as an estimate. */
export function SavingsCalculator() {
  const t = useTranslations('landing.calculator');
  const locale = useLocale();
  const sliderId = useId();
  const [students, setStudents] = useState(assumptions.defaultStudents);
  const result = calculateSavings(students, assumptions);
  const number = new Intl.NumberFormat(locale, { maximumFractionDigits: 1 });
  const precise = new Intl.NumberFormat(locale, { maximumFractionDigits: 2 });
  const forms = {
    one: t('studentForms.one'),
    few: t('studentForms.few'),
    many: t('studentForms.many'),
    other: t('studentForms.other'),
  };
  const rows = [
    { key: 'grading', icon: Clock, hours: result.grading },
    { key: 'quizzes', icon: FileQuestion, hours: result.quizzes },
    { key: 'messaging', icon: MessagesSquare, hours: result.messaging },
  ] as const;
  const { grading, quizzes, messaging } = assumptions.hoursPerStudent;

  return (
    <div className="grid grid-cols-1 items-stretch gap-gutter lg:grid-cols-2">
      <div className="flex flex-col justify-center gap-6 rounded-lg border border-card-border bg-surface p-6 shadow-sm md:p-8">
        <label htmlFor={sliderId} className="font-heading text-lg font-semibold">
          {t('studentsLabel')}
        </label>
        <p className="flex items-baseline gap-2" aria-hidden>
          <span className="font-heading text-5xl font-bold tracking-tight text-primary">
            {students}
          </span>
          <span className="text-lg text-text-muted">{declension(students, forms, locale)}</span>
        </p>
        <input
          id={sliderId}
          type="range"
          min={assumptions.minStudents}
          max={assumptions.maxStudents}
          step={1}
          value={students}
          aria-valuetext={`${students} ${declension(students, forms, locale)}`}
          onChange={(event) => setStudents(clampStudents(Number(event.target.value), assumptions))}
          className="h-2 w-full cursor-pointer accent-primary"
        />
        <p className="flex justify-between text-xs text-text-muted">
          <span>{assumptions.minStudents}</span>
          <span>{assumptions.maxStudents}</span>
        </p>
      </div>
      <div className="flex flex-col gap-5 rounded-lg border border-card-border bg-gradient-to-br from-surface to-primary-soft/60 p-6 shadow-md md:p-8">
        <p className="flex items-center gap-2 text-label-md uppercase text-text-muted">
          <PiggyBank className="size-4 text-success" aria-hidden /> {t('resultTitle')}
        </p>
        <output htmlFor={sliderId} aria-live="polite" className="flex flex-col gap-1">
          <span className="font-heading text-4xl font-bold tracking-tight text-success">
            {t('hoursPerWeek', { hours: number.format(result.hoursPerWeek) })}
          </span>
          <span className="text-lg font-semibold">
            {t('money', {
              amount: formatLandingPrice(result.moneyPerMonth, assumptions.currency, locale),
            })}
          </span>
        </output>
        <ul className="flex flex-col gap-2">
          {rows.map(({ key, icon: Icon, hours }) => (
            <li
              key={key}
              className="flex items-center gap-3 rounded-full bg-surface px-4 py-2.5 text-sm shadow-sm"
            >
              <Icon className="size-4 shrink-0 text-primary" aria-hidden />
              <span className="min-w-0 flex-1">{t(key)}</span>
              <span className="font-semibold tabular-nums">
                {t('hours', { hours: number.format(hours) })}
              </span>
            </li>
          ))}
        </ul>
        <p className="text-xs text-text-muted">
          {t('note', {
            grading: precise.format(grading),
            quizzes: precise.format(quizzes),
            messaging: precise.format(messaging),
            rate: formatLandingPrice(assumptions.hourlyRate, assumptions.currency, locale),
          })}
        </p>
      </div>
    </div>
  );
}
