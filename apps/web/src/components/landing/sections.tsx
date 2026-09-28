import {
  ArrowRight,
  BellRing,
  Check,
  CheckCircle2,
  CreditCard,
  FileQuestion,
  FolderTree,
  GripVertical,
  Lock,
  PlayCircle,
  Rocket,
  Send,
  Sigma,
  Smartphone,
  Sparkles,
  Star,
  Timer,
  TrendingUp,
  UploadCloud,
  Video,
  X,
  Zap,
  type LucideIcon,
} from 'lucide-react';
import Link from 'next/link';
import { getLocale, getTranslations } from 'next-intl/server';
import type { ReactNode } from 'react';
import { Avatar } from '@/components/ui/avatar';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { LANDING } from '@/content/landing';
import { localize } from '@/content/localize';
import { ROUTES } from '@/features/auth/routes';
import { cn } from '@/lib/utils/cn';
import { HeroMockup } from './hero-mockup';
import { LANDING_ANCHORS } from './site-header';

const MAX_STARS = 5;

/** Whole-ruble prices without kopecks: «1 490 ₽». */
export function formatRubles(amount: number, locale: string): string {
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency: 'RUB',
    maximumFractionDigits: 0,
  }).format(amount);
}

/** Section wrapper: anchor target below the sticky header, eyebrow + headline + subtitle. */
export function LandingSection({
  id,
  eyebrow,
  title,
  subtitle,
  className,
  children,
}: {
  id?: string;
  eyebrow: string;
  title: string;
  subtitle?: string;
  className?: string;
  children: ReactNode;
}) {
  const headingId = id ? `${id}-title` : undefined;
  return (
    <section
      id={id}
      aria-labelledby={headingId}
      className={cn('scroll-mt-[calc(var(--size-header-public)+1rem)] py-16 md:py-24', className)}
    >
      <div className="mx-auto flex max-w-content flex-col gap-10 px-page-x">
        <div className="mx-auto flex max-w-2xl flex-col items-center gap-3 text-center">
          <span className="rounded-full bg-accent/10 px-4 py-1.5 text-label-md uppercase text-primary">
            {eyebrow}
          </span>
          <h2 id={headingId} className="text-2xl md:text-3xl">
            {title}
          </h2>
          {subtitle ? <p className="text-base text-text-muted">{subtitle}</p> : null}
        </div>
        {children}
      </div>
    </section>
  );
}

async function SocialProofStrip() {
  const proof = LANDING.socialProof;
  if (!proof) return null;
  const t = await getTranslations('landing.hero');
  const locale = await getLocale();
  const count = new Intl.NumberFormat(locale).format(proof.tutorsCount);
  return (
    <div className="flex flex-wrap items-center justify-center gap-3 text-sm text-text-muted lg:justify-start">
      <span className="font-semibold text-text">{t('socialProof', { count })}</span>
      {proof.rating !== null && proof.ratingSource ? (
        <span className="flex items-center gap-1">
          <Star className="size-4 fill-warning-accent text-warning-accent" aria-hidden />
          {t('rating', {
            rating: new Intl.NumberFormat(locale, { maximumFractionDigits: 2 }).format(
              proof.rating,
            ),
            source: localize(proof.ratingSource, locale),
          })}
        </span>
      ) : null}
    </div>
  );
}

