import { describe, expect, it } from 'vitest';
import { LANDING } from '@/content/landing';
import { pricePerMonth, savingPercent } from './term-pricing';

const TERMS = [
  { price: 40, months: 1 },
  { price: 120, months: 3 },
  { price: 240, months: 12 },
];

describe('subscription term pricing', () => {
  it('derives the monthly price of each term', () => {
    expect(TERMS.map(pricePerMonth)).toEqual([40, 40, 20]);
  });

  it('computes the saving against the shortest term', () => {
    expect(TERMS.map((term) => savingPercent(term, TERMS))).toEqual([0, 0, 50]);
    expect(savingPercent(TERMS[2]!, [])).toBe(0);
  });

  it('keeps the landing plans in sync with core-api SubscriptionTerm (30 / 75 / 150 USD)', () => {
    const plans = LANDING.pricing.plans;
    expect(plans.map((plan) => [plan.id, plan.months, plan.price])).toEqual([
      ['month', 1, 30],
      ['quarter', 3, 75],
      ['year', 12, 150],
    ]);
    expect(plans.map((plan) => savingPercent(plan, plans))).toEqual([0, 17, 58]);
  });
});
