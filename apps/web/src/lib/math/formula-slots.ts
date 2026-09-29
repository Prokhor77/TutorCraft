/**
 * Empty template slots ("поля шаблона") in the formula editor.
 *
 * Three notations meet here:
 * - palette templates use MathLive placeholders: `#0` — the current selection or the first slot,
 *   `#?`/`#@`/`#1…` — an empty slot;
 * - the LaTeX source pane shows a slot as {@link EMPTY_SLOT} (`□`): short, readable, and KaTeX renders
 *   it as a box, so `\frac{□}{□}` is visible before anything is typed;
 * - the visual pane (MathLive) needs `\placeholder{}` to draw an editable slot.
 *
 * Slots are never saved: {@link stripSlots} removes them on submit.
 */
export const EMPTY_SLOT = '□';

const TEMPLATE_SELECTION = '#0';
const TEMPLATE_PLACEHOLDER_PATTERN = /#[0-9?@]/g;
const FIELD_PLACEHOLDER = '\\placeholder{}';
const FIELD_PLACEHOLDER_PATTERN = /\\placeholder\s*(?:\[[^\]]*\])?\s*\{\s*\}/g;
const EMPTY_SLOT_PATTERN = new RegExp(EMPTY_SLOT, 'g');
/**
 * An empty `{}` that is a required argument: right after a command (`\\frac{}`), `^`/`_`, or the
 * previous argument (`\\frac{a}{}`). A bare `{}` (as in `{}^{14}C`) is left alone.
 */
const EMPTY_ARGUMENT_PATTERN = /(?<=(?:\\(?!placeholder\b)[a-zA-Z]+\*?|[\^_}\]])\s*)\{\s*\}/g;

/** Palette template → LaTeX source; `#0` takes the selected text when there is one. */
export function templateToSource(template: string, selection = ''): string {
  return template
    .split(TEMPLATE_SELECTION)
    .map((part) => part.replace(TEMPLATE_PLACEHOLDER_PATTERN, EMPTY_SLOT))
    .join(selection || EMPTY_SLOT);
}

/**
 * LaTeX source → value for the MathLive field. Hand-typed empty arguments become slots too,
 * otherwise MathLive draws nothing for `\\frac{}{}`.
 */
export function sourceToField(source: string): string {
  return source
    .replace(EMPTY_SLOT_PATTERN, FIELD_PLACEHOLDER)
    .replace(EMPTY_ARGUMENT_PATTERN, `{${FIELD_PLACEHOLDER}}`);
}

/** MathLive field value → LaTeX source. */
export function fieldToSource(value: string): string {
  return value.replace(FIELD_PLACEHOLDER_PATTERN, EMPTY_SLOT);
}

/** Removes unfilled slots before the formula is saved. */
export function stripSlots(source: string): string {
  return source.replace(EMPTY_SLOT_PATTERN, '');
}

export type SlotDirection = 'forward' | 'backward';

/**
 * Index of the slot Tab/Shift+Tab should select, starting from the current selection
 * (`from` = selectionEnd for forward, selectionStart for backward); wraps around. -1 if none.
 */
export function findSlot(source: string, from: number, direction: SlotDirection): number {
  if (direction === 'forward') {
    const next = source.indexOf(EMPTY_SLOT, from);
    return next !== -1 ? next : source.indexOf(EMPTY_SLOT);
  }
  const previous = from > 0 ? source.lastIndexOf(EMPTY_SLOT, from - 1) : -1;
  return previous !== -1 ? previous : source.lastIndexOf(EMPTY_SLOT);
}
