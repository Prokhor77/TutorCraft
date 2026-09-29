import type { OutlineModule } from '@/lib/api/schemas/courses';

const pad = (value: number) => String(value).padStart(2, '0');

/** Value of <input type="date"> for a local calendar day. */
export function toDateInputValue(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** Value of <input type="time"> (HH:mm) for a local time. */
export function toTimeInputValue(date: Date): string {
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** Local date + time inputs → ISO instant; null when either part is missing or invalid. */
export function combineLocal(date: string, time: string): string | null {
  if (!date || !time) return null;
  const result = new Date(`${date}T${time}`);
  return Number.isNaN(result.getTime()) ? null : result.toISOString();
}

/** HH:mm strings compare lexicographically. */
export function isTimeBefore(end: string, start: string): boolean {
  return Boolean(end && start && end < start);
}

export type ModuleOption = { id: string; title: string; depth: number };
export type ItemOption = { id: string; title: string; moduleId: string };

/** Course outline → flat module list (with nesting depth) and items with their direct module. */
export function flattenOutline(modules: OutlineModule[]): {
  modules: ModuleOption[];
  items: ItemOption[];
} {
  const result = { modules: [] as ModuleOption[], items: [] as ItemOption[] };
  const visit = (module: OutlineModule, depth: number) => {
    result.modules.push({ id: module.id, title: module.title, depth });
    for (const item of module.items) {
      result.items.push({ id: item.id, title: item.title, moduleId: module.id });
    }
    module.children.forEach((child) => visit(child, depth + 1));
  };
  [...modules].sort((a, b) => a.position - b.position).forEach((module) => visit(module, 0));
  return result;
}
