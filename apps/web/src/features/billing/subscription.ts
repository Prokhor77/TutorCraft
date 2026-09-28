import type { Subscription } from '@/lib/api/schemas/billing';
import { pricePerMonth, savingPercent, type PricedTerm } from '@/lib/utils/term-pricing';
import { MS_PER_DAY } from '@/lib/utils/time';

/** Show the renewal reminder this many days before access ends. */
export const RENEWAL_WARNING_DAYS = 3;

/** Whole days of access left (rounded up; 0 once access has ended). */
export function daysLeft(accessUntil: string, now: Date = new Date()): number {
  const remaining = new Date(accessUntil).getTime() - now.getTime();
  return remaining <= 0 ? 0 : Math.ceil(remaining / MS_PER_DAY);
}

export type SubscriptionNotice = { kind: 'expired' } | { kind: 'endingSoon'; days: number };

/** App-wide banner: access has ended, or it ends within RENEWAL_WARNING_DAYS. Null — nothing to say. */
export function subscriptionNotice(
  subscription: Pick<Subscription, 'status' | 'accessUntil'>,
  now: Date = new Date(),
): SubscriptionNotice | null {
  if (subscription.status === 'expired') return { kind: 'expired' };
  const days = daysLeft(subscription.accessUntil, now);
  return days <= RENEWAL_WARNING_DAYS ? { kind: 'endingSoon', days } : null;
}

type Term = Subscription['terms'][number];

const asPricedTerm = (term: Term): PricedTerm => ({
  price: term.price.amountMinor,
  months: term.months,
});

/** Effective price per month in minor units (a year for 24000 → 2000). */
export function monthlyPriceMinor(term: Term): number {
  return Math.round(pricePerMonth(asPricedTerm(term)));
}

/** Whole-percent saving of `term` against the monthly term; 0 when there is none. */
export function termSavingPercent(term: Term, terms: readonly Term[]): number {
  return savingPercent(asPricedTerm(term), terms.map(asPricedTerm));
}
