'use client';
import { useEffect, useRef } from 'react';

export type HotkeyMap = Record<string, (event: KeyboardEvent) => void>;

/** True when focus is in a text field, where plain-letter shortcuts must not fire. */
export function isEditableTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  return target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName);
}

/** Normalizes a keyboard event to "mod+enter" / "j" / "?" style combos ("mod" = Ctrl or ⌘). */
export function comboOf(
  event: Pick<KeyboardEvent, 'key' | 'ctrlKey' | 'metaKey' | 'altKey'>,
): string {
  const key = event.key.length === 1 ? event.key.toLowerCase() : event.key.toLowerCase();
  const mod = event.ctrlKey || event.metaKey ? 'mod+' : '';
  const alt = event.altKey ? 'alt+' : '';
  return `${mod}${alt}${key}`;
}

/** UX-11 shortcuts. Plain keys are ignored while typing; modifier combos always work. */
export function useHotkeys(map: HotkeyMap, enabled = true): void {
  const mapRef = useRef(map);
  mapRef.current = map;
  useEffect(() => {
    if (!enabled) return;
    const onKeyDown = (event: KeyboardEvent) => {
      const combo = comboOf(event);
      const handler = mapRef.current[combo];
      if (!handler) return;
      if (!combo.startsWith('mod+') && isEditableTarget(event.target)) return;
      event.preventDefault();
      handler(event);
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [enabled]);
}
