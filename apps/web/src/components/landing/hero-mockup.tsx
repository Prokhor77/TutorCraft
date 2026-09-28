import { BookOpen, CheckCircle2, CloudCheck, FileText, Lock, Send, Timer } from 'lucide-react';
import { getTranslations } from 'next-intl/server';
import { Avatar } from '@/components/ui/avatar';
import { Badge } from '@/components/ui/badge';
import { cn } from '@/lib/utils/cn';

const RING_PERCENT = 76;
const RING_RADIUS = 36;
const FULL_PERCENT = 100;
const RING_CIRCUMFERENCE = 2 * Math.PI * RING_RADIUS;

function Panel({
  title,
  className,
  children,
}: {
  title: string;
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <div
      className={cn(
        'flex min-w-0 flex-col gap-3 rounded-md border border-card-border bg-surface p-4 shadow-sm',
        className,
      )}
    >
      <p className="font-heading text-sm font-semibold">{title}</p>
      {children}
    </div>
  );
}

/**
 * Hero product mockup (Stitch #demo): a static illustration built from real UI primitives with demo data.
 * It is `inert` (not focusable, hidden from assistive tech); the figure caption describes it instead.
 */
export async function HeroMockup() {
  const t = await getTranslations('landing.mockup');
  const queue = [
    { name: t('queue1'), when: t('new'), tone: 'success' as const },
    { name: t('queue2'), when: t('ago10'), tone: 'warning' as const },
    { name: t('queue3'), when: t('ago1h'), tone: 'neutral' as const },
  ];
  return (
    <figure className="flex flex-col gap-3" aria-label={t('label')}>
      <div
        inert
        aria-hidden
        className="overflow-hidden rounded-xl border border-card-border bg-surface-muted shadow-lg"
      >
        <div className="flex items-center gap-3 border-b border-card-border bg-surface px-4 py-3">
          <span className="flex gap-1.5">
            <span className="size-3 rounded-full bg-danger/70" />
            <span className="size-3 rounded-full bg-warning-accent" />
            <span className="size-3 rounded-full bg-success-accent" />
          </span>
          <span className="hidden min-w-0 flex-1 truncate rounded-full bg-surface-muted px-4 py-1 text-xs text-text-muted sm:block">
            {t('address')}
          </span>
          <Badge tone="success" dot className="ml-auto">
            <CloudCheck aria-hidden /> {t('synced')}
          </Badge>
        </div>
        <div className="grid grid-cols-1 gap-3 p-3 md:grid-cols-12 md:p-4">
          <Panel title={t('modules')} className="md:col-span-3">
            <ul className="flex flex-col gap-1.5 text-xs">
              {[
                { title: t('module1'), badge: t('lessons'), icon: BookOpen, active: true },
                { title: t('module2'), badge: t('homework'), icon: FileText },
                { title: t('module3'), icon: Lock, locked: true },
              ].map((row) => (
                <li
                  key={row.title}
                  className={cn(
                    'relative flex items-center gap-2 rounded-full px-3 py-2',
                    row.active ? 'bg-accent/10 font-semibold text-primary' : 'text-text',
                    row.locked && 'text-text-muted',
                  )}
                >
                  {row.active ? (
                    <span className="absolute inset-y-2 left-0 w-1 rounded-full bg-primary" />
                  ) : null}
                  <row.icon className="size-3.5 shrink-0" />
                  <span className="min-w-0 flex-1 truncate">{row.title}</span>
                  {row.badge ? (
                    <span className="shrink-0 rounded-full bg-primary-soft px-2 text-label-sm text-primary">
                      {row.badge}
                    </span>
                  ) : null}
                </li>
              ))}
            </ul>
            <p className="mt-auto flex items-center gap-1.5 text-label-sm text-success">
              <CheckCircle2 className="size-3.5" /> {t('savedAt')}
            </p>
          </Panel>
          <Panel title={t('review')} className="md:col-span-6">
            <div className="flex items-center gap-2">
              <Avatar name={t('student')} size="sm" />
              <span className="min-w-0 flex-1 truncate text-sm font-semibold">{t('student')}</span>
              <Badge tone="primary">{t('score')}</Badge>
            </div>
            <div className="rounded bg-surface-muted p-3 text-xs">
              <p className="font-semibold">{t('task')}</p>
              <p className="mt-1 font-mono text-sm text-primary">{t('formula')}</p>
              <p className="mt-1 text-text-muted">{t('answer')}</p>
            </div>
            <p className="rounded bg-success-soft p-3 text-xs text-success">{t('comment')}</p>
            <div className="flex flex-wrap items-center gap-2">
              <span className="inline-flex h-8 items-center rounded-full bg-primary px-4 text-label-md text-primary-foreground shadow-glow">
                {t('accept')}
              </span>
              <span className="flex items-center gap-1 text-label-sm text-text-muted">
                <Send className="size-3" /> {t('sent')}
              </span>
            </div>
          </Panel>
          <Panel title={t('progress')} className="md:col-span-3">
            <div className="flex items-center gap-3">
              <svg viewBox="0 0 88 88" className="size-20 shrink-0 -rotate-90">
                <circle
                  cx="44"
                  cy="44"
                  r={RING_RADIUS}
                  fill="none"
                  strokeWidth="8"
                  className="stroke-surface-container"
                />
                <circle
                  cx="44"
                  cy="44"
                  r={RING_RADIUS}
                  fill="none"
                  strokeWidth="8"
                  strokeLinecap="round"
                  className="stroke-success-accent"
                  strokeDasharray={RING_CIRCUMFERENCE}
                  strokeDashoffset={RING_CIRCUMFERENCE * (1 - RING_PERCENT / FULL_PERCENT)}
                />
              </svg>
              <span className="flex flex-col">
                <span className="font-heading text-2xl font-bold">{RING_PERCENT}%</span>
                <span className="text-label-sm text-text-muted">{t('ring')}</span>
              </span>
            </div>
            <p className="flex items-center gap-1.5 text-label-md">
              <Timer className="size-3.5 text-warning" /> {t('queue')}
            </p>
            <ul className="flex flex-col gap-2">
              {queue.map((entry) => (
                <li key={entry.name} className="flex items-center gap-2 text-xs">
                  <Avatar name={entry.name} size="sm" />
                  <span className="min-w-0 flex-1 truncate">{entry.name}</span>
                  <Badge tone={entry.tone} className="text-label-sm">
                    {entry.when}
                  </Badge>
                </li>
              ))}
            </ul>
          </Panel>
        </div>
      </div>
      <figcaption className="text-center text-xs text-text-muted">{t('caption')}</figcaption>
    </figure>
  );
}
