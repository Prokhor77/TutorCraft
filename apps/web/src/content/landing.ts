/**
 * Landing page content that is business data rather than UI copy (edit here, no code changes needed).
 *
 * HONESTY RULE: everything rendered from this file is presented to visitors as fact. Only put real, verifiable
 * numbers and real customer quotes (with their consent) here. Empty / null values hide the related blocks:
 * - `socialProof: null`      → the hero social-proof strip is not rendered;
 * - `testimonials: []`       → the «Отзывы» section and its header link are not rendered;
 * - `contacts.email: null`   → the footer «Компания» column is not rendered.
 * Localised strings use `{ ru, en }` pairs (next-intl locales).
 */

export type LocalizedText = { ru: string; en: string };

export type SocialProof = {
  /** Number of tutors actually using the product, e.g. 4800 (rendered as «4 800+»). */
  tutorsCount: number;
  /** Average rating 0–5 from a real, citable source (e.g. an app store). */
  rating: number | null;
  /** Where the rating comes from, shown next to it. */
  ratingSource: LocalizedText | null;
};

export type Testimonial = {
  name: string;
  /** e.g. «Репетитор по математике, 8 лет стажа». */
  role: LocalizedText;
  quote: LocalizedText;
  /** 1–5 stars as given by the author. */
  stars: number;
};

/** Currency of a landing amount (ISO 4217), e.g. `USD` for subscriptions, `BYN` for the savings estimate. */
export type LandingCurrency = 'USD' | 'BYN';

/** One subscription term. Every term unlocks the same product — only duration and price differ. */
export type Plan = {
  id: string;
  name: LocalizedText;
  /** Subscription length in months. */
  months: number;
  /** Price for the whole term in whole units of `Pricing.currency`. */
  price: number;
  highlighted?: boolean;
  /** Badge over a highlighted plan. */
  badge?: LocalizedText;
};

export type Pricing = {
  currency: LandingCurrency;
  /** Capabilities included in every subscription (listed once under the plans). */
  features: LocalizedText[];
  plans: Plan[];
};

/** Time-savings calculator assumptions (hours per student per week), shown to visitors as an estimate. */
export type SavingsAssumptions = {
  minStudents: number;
  maxStudents: number;
  defaultStudents: number;
  hoursPerStudent: { grading: number; quizzes: number; messaging: number };
  /** Hourly rate used for the «≈ N BYN в месяц» estimate. */
  hourlyRate: number;
  currency: LandingCurrency;
  weeksPerMonth: number;
};

export type LandingContent = {
  socialProof: SocialProof | null;
  testimonials: Testimonial[];
  pricing: Pricing;
  savings: SavingsAssumptions;
  contacts: { email: string | null; telegram: string | null };
};

const t = (ru: string, en: string): LocalizedText => ({ ru, en });

export const LANDING: LandingContent = {
  // Fill only with real numbers — see README «Landing content».
  socialProof: null,
  testimonials: [],
  pricing: {
    currency: 'USD',
    features: [
      t('Конструктор курсов и блочный редактор', 'Course builder and block editor'),
      t('Задания и тесты: 8 типов вопросов', 'Assignments and quizzes: 8 question types'),
      t('Единая очередь проверки и журнал оценок', 'Unified grading queue and gradebook'),
      t('Уведомления в Telegram и на почту', 'Telegram and email notifications'),
      t('Видео и медиатека курса', 'Video and course media library'),
      t('Условия доступа и отчёт о прогрессе', 'Access conditions and progress report'),
      t('Брендинг школы: логотип и цвет', 'School branding: logo and colour'),
      t('Несколько преподавателей и ассистентов', 'Multiple teachers and assistants'),
      t('Группы, импорт пользователей из CSV', 'Groups and CSV user import'),
      t('API-токены, вебхуки и журнал аудита', 'API tokens, webhooks and audit log'),
    ],
    plans: [
      { id: 'month', name: t('1 месяц', '1 month'), months: 1, price: 30 },
      { id: 'quarter', name: t('3 месяца', '3 months'), months: 3, price: 75 },
      {
        id: 'year',
        name: t('1 год', '1 year'),
        months: 12,
        price: 150,
        highlighted: true,
        badge: t('Выгоднее всего', 'Best value'),
      },
    ],
  },
  savings: {
    minStudents: 5,
    maxStudents: 60,
    defaultStudents: 22,
    hoursPerStudent: { grading: 0.42, quizzes: 0.25, messaging: 0.18 },
    hourlyRate: 30,
    currency: 'BYN',
    weeksPerMonth: 4,
  },
  contacts: { email: null, telegram: null },
};
