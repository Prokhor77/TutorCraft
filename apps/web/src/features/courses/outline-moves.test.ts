import { describe, expect, it } from 'vitest';
import type { CourseOutline, OutlineItem, OutlineModule } from '@/lib/api/schemas/courses';
import {
  applyItemMove,
  computeItemMove,
  MODULE_DROP_PREFIX,
  moduleProgress,
  reorderModules,
  studentPreview,
} from './outline-moves';

const available = { available: true, mode: 'show_locked' as const, reasons: [] };
const item = (id: string, extra: Partial<OutlineItem> = {}): OutlineItem => ({
  id,
  type: 'page',
  title: id,
  position: 0,
  visibility: 'published',
  publishAt: null,
  dueAt: null,
  availability: available,
  completion: null,
  status: null,
  version: 1,
  ...extra,
});
const outlineModule = (
  id: string,
  items: OutlineItem[],
  extra: Partial<OutlineModule> = {},
): OutlineModule => ({
  id,
  parentId: null,
  title: id,
  position: 0,
  visibility: 'published',
  publishAt: null,
  availability: available,
  items,
  children: [],
  version: 1,
  ...extra,
});
const outline: CourseOutline = {
  courseId: 'c',
  modules: [
    outlineModule('m1', [item('a'), item('b'), item('c')]),
    outlineModule('m2', [item('d')]),
    outlineModule('m3', []),
  ],
};

describe('computeItemMove', () => {
  it('moves within a module to the index of the target item', () => {
    expect(computeItemMove(outline, 'a', 'c')).toEqual({
      itemId: 'a',
      moduleId: 'm1',
      position: 2,
    });
  });

  it('moves across modules', () => {
    expect(computeItemMove(outline, 'a', 'd')).toEqual({
      itemId: 'a',
      moduleId: 'm2',
      position: 0,
    });
  });

  it('drops into an empty module via its drop zone', () => {
    expect(computeItemMove(outline, 'b', `${MODULE_DROP_PREFIX}m3`)).toEqual({
      itemId: 'b',
      moduleId: 'm3',
      position: 0,
    });
  });

  it('ignores no-op moves', () => {
    expect(computeItemMove(outline, 'a', 'a')).toBeNull();
    expect(computeItemMove(outline, 'missing', 'a')).toBeNull();
  });
});

describe('applyItemMove', () => {
  it('reorders optimistically and renumbers positions', () => {
    const moved = applyItemMove(outline, { itemId: 'a', moduleId: 'm2', position: 1 });
    expect(moved.modules[0]?.items.map((entry) => entry.id)).toEqual(['b', 'c']);
    expect(moved.modules[1]?.items.map((entry) => [entry.id, entry.position])).toEqual([
      ['d', 0],
      ['a', 1],
    ]);
  });
});

describe('reorderModules', () => {
  it('returns the new order and target position', () => {
    const result = reorderModules(outline, 'm3', 'm1');
    expect(result?.position).toBe(0);
    expect(result?.outline.modules.map((entry) => entry.id)).toEqual(['m3', 'm1', 'm2']);
    expect(reorderModules(outline, 'm1', 'm1')).toBeNull();
  });
});

describe('moduleProgress & studentPreview', () => {
  it('counts only trackable items, including submodules', () => {
    const tracked = outlineModule(
      'p',
      [item('x', { completion: 'complete' }), item('y', { completion: 'incomplete' }), item('z')],
      { children: [outlineModule('child', [item('w', { completion: 'complete' })])] },
    );
    expect(moduleProgress(tracked)).toEqual({ done: 2, total: 3 });
  });

  it('hides non-published modules and items in "view as student"', () => {
    const mixed: CourseOutline = {
      courseId: 'c',
      modules: [
        outlineModule('m1', [item('a'), item('h', { visibility: 'hidden' })]),
        outlineModule('m2', [], { visibility: 'scheduled' }),
      ],
    };
    const preview = studentPreview(mixed);
    expect(preview.modules.map((entry) => entry.id)).toEqual(['m1']);
    expect(preview.modules[0]?.items.map((entry) => entry.id)).toEqual(['a']);
  });
});
