# TutorCraft Studio — design system

This document is the single source of truth for the web app's visual language. Everything it describes lives
inside `apps/web`: tokens in `src/styles/tokens.css`, Tailwind mapping in `tailwind.config.ts`, fonts from npm
(`@fontsource-variable/*`), the logo in `src/components/layout/brand.tsx` and `public/icons/`, components in
`src/components/ui` and `src/components/landing`. No external design files are needed to build or change the UI.

## 1. Character

«Boutique tactile minimalism» for tutors and course authors: a craft workshop, not a grey admin panel. Pill shapes,
soft violet-tinted shadows, tactile cards, expressive headings.

| Role                          | Meaning                                              |
| ----------------------------- | ---------------------------------------------------- |
| **Violet** (primary / accent) | focus, primary actions, selection, active navigation |
| **Emerald** (success)         | done, published, saved, passed                       |
| **Amber** (warning)           | draft, needs review, hints, deadlines soon           |
| **Slate** (neutral)           | borders, separators, drag handles, metadata          |
| **Red** (danger)              | errors, overdue, destructive actions                 |

## 2. Colour tokens

Tokens are CSS variables with `R G B` channels (so Tailwind can apply alpha: `bg-accent/10`). Components use
**roles only** — never hex values. Light values (Material roles):

| Token                                   | Light                                        | Use                                     |
| --------------------------------------- | -------------------------------------------- | --------------------------------------- |
| `--background`                          | `#f8f9ff`                                    | page (surface)                          |
| `--canvas`                              | `#fafafd`                                    | long editing canvases                   |
| `--surface`                             | `#ffffff`                                    | cards, inputs, dialogs                  |
| `--surface-muted`                       | `#eff4ff`                                    | tinted sub-panels, tab tracks, footer   |
| `--surface-container` / `-high`         | `#e5eeff` / `#dce9ff`                        | nested chips, rings                     |
| `--surface-subtle`                      | `#f1f3fb`                                    | inactive toolbars                       |
| `--dropzone`                            | `#f8f9fe`                                    | upload areas                            |
| `--border`                              | `#e2e8f0`                                    | hairlines, input borders                |
| `--outline` / `--outline-variant`       | `#767586` / `#c7c4d7`                        | icons on surfaces, radio/checkbox rings |
| `--text`                                | `#0b1c30`                                    | on-surface                              |
| `--text-muted`                          | `#464554`                                    | on-surface-variant, metadata            |
| `--placeholder`                         | `#64748b`                                    | placeholders (see §9)                   |
| `--primary`                             | `#4648d4`                                    | filled buttons, active tabs, links      |
| `--primary-container`                   | `#6063ee`                                    | gradients                               |
| `--primary-soft`                        | `#e1e0ff`                                    | soft violet fills (primary-fixed)       |
| `--accent` / `--focus-ring`             | `#6366f1`                                    | glows, tints, focus rings               |
| `--success`                             | `#006c49` (accent `#10b981`, soft `#e1f6ef`) | emerald role                            |
| `--warning`                             | `#825100` (accent `#f59e0b`, soft `#fef3e1`) | amber role                              |
| `--draft` / `--draft-foreground`        | `#f1f5f9` / `#475569`                        | «Черновик» chip                         |
| `--danger`                              | `#ba1a1a` (soft `#ffdad6`)                   | error role                              |
| `--card-border` / `--card-border-hover` | accent @ 8 % / 30 %                          | card perimeter L1 / L2                  |
| `--glass`                               | surface @ 82 %                               | sticky bars                             |

**Dark theme** is derived from the same hues: navy surfaces (`#0a1120` → `#243252`), lighter violet / emerald /
amber roles with dark «on» colours (Material dark pattern), so filled buttons and chips stay ≥ 4.5 : 1. It follows
`prefers-color-scheme` and a manual toggle (`data-theme` on `<html>`).

**Tenant branding** (`buildBrandCss()` in `src/lib/utils/color.ts`) overrides `--primary*`, `--accent` and
`--focus-ring` for light and a lightened dark variant via `<style id="tc-brand">`.

## 3. Typography

