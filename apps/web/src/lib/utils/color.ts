/** Branding helpers (FR-ADMIN-01): convert tenant hex color to token channels and pick a readable foreground. */
const HEX_COLOR = /^#?([0-9a-f]{3}|[0-9a-f]{6})$/i;
const WCAG_LUMINANCE_THRESHOLD = 0.179;
const SOFT_MIX_RATIO = 0.88;

export type Rgb = [number, number, number];

export function parseHexColor(value: string | null | undefined): Rgb | null {
  if (!value) return null;
  const match = HEX_COLOR.exec(value.trim());
  if (!match?.[1]) return null;
  const hex = match[1].length === 3 ? [...match[1]].map((c) => c + c).join('') : match[1];
  return [0, 2, 4].map((offset) => parseInt(hex.slice(offset, offset + 2), 16)) as Rgb;
}

function channelLuminance(channel: number): number {
  const srgb = channel / 255;
  return srgb <= 0.03928 ? srgb / 12.92 : ((srgb + 0.055) / 1.055) ** 2.4;
}

export function relativeLuminance([r, g, b]: Rgb): number {
  return 0.2126 * channelLuminance(r) + 0.7152 * channelLuminance(g) + 0.0722 * channelLuminance(b);
}

export function readableForeground(rgb: Rgb): Rgb {
  return relativeLuminance(rgb) > WCAG_LUMINANCE_THRESHOLD ? [15, 18, 30] : [255, 255, 255];
}

export function mixWithWhite([r, g, b]: Rgb, ratio = SOFT_MIX_RATIO): Rgb {
  return [r, g, b].map((channel) => Math.round(channel + (255 - channel) * ratio)) as Rgb;
}

export const toChannels = (rgb: Rgb) => rgb.join(' ');

export const BRANDING_CSS_VARS = {
  primary: '--primary',
  foreground: '--primary-foreground',
  soft: '--primary-soft',
  focus: '--focus-ring',
} as const;

/** Applies (or clears when color is null) tenant primary color on :root. */
export function applyBrandColor(
  color: string | null,
  root: HTMLElement = document.documentElement,
): void {
  const rgb = parseHexColor(color);
  const vars = Object.values(BRANDING_CSS_VARS);
  if (!rgb) {
    vars.forEach((name) => root.style.removeProperty(name));
    return;
  }
  root.style.setProperty(BRANDING_CSS_VARS.primary, toChannels(rgb));
  root.style.setProperty(BRANDING_CSS_VARS.foreground, toChannels(readableForeground(rgb)));
  root.style.setProperty(BRANDING_CSS_VARS.soft, toChannels(mixWithWhite(rgb)));
  root.style.setProperty(BRANDING_CSS_VARS.focus, toChannels(rgb));
}