/** Stitch hero: gradient canvas with two soft blobs, pill badge, headline with gradient accent, CTAs, mockup. */
export async function Hero() {
  const t = await getTranslations('landing.hero');
  return (
    <section className="relative overflow-hidden bg-gradient-to-b from-background via-surface-muted to-background">
      <div
        aria-hidden
        className="pointer-events-none absolute -top-40 left-1/2 size-[36rem] -translate-x-1/2 rounded-full bg-primary-soft opacity-80 blur-3xl"
      />
      <div
        aria-hidden
        className="pointer-events-none absolute -right-32 top-40 size-96 rounded-full bg-success-soft opacity-80 blur-3xl"
      />
      <div className="relative mx-auto flex max-w-content flex-col gap-12 px-page-x pb-16 pt-12 md:pb-24 md:pt-20">
        <div className="mx-auto flex max-w-4xl flex-col items-center gap-6 text-center">
          <span className="inline-flex flex-wrap items-center justify-center gap-2 rounded-full border border-card-border bg-surface px-4 py-1.5 text-label-md text-primary shadow-sm">
            <Rocket className="size-4" aria-hidden /> {t('badge')}
            <span aria-hidden className="text-text-muted">
              •
            </span>
            <span className="text-text-muted">{t('badgeNote')}</span>
          </span>
          <h1 className="text-hero-mobile font-bold md:text-4xl">
            {t('titleStart')}
            <span className="bg-gradient-to-r from-primary to-accent bg-clip-text text-transparent">
              {t('titleAccent')}
            </span>
          </h1>
          <p className="max-w-2xl text-base text-text-muted md:text-lg">{t('subtitle')}</p>
          <div className="flex w-full flex-col justify-center gap-3 sm:w-auto sm:flex-row">
            <Button asChild size="lg" className="h-14 px-8 text-base">
              <Link href={ROUTES.register}>
                {t('ctaPrimary')} <ArrowRight aria-hidden />
              </Link>
            </Button>
            <Button
              asChild
              size="lg"
              variant="ghost"
              className="h-14 bg-surface px-8 text-base shadow-sm hover:shadow-md"
            >
              <a href={`#${LANDING_ANCHORS.demo}`}>
                <PlayCircle aria-hidden /> {t('ctaDemo')}
              </a>
            </Button>
          </div>
          <SocialProofStrip />
        </div>
        <div id={LANDING_ANCHORS.demo} className="scroll-mt-[calc(var(--size-header-public)+1rem)]">
          <HeroMockup />
        </div>
      </div>
    </section>
  );
}

const TRUST_CHIPS: { key: string; icon: LucideIcon }[] = [
  { key: 'telegram', icon: Send },
  { key: 'latex', icon: Sigma },
  { key: 'video', icon: Video },
  { key: 'quiz', icon: FileQuestion },
  { key: 'payments', icon: CreditCard },
  { key: 'mobile', icon: Smartphone },
];

