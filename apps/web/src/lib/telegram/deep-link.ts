/**
 * Telegram deep links for bot linking (FR-NOTIF-HYB-01). core-api returns `https://t.me/<bot>?start=<code>`;
 * this module derives the variants each platform needs:
 * - `appUrl` (`tg://resolve`) opens the installed app directly (Telegram Desktop on Windows/macOS, mobile apps);
 * - `httpsUrl` (`https://t.me/...`) is a universal/app link on phones and a landing page with an "Open" button elsewhere;
 * - `webUrl` opens the same bot with the same `start` payload in Telegram Web.
 */
export type TelegramLinks = {
  appUrl: string;
  httpsUrl: string;
  webUrl: string;
};

const T_ME_HOSTS = new Set(['t.me', 'telegram.me']);
/** Telegram bot usernames: 5–32 chars, Latin letters, digits, underscore. */
const BOT_USERNAME = /^[A-Za-z][A-Za-z0-9_]{3,31}$/;
/** Deep-link `start` payload: up to 64 chars of [A-Za-z0-9_-]. */
const START_PAYLOAD = /^[A-Za-z0-9_-]{1,64}$/;
const TELEGRAM_WEB_URL = 'https://web.telegram.org/k/#?tgaddr=';
const MOBILE_UA = /Android|iPhone|iPad|iPod|Mobile/i;

/** Returns null when the link is not a valid bot deep link — the caller must not navigate anywhere. */
export function buildTelegramLinks(deepLink: string): TelegramLinks | null {
  const parsed = parseUrl(deepLink);
  if (!parsed || !T_ME_HOSTS.has(parsed.hostname.toLowerCase())) return null;
  const bot = parsed.pathname.replace(/^\/+@?/, '').replace(/\/+$/, '');
  const start = parsed.searchParams.get('start') ?? '';
  if (!BOT_USERNAME.test(bot) || !START_PAYLOAD.test(start)) return null;

  const appUrl = `tg://resolve?domain=${bot}&start=${start}`;
  return {
    appUrl,
    httpsUrl: `https://t.me/${bot}?start=${start}`,
    webUrl: `${TELEGRAM_WEB_URL}${encodeURIComponent(appUrl)}`,
  };
}

/**
 * Phones: the https link is a universal/app link — it opens the app when installed and the store page otherwise
 * (a bare `tg://` shows an error in iOS Safari when the app is missing).
 * Desktop: `tg://` goes straight to Telegram Desktop; without it nothing happens and the UI offers fallbacks.
 */
export function preferredTelegramUrl(links: TelegramLinks, device: DeviceHints): string {
  return isMobileDevice(device) ? links.httpsUrl : links.appUrl;
}

export type DeviceHints = { userAgent: string; maxTouchPoints: number };

/** iPadOS Safari reports a desktop macOS user agent, but unlike Macs it has a touch screen. */
const IPADOS_UA = /Macintosh/i;

function isMobileDevice({ userAgent, maxTouchPoints }: DeviceHints): boolean {
  return MOBILE_UA.test(userAgent) || (IPADOS_UA.test(userAgent) && maxTouchPoints > 1);
}

function parseUrl(value: string): URL | null {
  try {
    return new URL(value);
  } catch {
    return null;
  }
}
