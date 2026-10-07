#!/usr/bin/env node
/**
 * NFR-I18N-01 guard: every static translation key used in src/ must exist in every locale file,
 * and all locale files must have identical key sets. Dynamic keys (template literals) are listed
 * as patterns and checked by prefix. Exit code 1 on problems.
 */
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';

const ROOT = new URL('..', import.meta.url).pathname;
const SRC = join(ROOT, 'src');
const LOCALES = ['ru', 'en', 'uz'];

function walk(dir) {
  return readdirSync(dir).flatMap((entry) => {
    const path = join(dir, entry);
    if (statSync(path).isDirectory()) return walk(path);
    return /\.(tsx?|mts)$/.test(entry) && !/\.test\./.test(entry) ? [path] : [];
  });
}

function flatten(object, prefix = '') {
  return Object.entries(object).flatMap(([key, value]) =>
    value && typeof value === 'object' ? flatten(value, `${prefix}${key}.`) : [`${prefix}${key}`],
  );
}

const BINDING =
  /(?:const|let)\s+(\w+)\s*=\s*(?:await\s+)?(?:useTranslations|getTranslations)\(\s*'([\w.]+)'\s*\)/g;
const used = new Map();
const dynamic = new Set();

/** Resolves each `t('key')` call to the nearest preceding binding of the same variable (per component). */
for (const file of walk(SRC)) {
  const source = readFileSync(file, 'utf8');
  const bindings = [...source.matchAll(BINDING)].map((match) => ({
    variable: match[1],
    namespace: match[2],
    index: match.index ?? 0,
  }));
  for (const variable of new Set(bindings.map((binding) => binding.variable))) {
    const call = new RegExp(`\\b${variable}\\(\\s*(['\`])((?:(?!\\1).)+)\\1`, 'g');
    for (const match of source.matchAll(call)) {
      const owner = bindings
        .filter((binding) => binding.variable === variable && binding.index < (match.index ?? 0))
        .at(-1);
      if (!owner) continue;
      const [, quote, key] = match;
      const full = `${owner.namespace}.${key}`;
      if (quote === '`' && key.includes('${'))
        dynamic.add(`${full.split('${')[0]}* (${relative(ROOT, file)})`);
      else used.set(full, relative(ROOT, file));
    }
  }
}

const catalogs = Object.fromEntries(
  LOCALES.map((locale) => [
    locale,
    new Set(flatten(JSON.parse(readFileSync(join(ROOT, 'messages', `${locale}.json`), 'utf8')))),
  ]),
);
let problems = 0;
for (const locale of LOCALES) {
  for (const [key, file] of used) {
    if (!catalogs[locale].has(key)) {
      console.error(`[${locale}] missing "${key}" (used in ${file})`);
      problems += 1;
    }
  }
  for (const other of LOCALES.filter((candidate) => candidate !== locale)) {
    for (const key of catalogs[locale]) {
      if (!catalogs[other].has(key)) {
        console.error(`[${other}] missing "${key}" (present in ${locale})`);
        problems += 1;
      }
    }
  }
}
for (const pattern of dynamic) {
  const prefix = pattern.split('*')[0];
  if (![...catalogs.ru].some((key) => key.startsWith(prefix))) {
    console.error(`[ru] no keys for dynamic pattern ${pattern}`);
    problems += 1;
  }
}
if (process.argv.includes('--list')) {
  console.log([...used.keys()].sort().join('\n'));
  console.log('--- dynamic ---');
  console.log([...dynamic].sort().join('\n'));
}
console.log(
  `i18n: ${used.size} static keys, ${dynamic.size} dynamic patterns, ${problems} problem(s)`,
);
process.exit(problems > 0 ? 1 : 0);
