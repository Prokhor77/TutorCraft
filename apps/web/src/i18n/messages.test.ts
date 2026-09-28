import { describe, expect, it } from 'vitest';
import en from '../../messages/en.json';
import ru from '../../messages/ru.json';

function flatten(object: Record<string, unknown>, prefix = ''): string[] {
  return Object.entries(object).flatMap(([key, value]) =>
    value && typeof value === 'object'
      ? flatten(value as Record<string, unknown>, `${prefix}${key}.`)
      : [`${prefix}${key}`],
  );
}

describe('messages (NFR-I18N-01)', () => {
  it('ru and en have identical key sets', () => {
    expect(flatten(en).sort()).toEqual(flatten(ru).sort());
  });

  it('has no empty translations', () => {
    const values = (object: Record<string, unknown>): unknown[] =>
      Object.values(object).flatMap((value) =>
        value && typeof value === 'object' ? values(value as Record<string, unknown>) : [value],
      );
    const emptyAllowed = new Set<string>();
    for (const catalog of [ru, en])
      expect(
        values(catalog).filter((value) => value === '' && !emptyAllowed.has(String(value))),
      ).toEqual([]);
  });
});