| Style             | Font                | Size / line                   | Weight | Tailwind                            |
| ----------------- | ------------------- | ----------------------------- | ------ | ----------------------------------- |
| headline-xl       | Bricolage Grotesque | 40/48 (mobile 30/38), −0.02em | 700    | `text-4xl` / `text-hero-mobile`     |
| headline-lg       | Bricolage Grotesque | 32/40 (mobile 24/32)          | 600    | `text-3xl` / `text-2xl`             |
| headline-md       | Bricolage Grotesque | 22/28                         | 600    | `text-xl`                           |
| headline-sm       | Bricolage Grotesque | 18/24                         | 600    | `text-lg`                           |
| body-lg / md / sm | Work Sans           | 16/26 · 14/22 · 12/18         | 400    | `text-base` · `text-sm` · `text-xs` |
| label-lg          | Work Sans           | 14/20                         | 600    | `text-label-lg`                     |
| label-md          | Work Sans           | 12/16, +0.02em                | 600    | `text-label-md`                     |
| label-sm          | Work Sans           | 10/14, +0.04em                | 600    | `text-label-sm`                     |

Headings (`h1–h4`) use `font-heading` automatically. Badges, status tags, eyebrows, drag-handle labels and keyboard
shortcuts use `label-md`/`label-sm`, uppercase where it reads as a label. Neither Bricolage nor Work Sans ships
Cyrillic glyphs, so the stacks fall back per glyph to **Geologica** (headings) and **Onest** (body). All four are
self-hosted variable fonts from npm. `cn()` knows the custom sizes (`src/lib/utils/cn.ts`) — add new ones there too.

## 4. Shape, spacing, depth

- **Radii:** `rounded-sm` .5rem (checkboxes, drag nubs) · `rounded` 1rem (inputs, selects) · `rounded-md` 1.5rem
  (cards) · `rounded-lg` 2rem (question cards, dropzones, dialogs, landing cards) · `rounded-xl` 3rem (hero mockup,
  CTA banner) · `rounded-full` (buttons, chips, tabs, tree nodes).
- **Spacing:** 4 · 8 · 16 · 24 · 36 px (`space-xs…xl`); gutter 20 px (`gap-gutter`); page margin 16 px mobile /
  28 px ≥ 768 (`px-page-x`). Tree indents in multiples of 16 px; cards pad 24 px with 16 px gaps.
- **Elevation:** L1 `shadow-sm` + `border-card-border` (cards) · L2 `shadow-md` + `border-card-border-hover`
  (hover / focus) · L3 `shadow-lg` + `scale-[1.02]` (dragging, overlays) · `shadow-glow` (primary hover).
- **Glass:** `.glass` — `backdrop-filter: blur(12px)` over `--glass` + hairline border; for sticky headers and
  action bars.
- **Motion:** `--motion-fast` 140 ms, `--motion-base` 220 ms, `--ease-bounce`; `.lift` = hover `translateY(-1px)`.
  Under `prefers-reduced-motion` durations drop to 0, `.lift` is disabled and anchor smooth-scrolling is off.

## 5. Layout

- **Sizes:** header 4rem (app) / 5rem (public), bottom nav 4.25rem, content max 90rem, tree 18.5rem, canvas max
  53.75rem (860 px), inspector 20rem.
- **Breakpoints:** mobile < 768 · tablet 768–1279 · desktop ≥ 1280 (`xl`).
- **App shell:** glass header with logo, breadcrumbs (2xl), pill section tabs, actions, notifications and user chip;
  mobile: two-line brand (school eyebrow + section) and a glass bottom nav (global sections, or inside a course
  Курс · Тесты · Проверка/Прогресс · Медиа · Профиль).
- **Three-pane workbench** (course builder, quiz builder, media library, grading): left dock (tree / structure /
  filters / queue) · centre canvas · right inspector 320 px. Tablet: left dock becomes an icon rail, inspector
  moves under the canvas or into a sheet. Mobile: single column, segmented control
  «Конструктор / Структура / Предпросмотр».
- **Public pages** (landing, auth, catalog, course landing, checkout, invites): `SiteHeader` + `SiteFooter` from
  `src/components/landing`.
