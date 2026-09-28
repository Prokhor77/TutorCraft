import type { LandingCurrency } from '@/content/landing';

/** Whole-unit amount with the currency code, no fractions: «40 USD», «2 240 BYN» (ru) / «USD 40» (en). */
export function formatLandingPrice(
  amount: number,
  currency: LandingCurrency,
  locale: string,
): string {
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency,
    currencyDisplay: 'code',
    maximumFractionDigits: 0,
  }).format(amount);
}
