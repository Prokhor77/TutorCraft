---
name: TutorCraft Studio
colors:
  surface: '#f8f9ff'
  surface-dim: '#cbdbf5'
  surface-bright: '#f8f9ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#eff4ff'
  surface-container: '#e5eeff'
  surface-container-high: '#dce9ff'
  surface-container-highest: '#d3e4fe'
  on-surface: '#0b1c30'
  on-surface-variant: '#464554'
  inverse-surface: '#213145'
  inverse-on-surface: '#eaf1ff'
  outline: '#767586'
  outline-variant: '#c7c4d7'
  surface-tint: '#494bd6'
  primary: '#4648d4'
  on-primary: '#ffffff'
  primary-container: '#6063ee'
  on-primary-container: '#fffbff'
  inverse-primary: '#c0c1ff'
  secondary: '#006c49'
  on-secondary: '#ffffff'
  secondary-container: '#6cf8bb'
  on-secondary-container: '#00714d'
  tertiary: '#825100'
  on-tertiary: '#ffffff'
  tertiary-container: '#a36700'
  on-tertiary-container: '#fffbff'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#e1e0ff'
  primary-fixed-dim: '#c0c1ff'
  on-primary-fixed: '#07006c'
  on-primary-fixed-variant: '#2f2ebe'
  secondary-fixed: '#6ffbbe'
  secondary-fixed-dim: '#4edea3'
  on-secondary-fixed: '#002113'
  on-secondary-fixed-variant: '#005236'
  tertiary-fixed: '#ffddb8'
  tertiary-fixed-dim: '#ffb95f'
  on-tertiary-fixed: '#2a1700'
  on-tertiary-fixed-variant: '#653e00'
  background: '#f8f9ff'
  on-background: '#0b1c30'
  surface-variant: '#d3e4fe'
typography:
  headline-xl:
    fontFamily: Bricolage Grotesque
    fontSize: 40px
    fontWeight: '700'
    lineHeight: 48px
    letterSpacing: -0.02em
  headline-xl-mobile:
    fontFamily: Bricolage Grotesque
    fontSize: 30px
    fontWeight: '700'
    lineHeight: 38px
    letterSpacing: -0.015em
  headline-lg:
    fontFamily: Bricolage Grotesque
    fontSize: 32px
    fontWeight: '600'
    lineHeight: 40px
    letterSpacing: -0.015em
  headline-lg-mobile:
    fontFamily: Bricolage Grotesque
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Bricolage Grotesque
    fontSize: 22px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: -0.01em
  headline-sm:
    fontFamily: Bricolage Grotesque
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
  body-lg:
    fontFamily: Work Sans
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 26px
  body-md:
    fontFamily: Work Sans
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 22px
  body-sm:
    fontFamily: Work Sans
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 18px
  label-lg:
    fontFamily: Work Sans
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
  label-md:
    fontFamily: Work Sans
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.02em
  label-sm:
    fontFamily: Work Sans
    fontSize: 10px
    fontWeight: '600'
    lineHeight: 14px
    letterSpacing: 0.04em
rounded:
  sm: 0.5rem
  DEFAULT: 1rem
  md: 1.5rem
  lg: 2rem
  xl: 3rem
  full: 9999px
spacing:
  gutter: 1.25rem
  margin: 1.75rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2.25rem
---

## Brand & Style

This design system manifests an experimental boutique creator aesthetic tailored for modern educators, private tutors, and course creators. Rejecting sterile enterprise administrative layouts, it balances meticulous productivity with tactile, energetic warmth. The visual identity transforms course creation from a tedious grading spreadsheet into an expressive craft workshop where pedagogical modules, interactive quizzes, and media blocks feel fluid, physical, and approachable.

### Design Movement & Ethos
- **Boutique Tactile Minimalism:** Blends clean content scaffolding with warm physical feedback—organic pill shapes, gentle reactive states, and tactile card surfaces that make curriculum design feel creative rather than bureaucratic.
- **Eclectic Typography Hierarchy:** Pairs the quirky, humanistic rhythm of Bricolage Grotesque in headlines with the dependable clarity of Work Sans for dense editorial workspaces, question banks, and configuration drawers.
- **Playful Authority:** Intelligent violet accents establish cognitive focus and modern pedagogical precision, softened by energetic amber tips and reassuring emerald completion pulses.

