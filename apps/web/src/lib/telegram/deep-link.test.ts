import { describe, expect, it } from 'vitest';
import { buildTelegramLinks, preferredTelegramUrl } from './deep-link';

const CODE = 'GHQrDanX7sP_qiFlBMHcTcr4fqtSArTOkl_CwQhCauk';
const IPHONE =
  'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148';
const MAC =
  'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/140.0 Safari/537.36';
const WINDOWS =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/140.0 Safari/537.36';

describe('buildTelegramLinks', () => {
  it('derives app, https and web links from the core-api deep link', () => {
    expect(buildTelegramLinks(`https://t.me/TutorCraft_bot?start=${CODE}`)).toEqual({
      appUrl: `tg://resolve?domain=TutorCraft_bot&start=${CODE}`,
      httpsUrl: `https://t.me/TutorCraft_bot?start=${CODE}`,
      webUrl: `https://web.telegram.org/k/#?tgaddr=${encodeURIComponent(
        `tg://resolve?domain=TutorCraft_bot&start=${CODE}`,
      )}`,
    });
  });

  it('repairs a bot name written with @ (t.me/@bot does not open the bot)', () => {
    expect(buildTelegramLinks(`https://t.me/@TutorCraft_bot?start=${CODE}`)?.httpsUrl).toBe(
      `https://t.me/TutorCraft_bot?start=${CODE}`,
    );
  });

  it.each([
    'not a url',
    `https://evil.example/TutorCraft_bot?start=${CODE}`,
    'https://t.me/TutorCraft_bot',
    'https://t.me/TutorCraft_bot?start=bad%20payload',
    `https://t.me/x?start=${CODE}`,
  ])('rejects %s', (link) => {
    expect(buildTelegramLinks(link)).toBeNull();
  });
});

describe('preferredTelegramUrl', () => {
  const links = buildTelegramLinks(`https://t.me/TutorCraft_bot?start=${CODE}`)!;

  it('uses the universal link on phones', () => {
    expect(preferredTelegramUrl(links, { userAgent: IPHONE, maxTouchPoints: 5 })).toBe(
      links.httpsUrl,
    );
  });

  it('treats iPadOS (desktop UA + touch) as mobile', () => {
    expect(preferredTelegramUrl(links, { userAgent: MAC, maxTouchPoints: 5 })).toBe(links.httpsUrl);
  });

  it.each([MAC, WINDOWS])('opens the desktop app via tg:// on %s', (userAgent) => {
    expect(preferredTelegramUrl(links, { userAgent, maxTouchPoints: 0 })).toBe(links.appUrl);
  });
});
