import { describe, expect, it } from 'vitest';
import { comboOf, isEditableTarget } from '@/features/app/use-hotkeys';
import { parseEssayId } from '@/features/assessment/quick-comments';
import { safeNextPath } from '@/features/auth/routes';
import { localeFromAcceptLanguage } from '@/i18n/config';
import { parseHexColor, readableForeground } from './color';
import { monthGrid, rangeFor, startOfWeek } from './calendar';
import { formatMoney, toMinor } from './money';

describe('safeNextPath (open-redirect guard)', () => {
  it('allows only same-origin relative paths', () => {
    expect(safeNextPath('/courses/1?tab=x')).toBe('/courses/1?tab=x');
    expect(safeNextPath('//evil.com')).toBe('/home');
    expect(safeNextPath('https://evil.com')).toBe('/home');
    expect(safeNextPath('/\\evil.com')).toBe('/home');
    expect(safeNextPath(null)).toBe('/home');
  });
});

describe('branding colors (FR-ADMIN-01)', () => {
  it('parses hex and chooses a readable foreground', () => {
    expect(parseHexColor('#4F46E5')).toEqual([79, 70, 229]);
    expect(parseHexColor('fff')).toEqual([255, 255, 255]);
    expect(parseHexColor('red')).toBeNull();
    expect(readableForeground([79, 70, 229])).toEqual([255, 255, 255]);
    expect(readableForeground([250, 204, 21])).toEqual([15, 18, 30]);
  });
});

describe('money', () => {
  it('formats minor units per currency', () => {
    expect(formatMoney({ amountMinor: 500000, currency: 'RUB' }, 'ru').replace(/\s/g, ' ')).toBe(
      '5 000,00 ₽',
    );
    expect(toMinor(49.99, 'USD')).toBe(4999);
  });
});

describe('hotkeys (UX-11)', () => {
  it('normalizes combos and detects editable targets', () => {
    expect(comboOf({ key: 'J', ctrlKey: false, metaKey: false, altKey: false })).toBe('j');
    expect(comboOf({ key: 'Enter', ctrlKey: true, metaKey: false, altKey: false })).toBe(
      'mod+enter',
    );
    expect(comboOf({ key: 'Enter', ctrlKey: false, metaKey: true, altKey: false })).toBe(
      'mod+enter',
    );
    expect(isEditableTarget(document.createElement('textarea'))).toBe(true);
    expect(isEditableTarget(document.createElement('button'))).toBe(false);
  });

  it('parses essay queue ids', () => {
    expect(parseEssayId('0192-abc:3')).toEqual({ attemptId: '0192-abc', slot: 3 });
    expect(parseEssayId('bad')).toBeNull();
  });
});

describe('calendar grid', () => {
  it('starts weeks on Monday and covers 6 weeks', () => {
    expect(startOfWeek(new Date(2026, 8, 27)).getDay()).toBe(1);
    const grid = monthGrid(new Date(2026, 8, 15));
    expect(grid).toHaveLength(6);
    expect(grid[0]?.[0]?.getDay()).toBe(1);
    const { from, to } = rangeFor('week', new Date(2026, 8, 30));
    expect((to.getTime() - from.getTime()) / 86_400_000).toBeCloseTo(7, 0);
  });
});

describe('locale negotiation', () => {
  it('picks the first supported language', () => {
    expect(localeFromAcceptLanguage('de-DE,en;q=0.8,ru;q=0.5')).toBe('en');
    expect(localeFromAcceptLanguage('fr')).toBeNull();
  });
});
