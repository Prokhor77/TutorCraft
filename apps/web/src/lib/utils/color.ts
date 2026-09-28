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

const BRAND_STYLE_ID = 'tc-brand';
/** Dark theme lightens the tenant color so it stays readable on navy surfaces (Material dark pattern). */
const DARK_LIGHTEN_RATIO = 0.45;
const DARK_SOFT: Rgb = [40, 42, 96];
const DARK_THEME_SELECTORS = ":root[data-theme='dark']";

function brandVars(primary: Rgb, soft: Rgb): string {
  const vars = {
    '--primary': primary,
    '--primary-foreground': readableForeground(primary),
    '--primary-soft': soft,
    '--accent': primary,
    '--focus-ring': primary,
  };
  return Object.entries(vars)
    .map(([name, rgb]) => `${name}: ${toChannels(rgb)};`)
    .join(' ');
}

/** CSS overriding the violet role tokens with the tenant color, for light and dark themes. */
export function buildBrandCss(color: string | null): string {
  const rgb = parseHexColor(color);
  if (!rgb) return '';
  const light = brandVars(rgb, mixWithWhite(rgb));
  const dark = brandVars(mixWithWhite(rgb, DARK_LIGHTEN_RATIO), DARK_SOFT);
  return [
    `:root { ${light} }`,
    `${DARK_THEME_SELECTORS} { ${dark} }`,
    `@media (prefers-color-scheme: dark) { :root:not([data-theme='light']) { ${dark} } }`,
  ].join('\n');
}

/** Applies (or clears when color is null) the tenant primary color (FR-ADMIN-01). */
export function applyBrandColor(color: string | null, doc: Document = document): void {
  const css = buildBrandCss(color);
  let style = doc.getElementById(BRAND_STYLE_ID);
  if (!css) {
    style?.remove();
    return;
  }
  if (!style) {
    style = doc.createElement('style');
    style.id = BRAND_STYLE_ID;
    doc.head.appendChild(style);
  }
  style.textContent = css;
}