## Colors

The color system is calibrated for extended authoring sessions with an emphasis on cognitive breathing room, distinct nesting depths, and clear pedagogical states:

- **Primary Violet (`#6366F1`):** Represents focused creation and intelligent scaffolding. Applied to master calls to action, selected tree nodes, active drag indicators, and primary navigation states.
- **Secondary Emerald (`#10B981`):** Signals validation, completed lesson steps, passing quiz criteria, published statuses, and successful auto-saves.
- **Tertiary Amber (`#F59E0B`):** Highlights drafting states, ungraded submissions, pedagogical hints, warning states, and interactive quiz feedback callouts.
- **Slate Neutral (`#64748B`):** Governs multi-tier container boundaries, subtle separator strokes, module handles, and muted metadata to prevent visual fatigue across complex multi-column editor views.

### Tint & Surface Tokens
- `surface-canvas`: `#FAFAFD` — A soft, warm off-white canvas preventing glare during long lesson-planning sessions.
- `surface-card`: `#FFFFFF` — Crisp elevated surface for interactive blocks, question items, and modal layers.
- `surface-subtle`: `#F1F3FB` — Tonal background for nested module child elements, reorder zones, and inactive toolbars.
- `border-subtle`: `#E2E8F0` — Clean delineation line separating nested curriculum trees without visual clutter.

## Typography

Typography bridges expressive creator warmth with functional information hierarchy, optimized for Cyrillic and Latin glyph sets alike.

- **Headlines (Bricolage Grotesque):** Introduces playful, expressive personality to workspace greetings, course headers, module titles, and score metrics. Its tactile curves remove sterility and infuse an artisan feel.
- **Body & Labels (Work Sans):** Chosen for its sturdy neutral legibility in Russian and English instructional copy, nested tree menus, question stem inputs, tooltips, and variable fields.
- **Hierarchical Rules:**
  - Section headers for modules use `headline-sm` with tight tracking.
  - Interactive test answers, explanations, and long-form feedback leverage `body-md` for sustained reading endurance.
  - Badges, status tags, drag handle indicators, and keyboard shortcuts strictly adopt `label-md` or `label-sm` with uppercase transforms where appropriate.

## Layout & Spacing

The layout employs a responsive three-pane fluid workbench architecture suited for non-linear course creation:

### Layout Grid & Panes
- **Curriculum Tree (Left Dock / 280px–340px fluid):** Houses nested module hierarchies, reorderable chapter lists, and quick status pills.
- **Active Canvas / Question Builder (Center Stage / flex-grow):** Unconstrained working zone centered with a max-width of 860px to maintain optimal readability while assembling questions, text prompts, and rich media.
- **Inspector & Settings Panel (Right Dock / 320px fixed):** Granular controls for grading rubrics, scoring weights, time limits, and pedagogical tags.

### Breakpoints & Adaptations
- **Desktop (1280px+):** Full 3-pane synchronized layout with simultaneous preview and contextual inspector.
- **Tablet (768px – 1279px):** Inspector collapses into an overlay slide-out drawer; module tree collapses to an icon sidebar.
- **Mobile (<768px):** Single-column stacked mode. Tutors navigate between "Конструктор" (Builder), "Структура" (Structure), and "Предпросмотр" (Preview) through bottom-anchored segmented controls.

### Spacing Cadence
- Nested module trees indent by exact multiples of `space-md` (16px) up to 3 levels.
- Question block cards enforce an internal padding of `space-lg` (24px) with sibling gaps of `space-md` (16px).

## Elevation & Depth

To maintain tactile warmth without visual chaos, elevation relies on subtle tinted atmospheric shadows and surface color layering:

