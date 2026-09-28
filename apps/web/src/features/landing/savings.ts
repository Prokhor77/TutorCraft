import type { SavingsAssumptions } from '@/content/landing';

export type SavingsBreakdown = {
  grading: number;
  quizzes: number;
  messaging: number;
  /** Total hours saved per week. */
  hoursPerWeek: number;
  /** ≈ rubles per month: hours × weeks per month × hourly rate. */
  rublesPerMonth: number;
};

const HOURS_PRECISION = 10;
const RUBLE_ROUNDING = 10;
const FLOAT_DIGITS = 12;

/** Rounds to one decimal to avoid float noise like 18.700000000000003. */
export function roundHours(hours: number): number {
  // toPrecision strips binary float noise (4.2499999… → 4.25) before rounding half up.
  return Math.round(Number((hours * HOURS_PRECISION).toPrecision(FLOAT_DIGITS))) / HOURS_PRECISION;
}

export function clampStudents(students: number, assumptions: SavingsAssumptions): number {
  if (!Number.isFinite(students)) return assumptions.defaultStudents;
  return Math.min(assumptions.maxStudents, Math.max(assumptions.minStudents, Math.round(students)));
}

/** Weekly hours a tutor saves for `students` active students, under the landing assumptions. */
export function calculateSavings(
  students: number,
  assumptions: SavingsAssumptions,
): SavingsBreakdown {
  const count = clampStudents(students, assumptions);
  const { grading, quizzes, messaging } = assumptions.hoursPerStudent;
  const hoursPerWeek = roundHours(count * (grading + quizzes + messaging));
  const rublesPerMonth =
    Math.round(
      (hoursPerWeek * assumptions.weeksPerMonth * assumptions.hourlyRateRub) / RUBLE_ROUNDING,
    ) * RUBLE_ROUNDING;
  return {
    grading: roundHours(count * grading),
    quizzes: roundHours(count * quizzes),
    messaging: roundHours(count * messaging),
    hoursPerWeek,
    rublesPerMonth,
  };
}

export type PluralForms = { one: string; few?: string; many?: string; other: string };

/** Picks the grammatical form for `count` («1 ученик · 2 ученика · 5 учеников») via CLDR plural rules. */
export function declension(count: number, forms: PluralForms, locale: string): string {
  const category = new Intl.PluralRules(locale).select(count);
  switch (category) {
    case 'one':
      return forms.one;
    case 'few':
      return forms.few ?? forms.other;
    case 'many':
      return forms.many ?? forms.other;
    default:
      return forms.other;
  }
}