- **Page rhythm:** every app page is a stack of white 2rem panels on `--background`: header card → KPI `StatGrid`
  (real data only) → content panels; lists inside panels are rounded rows with avatars and status chips.

## 6. Components (in `src/components/ui` unless noted)

- **Button** — `primary` violet pill with lift + glow; `secondary` ghost pill (`accent/10` + violet text); `success`
  emerald («Сохранить изменения», «Опубликовать»); `ghost`, `soft`, `danger`, `link`.
- **Inputs** — 1rem radius, 1.5 px `--border`; focus = violet border + 4 px `focus-ring/15` ring; friendly
  placeholders. `IconInput` (leading icon), `PasswordInput` (show/hide with `aria-pressed`), `NativeSelect`.
- **Field** — label + control + hint + error with aria wiring; `labelVariant="caps"` for auth forms.
- **Checkbox / Radio / Switch** — 0.5rem checkbox with pop-in check; radio ring with bouncing violet dot.
- **Card** and **Panel** (white 2rem section panel with title/description/actions), **StatCard / StatGrid** (uppercase
  label, tinted icon chip, headline value, footer), **PageHeader** — a white 2rem header card with `breadcrumbs`
  (`Breadcrumbs`), eyebrow, title, status `meta`, actions and a bottom toolbar row (`children`: tabs, filters);
  `plain` drops the card. **Tabs** (inset pill track, active tab filled violet) / **Segmented** (white thumb), **Badge** (label-md pill,
  optional status dot), **StatusChip** (`src/components/course`): Опубликовано · Черновик · Запланировано.
- **FileDropzone** — dashed `accent/30`, 2rem radius, `--dropzone` fill, copy «Перетащите аудио, видео или
  материалы урока сюда» + browse button; `layout="inline"` strip variant.
- **Question card** — white, 2rem radius, drag grip, type tag (`QuestionTypeTag`), points (`PointsPill`).
- **Course tree** — module cards with count chips, pill item rows, emerald checks, active accent bar.
- **QueueCard** (`src/components/grading`) — avatar, student, work, time chip, status line, active bar.
- **SavedIndicator** — «Сохранено N мин назад» from real saves only.
- **Landing** (`src/components/landing`) — `SiteHeader`, `SiteFooter`, `Hero` + `HeroMockup` (inert, demo data,
  captioned), `TrustStrip`, `ToolsBento`, `SavingsCalculator`, `StudentBenefits`, `Testimonials`, `Pricing`,
  `FinalCta`.

## 7. Logo

40 × 40 grid: rounded square `rx=10` in `#4F46E5`; mortarboard top `M12 15 L20 10 L28 15 L20 20 Z` in `#EEF2FF`;
cap body `M14 18.5 V24 C14 26.5 17 28.5 20 28.5 C23 28.5 26 26.5 26 24 V18.5 L20 22.25 Z` in `#C7D2FE`; emerald
tassel `circle(28, 22, r 2.5)` + line `28,24.5 → 28,28` (1.5 px, round caps) in `#10B981`. Sources:
`LogoMark` (`src/components/layout/brand.tsx`), `public/icons/icon.svg`, `icon-maskable.svg` (70 % safe zone);
PNGs are generated by `scripts/generate-icons.py`. Icons in the UI: Lucide (tree-shaken SVG, rounded 2 px stroke).

## 8. Content rules

- **Honesty:** only real features and real data. Metrics come from the API; illustrative mockups use demo data and
  are captioned as examples. Landing social proof and testimonials render only when filled in
  `src/content/landing.ts` with real, consented data.
- **Language:** Russian first, English complete (next-intl); every string lives in `messages/{ru,en}.json`
  (`npm run i18n:check`). Russian copy is friendly and direct («Перетащите…», «Сохранить и далее»).

## 9. Accessibility

- WCAG 2.2 AA: `npm run contrast:check` verifies text/fill pairs in both themes. Two source colours were replaced to
  pass AA: placeholder `#94a3b8` → `#64748b`, amber chip text `#d97706` → `#825100`.
- Visible focus (`focus-visible` ring 4 px), skip link on every layout, landmarks, labelled controls, `aria-live`
  for autosave/timers/results, decorative mockups `inert` + `aria-hidden`, reduced motion respected.
