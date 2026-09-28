import { describe, expect, it } from 'vitest';
import { MASKED_SEGMENT, sanitizePagePath } from './page-path';

describe('sanitizePagePath', () => {
  it('drops query strings and fragments', () => {
    expect(sanitizePagePath('/reset-password?token=abc#top')).toBe('/reset-password');
  });

  it('masks invite tokens and long opaque segments but keeps ids', () => {
    expect(sanitizePagePath('/join/abc123')).toBe(`/join/${MASKED_SEGMENT}`);
    expect(sanitizePagePath(`/x/${'a'.repeat(48)}`)).toBe(`/x/${MASKED_SEGMENT}`);
    const id = '01a0e898-99ce-7252-b8f6-18910177e41a';
    expect(sanitizePagePath(`/courses/${id}`)).toBe(`/courses/${id}`);
  });

  it('caps the length and falls back to root', () => {
    expect(sanitizePagePath(`/${'ab/'.repeat(200)}`)).toHaveLength(300);
    expect(sanitizePagePath('')).toBe('/');
    expect(sanitizePagePath('relative/path')).toBe('/');
  });
});
