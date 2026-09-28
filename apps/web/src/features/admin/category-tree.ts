import type { Category } from '@/lib/api/schemas/org';

export type CategoryNode = Category & { children: CategoryNode[]; depth: number };

/** Builds a sorted tree from the flat `Category[]` (parentId + position). Orphans go to the root. */
export function buildCategoryTree(categories: Category[]): CategoryNode[] {
  const ids = new Set(categories.map((category) => category.id));
  const byParent = new Map<string | null, Category[]>();
  for (const category of categories) {
    const parent = category.parentId && ids.has(category.parentId) ? category.parentId : null;
    byParent.set(parent, [...(byParent.get(parent) ?? []), category]);
  }
  const build = (parentId: string | null, depth: number): CategoryNode[] =>
    (byParent.get(parentId) ?? [])
      .sort((a, b) => a.position - b.position)
      .map((category) => ({ ...category, depth, children: build(category.id, depth + 1) }));
  return build(null, 0);
}

/** Descendant ids (a category cannot be moved into its own subtree). */
export function descendantIds(categories: Category[], id: string): Set<string> {
  const result = new Set<string>();
  const walk = (parentId: string) => {
    for (const category of categories) {
      if (category.parentId === parentId && !result.has(category.id)) {
        result.add(category.id);
        walk(category.id);
      }
    }
  };
  walk(id);
  return result;
}
