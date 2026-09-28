import { beforeEach, describe, expect, it } from 'vitest';
import {
  MAX_RECENT_FORMULAS,
  readRecentFormulas,
  RECENT_FORMULAS_KEY,
  rememberFormula,
} from './recent-formulas';

describe('recent formulas', () => {
  beforeEach(() => window.localStorage.clear());

  it('keeps the newest first without duplicates', () => {
    rememberFormula('a');
    rememberFormula('b');
    expect(rememberFormula('a')).toEqual(['a', 'b']);
    expect(readRecentFormulas()).toEqual(['a', 'b']);
  });

  it('caps the list and ignores blanks', () => {
    for (let index = 0; index < MAX_RECENT_FORMULAS + 3; index += 1) rememberFormula(`x_${index}`);
    expect(rememberFormula('   ')).toHaveLength(MAX_RECENT_FORMULAS);
  });

  it('survives corrupted storage', () => {
    window.localStorage.setItem(RECENT_FORMULAS_KEY, '{oops');
    expect(readRecentFormulas()).toEqual([]);
  });
});
