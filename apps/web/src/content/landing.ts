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

export type PlanFeature = { text: LocalizedText; included: boolean };

export type Plan = {
  id: string;
  name: LocalizedText;
  /** Monthly price in whole rubles; 0 = free. */
  priceRub: number;
  /** Price period caption, e.g. «навсегда» / «в месяц». */
  period: LocalizedText;
  description: LocalizedText;
  highlighted?: boolean;
  /** Badge over a highlighted plan. */
  badge?: LocalizedText;
  features: PlanFeature[];
};

/** Time-savings calculator assumptions (hours per student per week), shown to visitors as an estimate. */
export type SavingsAssumptions = {
  minStudents: number;
  maxStudents: number;
  defaultStudents: number;
  hoursPerStudent: { grading: number; quizzes: number; messaging: number };
  /** Hourly rate used for the «≈ N ₽ в месяц» estimate. */
  hourlyRateRub: number;
  weeksPerMonth: number;
};

export type LandingContent = {
  socialProof: SocialProof | null;
  testimonials: Testimonial[];
  plans: Plan[];
  savings: SavingsAssumptions;
  contacts: { email: string | null; telegram: string | null };
};

const t = (ru: string, en: string): LocalizedText => ({ ru, en });
const yes = (ru: string, en: string): PlanFeature => ({ text: t(ru, en), included: true });
const no = (ru: string, en: string): PlanFeature => ({ text: t(ru, en), included: false });

export const LANDING: LandingContent = {
  // Fill only with real numbers — see README «Landing content».
  socialProof: null,
  testimonials: [],
  plans: [
    {
      id: 'start',
      name: t('Старт', 'Start'),
      priceRub: 0,
      period: t('навсегда', 'forever'),
      description: t(
        'Чтобы собрать первый курс и позвать учеников',
        'Build your first course and invite students',
      ),
      features: [
        yes('Конструктор курсов и блочный редактор', 'Course builder and block editor'),
        yes('Задания и тесты: 8 типов вопросов', 'Assignments and quizzes: 8 question types'),
        yes('Единая очередь проверки и журнал оценок', 'Unified grading queue and gradebook'),
        yes('Уведомления в Telegram и на почту', 'Telegram and email notifications'),
        no('Продажа курсов с онлайн-оплатой', 'Selling courses with online payments'),
        no('API-токены и вебхуки', 'API tokens and webhooks'),
      ],
    },
    {
      id: 'pro',
      name: t('Профи Репетитор', 'Pro Tutor'),
      priceRub: 1490,
      period: t('в месяц', 'per month'),
      description: t(
        'Для репетитора с потоком учеников',
        'For a tutor with a steady flow of students',
      ),
      highlighted: true,
      badge: t('Популярный выбор', 'Most popular'),
      features: [
        yes('Всё из тарифа «Старт»', 'Everything in Start'),
        yes('Видео HLS и медиатека курса', 'HLS video and course media library'),
        yes('Условия доступа и отчёт о прогрессе', 'Access conditions and progress report'),
        yes('Продажа курсов с онлайн-оплатой', 'Selling courses with online payments'),
        no('Брендинг школы и несколько преподавателей', 'School branding and multiple teachers'),
        no('API-токены и вебхуки', 'API tokens and webhooks'),
      ],
    },
    {
      id: 'studio',
      name: t('Студия / Онлайн-школа', 'Studio / Online school'),
      priceRub: 3990,
      period: t('в месяц', 'per month'),
      description: t('Для команды преподавателей', 'For a team of teachers'),
      features: [
        yes('Всё из тарифа «Профи Репетитор»', 'Everything in Pro Tutor'),
        yes('Брендинг школы: логотип и цвет', 'School branding: logo and colour'),
        yes('Несколько преподавателей и ассистентов', 'Multiple teachers and assistants'),
        yes('Группы, импорт пользователей из CSV', 'Groups and CSV user import'),
        yes('API-токены, вебхуки и журнал аудита', 'API tokens, webhooks and audit log'),
      ],
    },
  ],
  savings: {
    minStudents: 5,
    maxStudents: 60,
    defaultStudents: 22,
    hoursPerStudent: { grading: 0.42, quizzes: 0.25, messaging: 0.18 },
    hourlyRateRub: 750,
    weeksPerMonth: 4,
  },
  contacts: { email: null, telegram: null },
};