- **Surface Tiering:**
  - `Level 0 (Canvas)`: `#FAFAFD` flat base workspace.
  - `Level 1 (Card Default)`: Pure white (`#FFFFFF`) with a delicate violet-slate border (`rgba(99, 102, 241, 0.08)`) and diffuse shadow: `0 2px 8px -2px rgba(15, 23, 42, 0.04), 0 1px 3px rgba(15, 23, 42, 0.02)`.
  - `Level 2 (Hover & Active Question Focus)`: Elevated state applying an amplified primary-tinted glow: `0 8px 24px -4px rgba(99, 102, 241, 0.12), 0 2px 6px -1px rgba(99, 102, 241, 0.06)` and a border tint shift to `rgba(99, 102, 241, 0.3)`.
  - `Level 3 (Drag Preview & Flying Blocks)`: Active drag-and-drop elements scale to `1.02x` with depth shadow `0 20px 32px -8px rgba(15, 23, 42, 0.16), 0 8px 16px -4px rgba(99, 102, 241, 0.18)` providing immediate physical elevation feedback.
- **Glassmorphism Layer:** Sticky action ribbons and bottom preview banners leverage frosted glass (`backdrop-filter: blur(12px); background: rgba(255, 255, 255, 0.82)`) with a crisp bottom hairline.

## Shapes

With a roundedness factor of `3`, the design system fully embraces generous, friendly pill-inspired geometry (`rounded-2xl` and `rounded-full` equivalents). This softens complex multi-layered assessment logic into welcoming, modular elements.

- **Primary Cards & Modals:** Radii set to `1.5rem` to `2rem` (24px–32px), creating smooth, approachable modular containers.
- **Form Controls & Inputs:** Inputs and select elements use a `1rem` (16px) contour, ensuring comfortable touch targets and inviting interactive states.
- **Pills & Chips:** Fully pill-shaped (`9999px`) badges, step markers, and filter items for quick scanning.
- **Drag Handles:** Rounded tactile nubs (`0.5rem`) aligned vertically to communicate physical grip.

## Components

### Buttons
- **Primary:** Full pill radius (`rounded-full`), primary violet fill (`#6366F1`), white text (`Work Sans` 600), subtle inner top highlight. Hover triggers a slight physical spring transform (`translate-y: -1px`) and elevation glow.
- **Secondary (Ghost-Pill):** Translucent violet background (`rgba(99, 102, 241, 0.08)`) with `#6366F1` text. Transitions to crisp solid tint on hover.
- **Success Action:** Emerald fill (`#10B981`) for "Опубликовать урок" (Publish Lesson) or "Сохранить изменения" (Save Changes).

### Input Fields & Question Prompts
- High-comfort pill/rounded rectangular fields with `1rem` corner rounding.
- Inactive state: 1.5px subtle slate border (`#E2E8F0`) over white background.
- Focus state: Smooth transition to `#6366F1` border coupled with a 4px diffuse focus ring (`rgba(99, 102, 241, 0.15)`).
- Russian placeholders use muted slate (`#94A3B8`) in casual, encouraging syntax (e.g., *«Введите формулировку вопроса или темы...»*).

### Question Cards & Module Tree Nodes
- **Question Card Container:** Pure white background, `2rem` rounded corners, 1px perimeter outline. Features an integrated left-aligned drag grip handle, contextual question-type icon (e.g., «Один выбор», «Развернутый ответ»), and dynamic score allocation indicator.
- **Module Tree Nodes:** Reorderable pill rows featuring collapsible chevron toggles, completion checkmarks (emerald), and active status highlight bars.

### Chips & Status Badges
- Pill-shaped (`rounded-full`) labels featuring micro-typography (`label-sm`).
- **Черновик (Draft):** Slate tint background (`#F1F5F9`), slate text (`#475569`).
- **Опубликовано (Published):** Emerald tint background (`rgba(16, 185, 129, 0.12)`), emerald text (`#059669`).
- **Требует проверки (Needs Review):** Warm amber tint background (`rgba(245, 158, 11, 0.12)`), amber text (`#D97706`).

### Checkboxes & Radio Selectors
- **Radio Buttons:** Circular custom rings with soft bounce animations upon selection; selected state displays a solid violet dot surrounded by an emerald confirmation ripple during preview scoring.
- **Checkboxes:** `0.5rem` rounded boxes with smooth checkmark vector draws.

### Media Upload Dropzone
- Outlined with an expressive dashed line in primary slate-violet (`rgba(99, 102, 241, 0.3)`), generous `2rem` corners, and a light violet tint (`#F8F9FE`). Displays energetic micro-illustrations, drag-and-drop prompt (*«Перетащите аудио, видео или материалы урока сюда»*), and an explicit file-browser button.