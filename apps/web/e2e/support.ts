import { expect, type Page } from '@playwright/test';
import ru from '../messages/ru.json';

export const t = ru;
export const MAILPIT_URL = process.env.E2E_MAILPIT_URL ?? 'http://localhost:8025';
export const PASSWORD = process.env.E2E_PASSWORD ?? 'Str0ngPassw0rd!';
const MAIL_POLL_MS = 1_000;
const MAIL_TIMEOUT_MS = 60_000;

export function uniqueEmail(prefix: string): string {
  return `${prefix}.${Date.now()}.${Math.random().toString(36).slice(2, 7)}@e2e.tutorcraft.local`;
}

/** Reads the newest email to `to` from Mailpit and returns the first link matching `pattern`. */
export async function linkFromMail(to: string, pattern: RegExp): Promise<string> {
  const deadline = Date.now() + MAIL_TIMEOUT_MS;
  while (Date.now() < deadline) {
    const search = await fetch(
      `${MAILPIT_URL}/api/v1/search?query=${encodeURIComponent(`to:${to}`)}`,
    ).then((response) => response.json() as Promise<{ messages: { ID: string }[] }>);
    const id = search.messages?.[0]?.ID;
    if (id) {
      const message = await fetch(`${MAILPIT_URL}/api/v1/message/${id}`).then(
        (response) => response.json() as Promise<{ HTML: string; Text: string }>,
      );
      const match = `${message.HTML}\n${message.Text}`.match(pattern);
      if (match) return match[0].replace(/&amp;/g, '&');
    }
    await new Promise((resolve) => setTimeout(resolve, MAIL_POLL_MS));
  }
  throw new Error(`No email with ${pattern} for ${to}`);
}

export async function expectSaved(page: Page) {
  await expect(
    page
      .getByRole('status')
      .filter({ hasText: new RegExp(`${t.autosave.saved}|${t.autosave.idle}`) })
      .first(),
  ).toBeVisible({ timeout: 20_000 });
}

/** Opens the "+" type picker in a module and creates an item with just a title (AC-2: ≤ 3 clicks + title). */
export async function quickCreateItem(
  page: Page,
  moduleTitle: string,
  typeLabel: string,
  title: string,
  fill?: (page: Page) => Promise<void>,
) {
  const moduleSection = page
    .locator('section')
    .filter({ has: page.getByRole('heading', { name: moduleTitle }) });
  await moduleSection
    .getByRole('button', { name: new RegExp(`${t.course.addFirstItem}|${t.course.addItem}`) })
    .last()
    .click(); // click 1
  await page.getByRole('option', { name: new RegExp(typeLabel) }).click(); // click 2
  await page.getByLabel(t.quickCreate.name).fill(title);
  if (fill) await fill(page);
  await page.getByRole('button', { name: t.quickCreate.create, exact: true }).click(); // click 3
  await expect(moduleSection.getByRole('link', { name: title })).toBeVisible();
}
