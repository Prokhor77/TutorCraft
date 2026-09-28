/** A subscription term priced for the whole period (any unit: whole dollars or minor units). */
export type PricedTerm = { price: number; months: number };

const PERCENT = 100;

/** Effective price per month of a term. */
export function pricePerMonth(term: PricedTerm): number {
  return term.price / term.months;
}

/**
 * Whole-percent saving of `term` against the shortest term's monthly price
 * (12 months for 240 vs 40/month → 50). Returns 0 when there is no saving or no terms.
 */
export function savingPercent(term: PricedTerm, terms: readonly PricedTerm[]): number {
  const shortest = terms.reduce<PricedTerm | null>(
    (best, candidate) => (best && best.months <= candidate.months ? best : candidate),
    null,
  );
  if (!shortest) return 0;
  const baseMonthly = pricePerMonth(shortest);
  if (baseMonthly <= 0) return 0;
  return Math.max(0, Math.round((1 - pricePerMonth(term) / baseMonthly) * PERCENT));
}
