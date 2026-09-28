import type { CourseOutline, OutlineItem, OutlineModule } from '@/lib/api/schemas/courses';

export const MODULE_DROP_PREFIX = 'module-drop:';

export type ItemMove = { itemId: string; moduleId: string; position: number };

function allModules(modules: OutlineModule[]): OutlineModule[] {
  return modules.flatMap((module) => [module, ...allModules(module.children)]);
}

function moduleOfItem(outline: CourseOutline, itemId: string): OutlineModule | undefined {
  return allModules(outline.modules).find((module) =>
    module.items.some((item) => item.id === itemId),
  );
}

/**
 * Resolves a drag end (active item dropped over another item or an empty module drop zone)
 * into the target module and 0-based position used by POST /items/{id}/move.
 */
export function computeItemMove(
  outline: CourseOutline,
  activeId: string,
  overId: string,
): ItemMove | null {
  if (activeId === overId) return null;
  const source = moduleOfItem(outline, activeId);
  if (!source) return null;
  if (overId.startsWith(MODULE_DROP_PREFIX)) {
    const moduleId = overId.slice(MODULE_DROP_PREFIX.length);
    const target = allModules(outline.modules).find((module) => module.id === moduleId);
    if (!target) return null;
    const position = target.items.filter((item) => item.id !== activeId).length;
    return target.id === source.id &&
      position === source.items.findIndex((item) => item.id === activeId)
      ? null
      : { itemId: activeId, moduleId, position };
  }
  const target = moduleOfItem(outline, overId);
  if (!target) return null;
  const overIndex = target.items.findIndex((item) => item.id === overId);
  return { itemId: activeId, moduleId: target.id, position: overIndex };
}

/** Optimistic local application of an ItemMove (mirrors the server reorder). */
export function applyItemMove(outline: CourseOutline, move: ItemMove): CourseOutline {
  let moving: OutlineItem | undefined;
  const without = mapAll(outline.modules, (module) => {
    const found = module.items.find((item) => item.id === move.itemId);
    if (found) moving = found;
    return { ...module, items: module.items.filter((item) => item.id !== move.itemId) };
  });
  if (!moving) return outline;
  const inserted = mapAll(without, (module) => {
    if (module.id !== move.moduleId) return module;
    const items = [...module.items];
    items.splice(Math.min(move.position, items.length), 0, moving as OutlineItem);
    return { ...module, items: items.map((item, index) => ({ ...item, position: index })) };
  });
  return { ...outline, modules: inserted };
}

export function reorderModules(
  outline: CourseOutline,
  activeId: string,
  overId: string,
): { outline: CourseOutline; position: number } | null {
  const from = outline.modules.findIndex((module) => module.id === activeId);
  const to = outline.modules.findIndex((module) => module.id === overId);
  if (from === -1 || to === -1 || from === to) return null;
  const modules = [...outline.modules];
  const [moved] = modules.splice(from, 1);
  modules.splice(to, 0, moved as OutlineModule);
  return {
    outline: {
      ...outline,
      modules: modules.map((module, index) => ({ ...module, position: index })),
    },
    position: to,
  };
}

function mapAll(
  modules: OutlineModule[],
  fn: (module: OutlineModule) => OutlineModule,
): OutlineModule[] {
  return modules.map((module) => fn({ ...module, children: mapAll(module.children, fn) }));
}

/** Module progress for the sidebar: completed / trackable items. */
export function moduleProgress(module: OutlineModule): { done: number; total: number } {
  const items = allModules([module]).flatMap((entry) => entry.items);
  const trackable = items.filter((item) => item.completion !== null);
  return {
    done: trackable.filter((item) => item.completion === 'complete').length,
    total: trackable.length,
  };
}

/** Author readiness (Stitch «Готовность»): published items out of all items of the modules. */
export function publishedProgress(modules: OutlineModule[]): { done: number; total: number } {
  const items = allModules(modules).flatMap((entry) => entry.items);
  return {
    done: items.filter((item) => item.visibility === 'published').length,
    total: items.length,
  };
}

/** Tree search: keeps items whose title contains the query (case-insensitive) and their modules. */
export function filterOutlineByTitle(modules: OutlineModule[], query: string): OutlineModule[] {
  const needle = query.trim().toLocaleLowerCase();
  if (!needle) return modules;
  const keep = (list: OutlineModule[]): OutlineModule[] =>
    list
      .map((module) => {
        const moduleMatches = module.title.toLocaleLowerCase().includes(needle);
        return {
          ...module,
          items: moduleMatches
            ? module.items
            : module.items.filter((item) => item.title.toLocaleLowerCase().includes(needle)),
          children: keep(module.children),
        };
      })
      .filter(
        (module) =>
          module.title.toLocaleLowerCase().includes(needle) ||
          module.items.length > 0 ||
          module.children.length > 0,
      );
  return keep(modules);
}

/** Client-side "view as student" filter (FR-ACL-05 preview): drop non-published content. */
export function studentPreview(outline: CourseOutline): CourseOutline {
  const visible = (modules: OutlineModule[]): OutlineModule[] =>
    modules
      .filter((module) => module.visibility === 'published')
      .map((module) => ({
        ...module,
        items: module.items.filter((item) => item.visibility === 'published'),
        children: visible(module.children),
      }));
  return { ...outline, modules: visible(outline.modules) };
}
