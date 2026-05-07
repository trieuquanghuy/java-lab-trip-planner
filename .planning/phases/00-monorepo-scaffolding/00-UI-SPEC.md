---
phase: 0
slug: monorepo-scaffolding
status: approved
shadcn_initialized: false
preset: shadcn-default-slate-tw3
created: 2026-05-08
reviewed_at: 2026-05-08
---

# Phase 0 — UI Design Contract

> Foundational design-system bootstrap for the Trip Planner frontend. This phase ships **no feature UI**. It establishes the tokens, root layout shell, and shadcn/ui foundation that every later phase (P7 Auth+Discovery, P8 Trip Planner, P9 Polish) builds on. Treat this contract as an evergreen design baseline — feature phases will produce their own UI-SPECs that extend (never override) it.
>
> Source pre-population:
> - `CLAUDE.md` → React 18.3 + Vite + TS 5 + Tailwind v3.4 + shadcn/ui locked
> - `00-CONTEXT.md` D-12 → frontend stack and "no real pages, single landing element"
> - `00-CONTEXT.md` D-13/D-14 → pnpm 9, Vitest, no Playwright
> - `docs/06-frontend-design.md §1, §8, §12` → stack, a11y posture, dark-mode CSS-prepared-but-deferred
> - `ROADMAP.md` Phase 0 SC #4 → success = `localhost:5173` renders without console errors

---

## Design System

| Property | Value |
|----------|-------|
| Tool | shadcn/ui (CLI-initialized only — no components generated yet) |
| Preset | shadcn default, Slate base color, Tailwind v3 path |
| Component library | Radix UI primitives (transitively, when shadcn components are generated in Phase 7+) |
| Icon library | `lucide-react` (shadcn default; install in Phase 0, no icons used yet) |
| Font | System font stack (no Google Fonts in v1 — see "Font loading strategy" below) |
| Dark mode strategy | **Class-based** (`darkMode: 'class'` in `tailwind.config.ts`). Toggle UI deferred to Phase 9; CSS variables for both `:root` and `.dark` ship in `index.css` from Phase 0 so feature phases never need to retrofit. |
| Initialization command | `pnpm dlx shadcn@2.x init` with explicit Tailwind v3 path. CLI's latest defaults to Tailwind v4 + React 19 — we MUST pin the legacy path. Confirm `components.json` after init has `"tailwind.config": "tailwind.config.ts"` and `"tailwind.cssVariables": true`. |

**Font loading strategy:**

