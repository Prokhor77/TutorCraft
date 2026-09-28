import { describe, expect, it } from 'vitest';
import { daysLeft, monthlyPriceMinor, subscriptionNotice, termSavingPercent } from './subscription';

const NOW = new Date('2026-09-28T10:00:00Z');
const usd = (amountMinor: number) => ({ amountMinor, currency: 'USD' });
const TERMS = [
  { term: 'month' as const, months: 1, price: usd(4000) },
  { term: 'quarter' as const, months: 3, price: usd(12000) },
  { term: 'year' as const, months: 12, price: usd(24000) },
];

describe('school subscription helpers', () => {
  it('counts whole days of access left', () => {
    expect(daysLeft('2026-10-12T10:00:00Z', NOW)).toBe(14);
    expect(daysLeft('2026-09-28T11:00:00Z', NOW)).toBe(1);
    expect(daysLeft('2026-09-27T10:00:00Z', NOW)).toBe(0);
  });

  it('warns only when access ended or ends within three days', () => {
    expect(
      subscriptionNotice({ status: 'expired', accessUntil: '2026-09-27T10:00:00Z' }, NOW),
    ).toEqual({
      kind: 'expired',
    });
    expect(
      subscriptionNotice({ status: 'trial', accessUntil: '2026-09-30T10:00:00Z' }, NOW),
    ).toEqual({
      kind: 'endingSoon',
      days: 2,
    });
    expect(
      subscriptionNotice({ status: 'active', accessUntil: '2026-10-28T10:00:00Z' }, NOW),
    ).toBeNull();
  });

  it('derives the monthly price and the saving against the monthly term', () => {
    expect(TERMS.map(monthlyPriceMinor)).toEqual([4000, 4000, 2000]);
    expect(TERMS.map((term) => termSavingPercent(term, TERMS))).toEqual([0, 0, 50]);
    expect(termSavingPercent(TERMS[2]!, [])).toBe(0);
  });
});
