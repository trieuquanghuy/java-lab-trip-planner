---
phase: 9
slug: polish
status: approved
shadcn_initialized: true
preset: slate
created: 2026-05-19
---

# Phase 9 — UI Design Contract

> Visual and interaction contract for the Polish phase. This phase adds no new pages — it hardens existing UI with error handling, loading states, a11y, and mobile responsiveness.

---

## Design System

| Property | Value |
|----------|-------|
| Tool | shadcn (already initialized) |
| Preset | default / slate base |
| Component library | Radix UI (via shadcn) |
| Icon library | lucide-react |
| Font | System UI stack (`ui-sans-serif, system-ui, -apple-system, ...`) |

**New dependency:** `react-error-boundary` (^4.x) — lightweight error boundary wrapper.

---

## Spacing Scale

Declared values (multiples of 4, matching existing Tailwind config):

| Token | Value | Usage |
|-------|-------|-------|
| xs | 4px | Icon gaps, inline padding |
| sm | 8px | Compact element spacing |
| md | 16px | Default element spacing |
| lg | 24px | Section padding |
| xl | 32px | Layout gaps |
| 2xl | 48px | Major section breaks |
| 3xl | 64px | Page-level spacing (hero sections) |

Exceptions: none — existing spacing patterns maintained throughout.

---

## Typography

Already established. No changes in this phase.

| Role | Size | Weight | Line Height |
|------|------|--------|-------------|
| Body | 16px (base) | 400 | 1.5 |
| Label | 14px (sm) | 500 | 1.4 |
| Heading (page) | 30px (3xl) / 36px (4xl) | 700 | 1.2 |
| Display (hero) | 36px (4xl) / 48px (5xl) | 700 | 1.1 |
| Muted text | 14px (sm) | 400 | 1.5 |

---

## Color

Existing palette preserved. No new colors introduced.

| Role | Value (light) | Usage |
|------|---------------|-------|
| Dominant (60%) | `--background: 0 0% 100%` | Page background |
| Secondary (30%) | `--muted: 210 40% 96.1%` | Cards, skeletons, surfaces |
| Accent (10%) | `--primary: 222.2 47.4% 11.2%` | CTAs, links, focus rings |
| Destructive | `--destructive: 0 84.2% 60.2%` | Error states, destructive actions |

Accent reserved for: primary CTAs ("Go Home", "Try Again", "Retry"), active nav links, focus indicators.

---

## Copywriting Contract

### Error Boundary Fallback (per-route)

| Element | Copy |
|---------|------|
| Heading | Something went wrong |
| Body | We hit an unexpected error. Your data is safe. |
| CTA | Try again |

### Server Error Page (500)

| Element | Copy |
|---------|------|
| Heading | Server Error |
| Body | Something went wrong on our end. Please try again in a moment. |
| CTA | Go Home |

### Inline Query Error (per-component)

| Element | Copy |
|---------|------|
| Message | Failed to load {resource} |
| CTA | Retry |

### Empty States (audit — confirm existing, add missing)

| Page/Component | Heading | Body | CTA |
|----------------|---------|------|-----|
| TripsPage (empty) | No trips yet | Start planning your first adventure | Create a Trip |
| Destination list (no results) | No attractions found | Try searching for a different city | — |
| Trip day (no items) | Add your first stop | Drag a destination here or use the "Add" button | Browse Destinations |
| Favorites (empty) | No favorites yet | Heart destinations you love to find them here | Explore Destinations |

### Loading States

| Element | Behavior |
|---------|----------|
| Page skeleton | Content-aware shapes matching final layout (cards → card skeletons, text → text lines) |
| Skeleton animation | `animate-pulse` (existing pattern, no change) |
| Route transition | `React.Suspense` fallback: centered spinner (lucide `Loader2` with `animate-spin`) |

---

## Accessibility Contracts

### Skip Navigation

| Element | Copy | Implementation |
|---------|------|----------------|
| Skip link | Skip to main content | First focusable element in `<body>`, visually hidden until focused, links to `<main id="main-content">` |

### Focus Management

| Requirement | Implementation |
|-------------|----------------|
| Focus order | Natural DOM order. No `tabindex > 0` anywhere. |
| Focus visible | Tailwind `ring` utility (already configured via `--ring` CSS var) |
| Route change | Announce via `aria-live="polite"` region in Layout |

### ARIA Requirements

| Element | Attribute |
|---------|-----------|
| Icon-only buttons | `aria-label="{action}"` |
| Decorative images | `alt=""` |
| Content images | `alt="{descriptive text}"` |
| Form errors | `aria-describedby="{error-id}"` |
| Loading regions | `aria-busy="true"` while loading |
| Mobile nav toggle | `aria-expanded="{state}"`, `aria-controls="mobile-nav"` |

### Color Contrast

All existing colors meet WCAG AA (4.5:1 body, 3:1 large text). No changes needed — verified against slate palette defaults.

---

## Mobile Responsive Contracts (360px)

### Layout Breakpoints

