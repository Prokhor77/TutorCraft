import type { Money } from '@/lib/api/schemas/common';

const DEFAULT_MINOR_DIGITS = 2;

function minorDigits(currency: string): number {
  try {
    return (
      new Intl.NumberFormat('en', { style: 'currency', currency }).resolvedOptions()
        .maximumFractionDigits ?? DEFAULT_MINOR_DIGITS
    );
  } catch {
    return DEFAULT_MINOR_DIGITS;
  }
}

/** Subscription amounts: 3000 USD → "$30.00" (en), 500000 RUB → "5 000,00 ₽" (ru). */
export function formatMoney(money: Money, locale: string): string {
  const digits = minorDigits(money.currency);
  const major = money.amountMinor / 10 ** digits;
  return new Intl.NumberFormat(locale, { style: 'currency', currency: money.currency }).format(
    major,
  );
}