export async function TrustStrip() {
  const t = await getTranslations('landing.trust');
  return (
    <section aria-labelledby="trust-title" className="border-y border-card-border bg-surface-muted">
      <div className="mx-auto flex max-w-content flex-col gap-5 px-page-x py-8 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex flex-col gap-1">
          <span className="text-label-md uppercase text-text-muted">{t('eyebrow')}</span>
          <h2 id="trust-title" className="text-lg">
            {t('title')}
          </h2>
        </div>
        <ul className="flex flex-wrap gap-2">
          {TRUST_CHIPS.map(({ key, icon: Icon }) => (
            <li
              key={key}
              className="flex items-center gap-2 rounded-full border border-card-border bg-surface px-4 py-2 text-label-lg shadow-sm"
            >
              <Icon className="size-4 text-primary" aria-hidden /> {t(key)}
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}

function ToolCard({
  number,
  tag,
  title,
  text,
  checks,
  mockup,
  className,
}: {
  number: string;
  tag: string;
  title: string;
  text: string;
  checks: string[];
  mockup: ReactNode;
  className?: string;
}) {
  return (
    <article
      className={cn(
        'lift flex flex-col gap-5 rounded-lg border border-card-border bg-surface p-6 shadow-sm hover:border-card-border-hover hover:shadow-md md:p-8',
        className,
      )}
    >
      <div className="flex items-center gap-3">
        <span className="flex size-10 items-center justify-center rounded-full bg-primary font-heading text-sm font-bold text-primary-foreground">
          {number}
        </span>
        <Badge tone="primary">{tag}</Badge>
      </div>
      <h3 className="text-xl md:text-2xl">{title}</h3>
      <p className="text-sm text-text-muted md:text-base">{text}</p>
      <div
        aria-hidden
        inert
        className="rounded-md bg-surface-muted p-4 shadow-[inset_0_2px_6px_rgb(15_23_42/0.06)]"
      >
        {mockup}
      </div>
      <ul className="mt-auto flex flex-col gap-2">
        {checks.map((check) => (
          <li key={check} className="flex items-center gap-2 text-sm">
            <CheckCircle2 className="size-4 shrink-0 text-success" aria-hidden /> {check}
          </li>
        ))}
      </ul>
    </article>
  );
}

/** Bento «4 инструмента для репетитора» (grid 12: 7/5, 5/7) with mini mockups of the real screens. */
export async function ToolsBento() {
  const t = await getTranslations('landing.tools');
  const builderRows = [
    { label: t('builder.mini1'), done: true },
    { label: t('builder.mini2'), done: true },
    { label: t('builder.mini3') },
    { label: t('builder.mini4'), locked: true },
  ];
  return (
    <LandingSection
      id={LANDING_ANCHORS.features}
      eyebrow={t('eyebrow')}
      title={t('title')}
      subtitle={t('subtitle')}
    >
      <div className="grid grid-cols-1 gap-gutter lg:grid-cols-12">
        <ToolCard
          className="lg:col-span-7"
          number="01"
          tag={t('builder.tag')}
          title={t('builder.title')}
          text={t('builder.text')}
          checks={[t('builder.check1'), t('builder.check2')]}
          mockup={
            <ul className="flex flex-col gap-1.5 text-xs">
              {builderRows.map((row) => (
                <li
                  key={row.label}
                  className="flex items-center gap-2 rounded-full bg-surface px-3 py-2 shadow-sm"
                >
                  <GripVertical className="size-3.5 text-text-muted" />
                  <FolderTree className="size-3.5 text-primary" />
                  <span className="min-w-0 flex-1 truncate">{row.label}</span>
                  {row.done ? <CheckCircle2 className="size-3.5 text-success" /> : null}
                  {row.locked ? (
                    <span className="flex items-center gap-1 text-label-sm text-text-muted">
                      <Lock className="size-3" /> {t('builder.locked')}
                    </span>
                  ) : null}
                </li>
              ))}
            </ul>
          }
        />
        <ToolCard
          className="lg:col-span-5"
          number="02"
          tag={t('quiz.tag')}
          title={t('quiz.title')}
          text={t('quiz.text')}
          checks={[t('quiz.check1'), t('quiz.check2')]}
          mockup={
            <div className="flex flex-col gap-2 text-xs">
              <p className="flex items-center justify-between gap-2 font-semibold">
                {t('quiz.question')}
                <span className="flex items-center gap-1 rounded-full bg-warning-soft px-2 py-0.5 text-warning">
                  <Timer className="size-3" /> {t('quiz.timer')}
                </span>
              </p>
              {(['a', 'b', 'c', 'd'] as const).map((key, index) => (
                <span
                  key={key}
                  className={cn(
                    'flex items-center gap-2 rounded-full border-[1.5px] bg-surface px-3 py-1.5',
                    key === 'b' ? 'border-accent bg-primary-soft/60' : 'border-border',
                  )}
                >
                  <span className="font-semibold text-primary">
                    {String.fromCharCode('A'.charCodeAt(0) + index)}
                  </span>
                  {t(`quiz.${key}`)}
                </span>
              ))}
            </div>
          }
        />
        <ToolCard
          className="lg:col-span-5"
          number="03"
          tag={t('grading.tag')}
          title={t('grading.title')}
          text={t('grading.text')}
          checks={[t('grading.check1'), t('grading.check2')]}
          mockup={
            <div className="flex flex-col gap-2 text-xs">
              <div className="flex items-center gap-2">
                <Avatar name={t('grading.student')} size="sm" />
                <span className="flex min-w-0 flex-1 flex-col">
                  <span className="truncate font-semibold">{t('grading.student')}</span>
                  <span className="truncate text-text-muted">{t('grading.work')}</span>
                </span>
                <Badge tone="success">18/20</Badge>
              </div>
              <p className="rounded bg-success-soft p-2 text-success">{t('grading.feedback')}</p>
            </div>
          }
        />
        <ToolCard
          className="lg:col-span-7"
          number="04"
          tag={t('media.tag')}
          title={t('media.title')}
          text={t('media.text')}
          checks={[t('media.check1'), t('media.check2')]}
          mockup={
            <div className="flex flex-col items-center gap-2 rounded-md border-2 border-dashed border-accent/30 bg-dropzone p-5 text-center text-xs">
              <UploadCloud className="size-6 text-primary" />
              <span className="font-semibold">{t('media.dropzone')}</span>
              <span className="rounded-full bg-surface px-3 py-1 text-label-md text-primary shadow-sm">
                {t('media.browse')}
              </span>
            </div>
          }
        />
      </div>
    </LandingSection>
  );
}

const STUDENT_BENEFITS: { key: string; icon: LucideIcon; tone: string }[] = [
  { key: 'mobile', icon: Smartphone, tone: 'bg-primary-soft text-primary' },
  { key: 'progress', icon: TrendingUp, tone: 'bg-success-soft text-success' },
  { key: 'reminders', icon: BellRing, tone: 'bg-warning-soft text-warning' },
];

export async function StudentBenefits() {
  const t = await getTranslations('landing.students');
  return (
    <LandingSection
      id={LANDING_ANCHORS.benefits}
      eyebrow={t('eyebrow')}
      title={t('title')}
      subtitle={t('subtitle')}
      className="bg-surface-muted"
    >
      <ul className="grid grid-cols-1 gap-gutter md:grid-cols-3">
        {STUDENT_BENEFITS.map(({ key, icon: Icon, tone }) => (
          <li
            key={key}
            className="flex flex-col gap-3 rounded-lg border border-card-border bg-surface p-6 shadow-sm md:p-8"
          >
            <span className={cn('flex size-12 items-center justify-center rounded-full', tone)}>
              <Icon className="size-6" aria-hidden />
            </span>
            <h3 className="text-lg">{t(`${key}.title`)}</h3>
            <p className="text-sm text-text-muted">{t(`${key}.text`)}</p>
          </li>
        ))}
      </ul>
    </LandingSection>
  );
}

/** Rendered only when real testimonials are configured in `src/content/landing.ts`. */
export async function Testimonials() {
  if (LANDING.testimonials.length === 0) return null;
  const t = await getTranslations('landing.testimonials');
  const locale = await getLocale();
  return (
    <LandingSection id={LANDING_ANCHORS.testimonials} eyebrow={t('eyebrow')} title={t('title')}>
      <ul className="grid grid-cols-1 gap-gutter md:grid-cols-3">
        {LANDING.testimonials.map((item) => (
          <li
            key={item.name}
            className="flex flex-col gap-4 rounded-lg border border-card-border bg-surface p-6 shadow-sm"
          >
            <span
              role="img"
              aria-label={t('stars', { stars: item.stars })}
              className="flex gap-0.5"
            >
              {Array.from({ length: MAX_STARS }, (_, index) => (
                <Star
                  key={index}
                  aria-hidden
                  className={cn(
                    'size-4',
                    index < item.stars
                      ? 'fill-warning-accent text-warning-accent'
                      : 'text-outline-variant',
                  )}
                />
              ))}
            </span>
            <blockquote className="flex-1 text-sm">«{localize(item.quote, locale)}»</blockquote>
            <p className="flex items-center gap-3">
              <Avatar name={item.name} />
              <span className="flex flex-col">
                <span className="font-semibold">{item.name}</span>
                <span className="text-xs text-text-muted">{localize(item.role, locale)}</span>
              </span>
            </p>
          </li>
        ))}
      </ul>
    </LandingSection>
  );
}

/** Pricing from `LANDING.plans`; every CTA leads to registration. */
export async function Pricing() {
  const t = await getTranslations('landing.pricing');
  const locale = await getLocale();
  return (
    <LandingSection
      id={LANDING_ANCHORS.pricing}
      eyebrow={t('eyebrow')}
      title={t('title')}
      subtitle={t('subtitle')}
    >
      <ul className="grid grid-cols-1 items-stretch gap-gutter lg:grid-cols-3">
        {LANDING.plans.map((plan) => (
          <li
            key={plan.id}
            className={cn(
              'relative flex flex-col gap-5 rounded-lg border bg-surface p-6 shadow-sm md:p-8',
              plan.highlighted
                ? 'border-accent shadow-md ring-2 ring-primary lg:-translate-y-2'
                : 'border-card-border',
            )}
          >
            {plan.highlighted && plan.badge ? (
              <span className="absolute -top-3.5 left-1/2 -translate-x-1/2 whitespace-nowrap rounded-full bg-primary px-4 py-1 text-label-md uppercase text-primary-foreground shadow-glow">
                {localize(plan.badge, locale)}
              </span>
            ) : null}
            <div className="flex flex-col gap-1">
              <h3 className="text-xl">{localize(plan.name, locale)}</h3>
              <p className="text-sm text-text-muted">{localize(plan.description, locale)}</p>
            </div>
            <p className="flex items-baseline gap-2">
              <span className="font-heading text-4xl font-bold tracking-tight">
                {plan.priceRub === 0 ? t('free') : formatRubles(plan.priceRub, locale)}
              </span>
              <span className="text-sm text-text-muted">/ {localize(plan.period, locale)}</span>
            </p>
            <ul className="flex flex-1 flex-col gap-2.5">
              {plan.features.map((feature) => (
                <li
                  key={feature.text.ru}
                  className={cn(
                    'flex items-start gap-2 text-sm',
                    !feature.included && 'text-text-muted',
                  )}
                >
                  {feature.included ? (
                    <Check
                      className="mt-0.5 size-4 shrink-0 text-success"
                      aria-label={t('included')}
                    />
                  ) : (
                    <X
                      className="mt-0.5 size-4 shrink-0 text-text-muted"
                      aria-label={t('notIncluded')}
                    />
                  )}
                  {localize(feature.text, locale)}
                </li>
              ))}
            </ul>
            <Button
              asChild
              size="lg"
              variant={plan.highlighted ? 'primary' : 'secondary'}
              className="w-full"
            >
              <Link href={ROUTES.register}>{plan.priceRub === 0 ? t('ctaFree') : t('cta')}</Link>
            </Button>
          </li>
        ))}
      </ul>
    </LandingSection>
  );
}

/** Final CTA on the violet gradient; the email pre-fills registration (plain GET form, works without JS). */
export async function FinalCta() {
  const t = await getTranslations('landing.finalCta');
  return (
    <section aria-labelledby="final-cta-title" className="px-page-x py-16 md:py-24">
      <div className="relative mx-auto flex max-w-5xl flex-col items-center gap-6 overflow-hidden rounded-xl bg-gradient-to-br from-primary via-primary to-accent px-6 py-14 text-center text-primary-foreground shadow-lg md:px-12">
        <div
          aria-hidden
          className="pointer-events-none absolute -right-20 -top-20 size-72 rounded-full bg-primary-foreground/10 blur-2xl"
        />
        <span className="relative inline-flex items-center gap-2 rounded-full bg-primary-foreground/15 px-4 py-1.5 text-label-md uppercase">
          <Zap className="size-4" aria-hidden /> {t('badge')}
        </span>
        <h2 id="final-cta-title" className="relative text-2xl text-primary-foreground md:text-4xl">
          {t('title')}
        </h2>
        <p className="relative max-w-xl text-base">{t('text')}</p>
        <form
          action={ROUTES.register}
          method="get"
          className="relative flex w-full max-w-md flex-col gap-2 rounded-lg bg-surface p-2 shadow-md sm:flex-row sm:rounded-full"
        >
          <label htmlFor="final-cta-email" className="sr-only">
            {t('emailLabel')}
          </label>
          <input
            id="final-cta-email"
            name="email"
            type="email"
            required
            autoComplete="email"
            placeholder={t('emailPlaceholder')}
            className="h-12 min-w-0 flex-1 rounded-full bg-transparent px-5 text-sm text-text placeholder:text-placeholder focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-focus-ring/20"
          />
          <Button type="submit" size="lg" className="h-12">
            {t('submit')} <Sparkles aria-hidden />
          </Button>
        </form>
      </div>
    </section>
  );
}
