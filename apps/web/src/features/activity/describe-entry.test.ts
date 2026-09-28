import { describe, expect, it } from 'vitest';
import { entryObjects, entryTarget, entryTone, isFailure, isSlow } from './describe-entry';

const request = {
  kind: 'request' as const,
  method: 'PATCH',
  route: '/api/v1/courses/{courseId}',
  path: '/api/v1/courses/c1',
  page: '/courses/c1',
  errorType: null,
  status: 200,
};

describe('describe-entry', () => {
  it('colours records by outcome', () => {
    expect(entryTone(request)).toBe('success');
    expect(entryTone({ ...request, status: 404 })).toBe('warning');
    expect(entryTone({ ...request, status: 503 })).toBe('danger');
    expect(entryTone({ kind: 'client_error', status: null })).toBe('danger');
    expect(entryTone({ kind: 'page_view', status: null })).toBe('info');
  });

  it('describes what was done', () => {
    expect(entryTarget(request)).toBe('PATCH /api/v1/courses/{courseId}');
    expect(entryTarget({ ...request, route: null })).toBe('PATCH /api/v1/courses/c1');
    expect(entryTarget({ ...request, kind: 'page_view' })).toBe('/courses/c1');
    expect(entryTarget({ ...request, kind: 'client_error', errorType: 'TypeError' })).toBe(
      'TypeError',
    );
  });

  it('flags failures, slow requests and lists touched objects', () => {
    expect(isFailure({ kind: 'request', status: 401 })).toBe(true);
    expect(isFailure(request)).toBe(false);
    expect(isSlow({ durationMs: 1500 })).toBe(true);
    expect(entryObjects({ pathParams: { courseId: 'c1' } })).toEqual(['courseId: c1']);
    expect(entryObjects({ pathParams: null })).toEqual([]);
  });
});
