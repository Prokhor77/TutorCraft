#!/usr/bin/env node
/**
 * WCAG 2.2 AA guard for design tokens: parses light and dark token blocks from src/styles/tokens.css
 * and checks every text-on-background pair used by components (≥ 4.5:1 text, ≥ 3:1 UI/large).
 */
import { readFileSync } from 'node:fs';

const css = readFileSync(new URL('../src/styles/tokens.css', import.meta.url), 'utf8');

function block(selector) {
  const start = css.indexOf(selector);
  const body = css.slice(css.indexOf('{', start) + 1, css.indexOf('}', start));
  return Object.fromEntries(
    [...body.matchAll(/--([\w-]+):\s*(\d+) (\d+) (\d+);/g)].map((m) => [
      m[1],
      [+m[2], +m[3], +m[4]],
    ]),
  );
}

const lum = (rgb) => {
  const [r, g, b] = rgb.map((c) => {
    const s = c / 255;
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
};
const ratio = (a, b) => {
  const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p);
  return (x + 0.05) / (y + 0.05);
};

const TEXT = 4.5;
const UI = 3;
/** WCAG large text (≥ 24px, or ≥ 18.66px bold). */
const LARGE_TEXT = 3;
const PAIRS = [
  ['text', 'background', TEXT],
  ['text', 'surface', TEXT],
  ['text', 'surface-container-high', TEXT],
  ['text-muted', 'surface', TEXT],
  ['text-muted', 'background', TEXT],
  ['text-muted', 'surface-muted', TEXT],
  ['placeholder', 'surface', TEXT],
  ['primary', 'surface', TEXT],
  ['primary', 'background', TEXT],
  ['primary', 'primary-soft', TEXT],
  ['primary', 'surface-muted', TEXT],
  ['primary-foreground', 'primary', TEXT],
  // Landing: gradient headline accent and large text on the violet CTA gradient (≥ 24px bold → 3:1).
  ['accent', 'background', LARGE_TEXT],
  ['primary-foreground', 'accent', LARGE_TEXT],
  ['success', 'surface', TEXT],
  ['success', 'success-soft', TEXT],
  ['success-foreground', 'success', TEXT],
  ['warning', 'surface', TEXT],
  ['warning', 'warning-soft', TEXT],
  ['warning-foreground', 'warning', TEXT],
  ['draft-foreground', 'draft', TEXT],
  ['danger', 'surface', TEXT],
  ['danger', 'danger-soft', TEXT],
  ['danger-foreground', 'danger', TEXT],
  ['info', 'info-soft', TEXT],
  ['focus-ring', 'surface', UI],
  ['outline', 'surface', UI],
  ['success-accent', 'surface', 1],
];

let failures = 0;
for (const [name, selector] of [
  ['light', ':root {'],
  ['dark', ":root[data-theme='dark']"],
]) {
  const base = block(':root {');
  const tokens = name === 'light' ? base : { ...base, ...block(selector) };
  console.log(`\n${name}`);
  for (const [fg, bg, min] of PAIRS) {
    const value = ratio(tokens[fg], tokens[bg]);
    const ok = value >= min;
    if (!ok) failures += 1;
    console.log(
      `${ok ? '  ok ' : ' FAIL'} ${fg.padEnd(20)} on ${bg.padEnd(24)} ${value.toFixed(2)}:1 (min ${min})`,
    );
  }
}
process.exit(failures ? 1 : 0);
