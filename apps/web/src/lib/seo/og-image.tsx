import 'server-only';
import { readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { ImageResponse } from 'next/og';
import { OG_IMAGE_SIZE } from './metadata';

/**
 * 1200×630 link-preview card rendered by the `opengraph-image` routes. Generated instead of using course covers:
 * covers are pre-signed URLs that expire after minutes, while crawlers and messengers cache previews for days.
 *
 * Satori needs static TTF/OTF fonts (no WOFF2, no variable fonts), so `src/assets/og/Onest-*.ttf` are static
 * Latin + Cyrillic instances of the self-hosted Onest (OFL) — see `outputFileTracingIncludes` in next.config.ts.
 */

const FONT_DIR = join(process.cwd(), 'src', 'assets', 'og');
const COLORS = {
  background: '#F8F9FC',
  primary: '#4648D4',
  primarySoft: '#E8E8FF',
  success: '#10B981',
  text: '#121528',
  muted: '#5B6075',
};
const LOGO_SVG =
  '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 40 40"><rect width="40" height="40" rx="10" fill="#4F46E5"/><path d="M12 15L20 10L28 15L20 20L12 15Z" fill="#EEF2FF"/><path d="M14 18.5V24C14 26.5 17 28.5 20 28.5C23 28.5 26 26.5 26 24V18.5L20 22.25L14 18.5Z" fill="#C7D2FE"/><circle cx="28" cy="22" r="2.5" fill="#10B981"/><path d="M28 24.5V28" stroke="#10B981" stroke-width="1.5" stroke-linecap="round"/></svg>';
const LOGO_DATA_URI = `data:image/svg+xml;base64,${Buffer.from(LOGO_SVG).toString('base64')}`;
const TITLE_MAX = 90;

let fontsPromise: Promise<{ medium: Buffer; bold: Buffer }> | null = null;

function loadFonts() {
  fontsPromise ??= Promise.all([
    readFile(join(FONT_DIR, 'Onest-500.ttf')),
    readFile(join(FONT_DIR, 'Onest-700.ttf')),
  ]).then(([medium, bold]) => ({ medium, bold }));
  return fontsPromise;
}

export type OgCard = {
  /** Small label above the title: school name, «Курс», product tagline. */
  eyebrow: string;
  title: string;
  /** One line under the title (teacher, course count, promise). */
  subtitle?: string;
  /** Bottom-right caption, usually the site host. */
  footer: string;
};

function clamp(text: string, max: number): string {
  return text.length > max ? `${text.slice(0, max - 1).trimEnd()}…` : text;
}

export async function renderOgImage(card: OgCard): Promise<ImageResponse> {
  const { medium, bold } = await loadFonts();
  const title = clamp(card.title, TITLE_MAX);
  const titleSize = title.length > 60 ? 56 : title.length > 32 ? 68 : 80;
  return new ImageResponse(
    <div
      style={{
        width: '100%',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'space-between',
        padding: '64px 72px',
        background: COLORS.background,
        borderBottom: `14px solid ${COLORS.primary}`,
        fontFamily: 'Onest',
        color: COLORS.text,
        position: 'relative',
      }}
    >
      <div
        style={{
          position: 'absolute',
          right: -160,
          top: -200,
          width: 640,
          height: 640,
          borderRadius: 9999,
          background: COLORS.primarySoft,
        }}
      />
      <div style={{ display: 'flex', alignItems: 'center', gap: 20 }}>
        {/* eslint-disable-next-line @next/next/no-img-element -- rendered by Satori, not the browser */}
        <img src={LOGO_DATA_URI} width={72} height={72} alt="" />
        <span style={{ fontSize: 36, fontWeight: 700 }}>TutorCraft</span>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 24, maxWidth: 1000 }}>
        <span
          style={{
            display: 'flex',
            alignSelf: 'flex-start',
            padding: '10px 24px',
            borderRadius: 9999,
            background: COLORS.primary,
            color: '#FFFFFF',
            fontSize: 26,
            fontWeight: 500,
          }}
        >
          {clamp(card.eyebrow, 48)}
        </span>
        <span style={{ fontSize: titleSize, fontWeight: 700, lineHeight: 1.1 }}>{title}</span>
        {card.subtitle ? (
          <span style={{ fontSize: 30, fontWeight: 500, color: COLORS.muted }}>
            {clamp(card.subtitle, 80)}
          </span>
        ) : null}
      </div>
      <div style={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: 12 }}>
        <div style={{ width: 14, height: 14, borderRadius: 9999, background: COLORS.success }} />
        <span style={{ fontSize: 26, fontWeight: 500, color: COLORS.muted }}>{card.footer}</span>
      </div>
    </div>,
    {
      ...OG_IMAGE_SIZE,
      fonts: [
        { name: 'Onest', data: medium, weight: 500, style: 'normal' },
        { name: 'Onest', data: bold, weight: 700, style: 'normal' },
      ],
      headers: { 'Cache-Control': 'public, max-age=3600, stale-while-revalidate=86400' },
    },
  );
}
