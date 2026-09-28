import { describe, expect, it } from 'vitest';
import { formatLandingPrice } from './money';

/** Intl separates groups/code with (narrow) no-break spaces; normalise for readable assertions. */
const plain = (text: string) => text.replace(/[  ]/g, ' ');

describe('landing money formatting', () => {
  it('shows whole amounts with the currency code', () => {
    expect(plain(formatLandingPrice(40, 'USD', 'ru'))).toBe('40 USD');
    expect(plain(formatLandingPrice(2240, 'BYN', 'ru'))).toBe('2 240 BYN');
    expect(plain(formatLandingPrice(240, 'USD', 'en'))).toBe('USD 240');
  });
});