| Breakpoint | Behavior |
|------------|----------|
| < 640px (mobile) | Single column, hamburger nav, stacked cards, 44px min touch targets |
| 640–1024px (tablet) | 2-column grids, expanded nav |
| > 1024px (desktop) | 3-column grids, full nav |

### Navigation (mobile)

| Element | Behavior |
|---------|----------|
| Header | Logo left, hamburger icon right |
| Menu trigger | `Menu` icon (lucide), 44x44px touch target |
| Menu panel | Full-width dropdown or slide-in, contains: nav links + auth buttons |
| Close | `X` icon or tap outside |

### Touch Targets

All interactive elements minimum 44x44px on viewports < 640px. Enforce via:
- Buttons: already min `h-10` (40px) → increase to `h-11` (44px) on mobile via `sm:h-10`
- Icon buttons: explicit `w-11 h-11` on mobile
- Links in nav: `py-3` padding ensures 44px height

### Content Stacking

| Component | Desktop | Mobile (360px) |
|-----------|---------|----------------|
| Destination grid | 3 columns | 1 column |
| Trip cards | 2-3 columns | 1 column |
| Day tabs | Horizontal scroll | Horizontal scroll (acceptable) |
| Itinerary board | Multi-column | Single column stacked |
| Photo carousel | Horizontal | Horizontal (swipeable, same) |
| Map sidebar | Side panel | Full-screen overlay toggle |

---

## Performance Contracts

### Code Splitting

| Route | Load Strategy |
|-------|---------------|
| HomePage | Eager (entry chunk) |
| LoginPage | `React.lazy()` |
| SignupPage | `React.lazy()` |
| VerifyEmailPage | `React.lazy()` |
| DestinationDetailPage | `React.lazy()` |
| TripsPage | `React.lazy()` |
| TripDetailPage | `React.lazy()` |
| NotFoundPage | `React.lazy()` |

### Suspense Fallback

Centered `Loader2` spinner from lucide with `animate-spin`, wrapped in a flex container matching page min-height. No skeleton for route-level suspense (too generic to be content-aware).

```tsx
function PageLoader() {
  return (
    <div className="flex items-center justify-center py-20">
      <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
    </div>
  );
}
```

---

## Registry Safety

| Registry | Blocks Used | Safety Gate |
|----------|-------------|-------------|
| shadcn official | badge, button, card, form, input, label, popover, separator, skeleton, sonner | not required |
| npm: react-error-boundary | ErrorBoundary | peer-reviewed, 5M+ weekly downloads, no UI components (logic only) |

No new shadcn components needed for this phase.

---

## Component Specifications

### ErrorBoundaryFallback

```
┌──────────────────────────────────────┐
│         (centered, py-20)            │
│                                      │
│          ⚠ (AlertTriangle icon)      │
│                                      │
│     Something went wrong             │  ← text-2xl font-bold
│                                      │
│  We hit an unexpected error.         │  ← text-muted-foreground
│  Your data is safe.                  │
│                                      │
│        [ Try again ]                 │  ← Button variant="default"
│                                      │
└──────────────────────────────────────┘
```

Matches `NotFoundPage` visual pattern: centered, vertical flex, icon + heading + body + CTA.

### ServerErrorPage (route: `/error`)

Same layout as `NotFoundPage` but with:
- Icon: `ServerCrash` (lucide)
- Heading: "Server Error"
- Body: "Something went wrong on our end. Please try again in a moment."
- CTA: "Go Home" → links to `/`

### MobileNav (hamburger menu)

```
┌─────────────────────────────────────────────┐
│ TripPlanner                        [☰]      │  ← header, 44px touch target
├─────────────────────────────────────────────┤
│  (dropdown, border-b, bg-background)        │
│                                              │
│  My Trips                                    │  ← Link, py-3 (44px target)
│  ─────────────────────────────               │  ← separator
│  user@email.com                              │  ← text-sm muted
│  Logout                                      │  ← Button ghost
│                                              │
│  (if logged out:)                            │
│  Login                                       │
│  Sign Up                                     │
│                                              │
└─────────────────────────────────────────────┘
```

### SkipNavLink

Visually hidden (`sr-only`) until focused, then appears as a fixed banner at top:
```
┌─────────────────────────────────────────────┐
│  Skip to main content                        │  ← focus:not-sr-only, bg-primary, text-primary-foreground
└─────────────────────────────────────────────┘
```

---

## Checker Sign-Off

- [x] Dimension 1 Copywriting: PASS — all error/empty/loading copy specified with exact strings
- [x] Dimension 2 Visuals: PASS — component wireframes provided, matches existing NotFoundPage pattern
- [x] Dimension 3 Color: PASS — no new colors, existing palette preserved, WCAG AA confirmed
- [x] Dimension 4 Typography: PASS — no changes to established type scale
- [x] Dimension 5 Spacing: PASS — existing 4px grid maintained, touch targets specified
- [x] Dimension 6 Registry Safety: PASS — only shadcn official + react-error-boundary (logic-only lib)

**Approval:** approved 2026-05-19