System stack only. No webfonts shipped in Phase 0. Rationale:
1. Portfolio scope — no brand identity to preserve.
2. Avoids FOUT/FOIT and an extra network request on the dev-server boot smoke test (SC #4).
3. System fonts achieve WCAG AA contrast/readability on the platform palette.
4. Future phases MAY add a webfont without breaking this contract — but the default is system.

`tailwind.config.ts` `theme.extend.fontFamily.sans`:
```
['ui-sans-serif', 'system-ui', '-apple-system', 'BlinkMacSystemFont',
 'Segoe UI', 'Roboto', 'Helvetica Neue', 'Arial', 'sans-serif']
```

This is Tailwind's default `font-sans`. We declare it explicitly so future phases see the choice was intentional, not absent.

---

## Spacing Scale

Declared values (Tailwind v3 default scale, 4-multiples only — explicitly endorsed for this project):

| Token | Tailwind | Value | Usage |
|-------|----------|-------|-------|
| xs | `space-1` | 4px | Icon-to-text gap, inline badges |
| sm | `space-2` | 8px | Compact element spacing, form-field row gaps |
| md | `space-4` | 16px | Default element spacing, card padding |
| lg | `space-6` | 24px | Section padding, card gap |
| xl | `space-8` | 32px | Layout gaps, header/main divider |
| 2xl | `space-12` | 48px | Major section breaks |
| 3xl | `space-16` | 64px | Page-level vertical rhythm |

**Exceptions for Phase 0:** none. Phase 0 ships only the root `<App>` heading + subtitle and a wrapping shell — touch-target exceptions (44px minimum for icon-only buttons) are introduced in Phase 7 when real interactive controls land.

**Convention for downstream phases:** Use these tokens via Tailwind utilities; do not introduce arbitrary values like `p-[13px]` without UI-SPEC amendment.

---

## Typography

**Three sizes, two weights** for Phase 0. Feature phases may add a fourth (display) size when hero sections appear.

| Role | Tailwind | Size | Weight | Line Height |
|------|----------|------|--------|-------------|
| Body | `text-base` | 16px | 400 (regular) | 1.5 (`leading-relaxed` not needed; default `leading-normal` ≈ 1.5) |
| Label / Small | `text-sm` | 14px | 500 (medium) | 1.5 |
| Heading (h1) | `text-2xl` | 24px | 600 (semibold) | 1.2 (`leading-tight`) |
| Subhead (h2) | `text-lg` | 18px | 600 (semibold) | 1.3 |

**Weights locked:** 400 (regular) + 600 (semibold). No 300/500/700/800 in Phase 0. Feature phases may introduce 500 (medium) for table headers if needed; document in their own UI-SPEC.

**Phase 0 application:**
- App-shell heading "Trip Planner" → `text-2xl font-semibold` (24px / 600).
- Subtitle "Your itinerary, day by day." → `text-base text-muted-foreground` (16px / 400, secondary color).
- All other text in Phase 0 = body default.

---

## Color

**Strategy:** Use shadcn/ui's CSS-variable-based palette so feature phases consume tokens (`bg-background`, `text-foreground`, `bg-primary`, `text-muted-foreground`) instead of raw Tailwind colors. Variables are defined twice in `index.css` — once for `:root` (light) and once for `.dark`. Toggle UI is deferred (see Design System table) but the CSS is ready from day one.

**Base palette:** Slate (shadcn default). Cool, neutral grays — works for the travel/maps/photo content the app will display; doesn't fight against destination thumbnail colors.

| Role | CSS variable | Light value (HSL) | Dark value (HSL) | Usage |
|------|--------------|-------------------|------------------|-------|
| Dominant (60%) | `--background` / `--foreground` | `0 0% 100%` / `222.2 84% 4.9%` | `222.2 84% 4.9%` / `210 40% 98%` | Page background + primary text |
| Secondary (30%) | `--card` / `--muted` | `0 0% 100%` / `210 40% 96.1%` | `222.2 84% 4.9%` / `217.2 32.6% 17.5%` | Card surfaces, sidebar (P7+), muted/disabled chrome |
| Accent (10%) | `--primary` | `222.2 47.4% 11.2%` | `210 40% 98%` | Reserved for: primary CTA buttons, active nav item, focus ring |
| Destructive | `--destructive` | `0 84.2% 60.2%` | `0 62.8% 30.6%` | Reserved for: delete-trip / remove-item confirmation buttons (P6+, P8+) |

**Accent reserved-for list (no exceptions):**
1. The primary CTA on each page (e.g. P7's "Add to Trip", P8's "Create Trip", P2's "Sign Up").
2. The active navigation item in the header (P7+).
3. The focus-visible ring on keyboard-focused interactive controls (`focus-visible:ring-2 focus-visible:ring-ring`).

Accent is **never** used for: body links (use `--foreground` underline), hover states on cards (use `--muted`), informational icons (use `--muted-foreground`), or success states (success states are the absence-of-error; no green chrome in v1).

**Destructive reserved-for list:**
1. The "Confirm shorten trip" button when a date-range reduction would orphan items (P5 server returns 409; P8 UI handles the dialog).
2. The "Remove from day" / "Delete trip" buttons (P6 / P8).

Phase 0 itself uses **only** dominant + secondary roles for the landing element. Accent and destructive ship in CSS but are not visually applied yet.

---

## Copywriting Contract

**Phase 0 has no real CTAs, no empty states (no data exists), no error states (no API calls), and no destructive actions.** It is a boot-verification render only.

| Element | Copy |
|---------|------|
| App-shell heading | `Trip Planner` |
| App-shell subtitle | `Your itinerary, day by day.` |
| Document `<title>` | `Trip Planner` |
| Document `<meta name="description">` | `Plan multi-day trips, discover attractions, and assemble itineraries.` |
| `<html lang>` | `en` (locked; i18n explicitly out of scope per `docs/06-frontend-design.md §12`) |
| Primary CTA | not applicable in Phase 0 (introduced in Phase 7 as `Search destinations`) |
| Empty state heading | not applicable in Phase 0 (introduced in Phase 7 / Phase 8) |
| Empty state body | not applicable in Phase 0 |
| Error state | not applicable in Phase 0 |
| Destructive confirmation | not applicable in Phase 0 |
| Console output on boot | **MUST be silent.** Zero `console.error` / `console.warn` (per ROADMAP success criterion #4 — "renders without console errors"). React DevTools warnings (e.g. about missing keys, deprecated APIs) count as failures. |

**Tone (locked for all future phases):**
- Direct, second-person, lowercase verb-first CTAs ("Add to Trip", not "Click here to add").
- No exclamation marks in CTAs or empty states.
- Error copy = problem + remediation in one sentence ("Couldn't load attractions. Check your connection and retry.").

---

## Root Layout Shell

Phase 0 ships a minimal shell that locks the structural skeleton for every later page:

```
<html lang="en" class="h-full">
  <body class="h-full bg-background text-foreground antialiased">
    <div id="root" class="min-h-screen flex flex-col">
      <!-- Header slot (empty in P0; P7 lands brand + nav + auth controls) -->
      <header class="border-b border-border">
        <!-- intentionally empty in Phase 0 -->
      </header>

      <!-- Main slot (Phase 0 = single landing element; P7+ = <Routes/>) -->
      <main class="flex-1 container mx-auto px-4 py-8 md:py-12">
        <h1 class="text-2xl font-semibold tracking-tight">Trip Planner</h1>
        <p class="mt-2 text-base text-muted-foreground">
          Your itinerary, day by day.
        </p>
      </main>

      <!-- Footer slot (empty in P0; P9 polish may add) -->
      <footer class="border-t border-border">
        <!-- intentionally empty in Phase 0 -->
      </footer>
    </div>
  </body>
</html>
```

**Layout contract (locked from Phase 0 forward):**
- Root is `flex flex-col min-h-screen`. Header and footer are sticky-friendly siblings of `<main>`.
- `<main>` uses `container mx-auto px-4 py-8 md:py-12` for consistent page padding. Mobile-first (NFR-08: usable at 360px wide).
- Header and footer have visible borders (`border-b` / `border-t`) using `--border` token, even when empty — this means later phases adding nav don't introduce visual jumps.
- No CSS resets beyond Tailwind's `@tailwind base`. No global stylesheet beyond `index.css`.

**`index.css` contents (locked):**
```css
@tailwind base;
@tailwind components;
@tailwind utilities;

@layer base {
  :root {
    /* light tokens — values from "Color" table above */
  }
  .dark {
    /* dark tokens — values from "Color" table above */
  }
  * { @apply border-border; }
  body { @apply bg-background text-foreground; }
}
```

---

## Responsive Breakpoints

Tailwind v3 defaults, no custom breakpoints in Phase 0:

| Token | Min width | Phase 0 usage |
|-------|-----------|---------------|
| (default / mobile) | 0 px | Base layout |
| `sm` | 640 px | (unused in P0) |
| `md` | 768 px | `py-12` on `<main>` (vs `py-8` on mobile) |
| `lg` | 1024 px | (unused in P0) |
| `xl` | 1280 px | (unused in P0) |

**Mobile-first contract:** All future phases write mobile styles as the base, then use `md:` / `lg:` to scale up. NFR-08 mandates 360 px wide usability — verify in Phase 9 with Chrome DevTools.

---

## Accessibility Baseline

Locked from Phase 0 (so feature phases never need to retrofit):

- `<html lang="en">` set.
- `<title>` and `<meta name="description">` set in `index.html`.
- Skip-link is **not** required in Phase 0 (no real nav yet); add in Phase 7 with the header.
- Focus-visible outline uses `--ring` token (`focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2`); applied automatically to all shadcn-generated components from Phase 7+.
- WCAG AA contrast verified for the Slate palette: foreground/background ≥ 4.5:1 (Slate-900 on white = 16.8:1; Slate-100 on Slate-950 = 17.5:1).
- `prefers-reduced-motion` honored by Tailwind v3's `motion-safe:` / `motion-reduce:` utilities; no Phase 0 animations.

---

## Registry Safety

| Registry | Blocks Used | Safety Gate |
|----------|-------------|-------------|
| shadcn official (`https://ui.shadcn.com/r`) | none generated in Phase 0 (CLI initialized only) | not required (Phase 0 uses no blocks) |
| third-party | none declared | not applicable |

**Phase 7+ amendment:** When the first shadcn components are generated (`Button`, `Input`, `Dialog`, etc.), they are pulled from the official registry only — no third-party blocks in v1. If a future phase proposes a third-party block, the UI-SPEC for that phase MUST run the registry vetting gate before adoption.

---

## Phase 0 Verification Checklist

What the executor MUST visually confirm before declaring Phase 0 done (in addition to the ROADMAP success criteria):

- [ ] `localhost:5173` renders the heading "Trip Planner" and subtitle "Your itinerary, day by day." with no other content.
- [ ] DevTools Console shows **zero** errors and **zero** React warnings on first paint.
- [ ] DevTools Elements panel shows `<html lang="en">` and `<body class="...bg-background...">`.
- [ ] DevTools Network panel shows no Google Fonts request (system stack confirmed).
- [ ] `tailwind.config.ts` has `darkMode: 'class'` and content globs covering `./src/**/*.{ts,tsx}`.
- [ ] `components.json` exists and points to Tailwind v3 paths.
- [ ] `index.css` contains `:root` and `.dark` CSS variable blocks.
- [ ] At 360 px viewport (Chrome DevTools mobile sim), heading and subtitle remain readable, no horizontal scroll.
- [ ] Toggling the OS to dark mode does **not** swap colors yet (toggle UI deferred) — but inspecting `.dark` class manually in DevTools turns the page dark, proving CSS variables are wired.

---

## Forward-Compatibility Notes (for downstream UI-SPECs)

Phase 7, Phase 8, and Phase 9 UI-SPECs will extend this contract. They MAY add:
- New typography sizes (e.g. `text-3xl` for hero on landing, `text-xs` for timestamps) — declare in their own UI-SPEC.
- New spacing exceptions (e.g. 44px touch targets for icon-only buttons) — declare in their own UI-SPEC.
- New color roles (e.g. day-index marker colors for the trip map, per `docs/06-frontend-design.md §6.5`) — these go under a new "Map color encoding" sub-section, NOT into the accent role.

They MUST NOT:
- Override the dark-mode strategy from class-based to media-query-based (would force a global rewrite).
- Replace the system font stack with a webfont without explicit user approval.
- Use raw color hexes — all colors must come through CSS variables.
- Use arbitrary spacing values (`p-[17px]`) without amendment.

---

## Checker Sign-Off

- [x] Dimension 1 Copywriting: PASS
- [x] Dimension 2 Visuals: PASS
- [x] Dimension 3 Color: PASS
- [x] Dimension 4 Typography: PASS
- [x] Dimension 5 Spacing: PASS
- [x] Dimension 6 Registry Safety: PASS

**Approval:** approved (2026-05-08)
