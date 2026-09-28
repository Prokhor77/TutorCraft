import { clsx, type ClassValue } from 'clsx';
import { extendTailwindMerge } from 'tailwind-merge';

/**
 * Custom font-size tokens from tailwind.config.ts (Stitch type scale). tailwind-merge must know them, otherwise
 * `text-label-md text-text-muted` is read as two text colors and the size is silently dropped.
 */
const CUSTOM_FONT_SIZES = ['hero-mobile', 'label-lg', 'label-md', 'label-sm'] as const;

const twMerge = extendTailwindMerge({
  extend: { classGroups: { 'font-size': [{ text: [...CUSTOM_FONT_SIZES] }] } },
});

export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
