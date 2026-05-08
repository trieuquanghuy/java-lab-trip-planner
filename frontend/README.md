# Trip Planner — Frontend

Phase 0 frontend skeleton. Single landing element + provider stack pre-wired
(`BrowserRouter`, `QueryClientProvider`, axios singleton, Zustand store) so
Phase 7 (auth + discovery), Phase 8 (trip planner), and Phase 9 (polish)
extend without retrofitting.

## Stack

React 18.3 + Vite 6 + TypeScript 5.8 + Tailwind v3.4 + shadcn/ui (CLI-initialized
only — no components generated yet) + React Router 6.30 + TanStack Query 5.100
+ Zustand 5 + Axios 1.16. pnpm 9 (auto-bootstrapped via Corepack).

## Quickstart

```bash
# From repo root: ensure pnpm is on PATH (one-time)
corepack enable

cd frontend
pnpm install --frozen-lockfile
pnpm dev          # http://localhost:5173
pnpm test --run   # Vitest 3 — 2 tests (heading render + console-silent contract)
pnpm build        # tsc -b && vite build
pnpm lint         # ESLint
```

`docker compose up --wait` from repo root brings the frontend container up via
the multi-stage `Dockerfile` in this directory — no prior local `pnpm install`
or `pnpm build` is needed (ROADMAP SC#1 "no manual intervention").

## Adding components (LOCKED to Tailwind v3 path per D-32)

This project uses `shadcn/ui` initialized with the LOCKED Tailwind v3 + React 18
prompt answers per D-32. The locking mechanism is the prompt answers (NOT the
version pin); using a different set of answers — for example accepting the v4
+ React 19 defaults — would produce output incompatible with our locked stack.

**The LOCKED prompt answers** (reproduce these EXACTLY for any future shadcn
invocation in this project, including Phase 7 component additions):

| Prompt | Locked Answer |
|--------|---------------|
| Style | `default` |
| Base color | `slate` |
| CSS file | `src/index.css` |
| CSS variables | `yes` |
| Tailwind config path | `tailwind.config.ts` (NOT `.js`) |
| Path alias | `@/*` → `src/*` |

These are committed in `components.json`, which the CLI reads on every subsequent
`add` invocation — no per-add prompts.

### Phase 0 init history (deviation note)

The plan was authored against `pnpm dlx shadcn@latest init` with interactive
prompts. As of the latest shadcn CLI release, those prompts have been replaced
with preset-based config that defaults to Tailwind v4 + React 19 (incompatible
with our locked v3 + React 18 stack — see CLAUDE.md SHADCN gotcha). Phase 0 ran
`pnpm dlx shadcn@2.x init --base-color slate --css-variables` instead, which
still respects the v3 path. The same `components.json` was produced as the
locked answers would have produced; future adds use either CLI version
correctly because `components.json` is the source of truth.

### To add a component (Phase 7+)

```bash
# Either of these works — both read components.json for the v3 path
pnpm dlx shadcn@2.x add button
pnpm dlx shadcn@latest add button   # works once components.json is committed
```

The CLI writes the component to `src/components/ui/<name>.tsx`. **Phase 0
generates ZERO components** (UI-SPEC §Registry Safety) — only the CLI init
ran.

## Forbidden installs in Phase 0

| Library | Reason |
|---------|--------|
| `react-leaflet` | v5 requires React 19; we're on React 18 (Pitfall I / C21). Phase 8 installs v4.2.x. |
| `@dnd-kit/*` | `@dnd-kit/react` is pre-1.0 unstable. Phase 8 installs `@dnd-kit/core@~6` + `@dnd-kit/sortable`. |
| `tailwindcss@4` | Incompatible with Tailwind v3 utilities and our `tailwind.config.ts` (Pitfall G). |
| `axios@<1.15.0` | Has unpatched CVEs — CVE-2025-62718 (SSRF via NO_PROXY bypass) and CVE-2026-40175 (cloud metadata exfiltration). See CLAUDE.md SECURITY WARNING / D-12 / C19. |

## Project structure

```
frontend/
├── package.json           # locked versions per CLAUDE.md + D-31
├── vite.config.ts         # Vite 6 + Vitest 3 config
├── tailwind.config.ts     # darkMode: 'class', slate palette via CSS vars
├── postcss.config.js
├── components.json        # shadcn config — locked to Tailwind v3 path (D-32)
├── tsconfig.json          # strict; @/* alias
├── tsconfig.node.json     # for vite.config.ts itself
├── index.html             # <html lang="en"> + meta description
├── Dockerfile             # multi-stage (builder + nginx runtime); SC#1 compliant
├── src/
│   ├── main.tsx           # React mount + provider stack
│   ├── App.tsx            # Phase 0 landing only (UI-SPEC §Root Layout Shell)
│   ├── App.test.tsx       # Vitest smoke (heading + console-silent contract)
│   ├── test-setup.ts      # @testing-library/jest-dom
│   ├── index.css          # @tailwind + :root + .dark CSS vars
│   ├── env.d.ts           # Vite env types
│   └── lib/
│       ├── axios.ts       # apiClient singleton, no-op interceptor
│       ├── queryClient.ts # TanStack QueryClient w/ defaults
│       ├── store.ts       # Zustand useAppStore skeleton
│       └── utils.ts       # shadcn cn()
```

## Phase 0 verification checklist

Per UI-SPEC §Phase 0 Verification Checklist — what the executor MUST visually
confirm before declaring Phase 0 done (in addition to ROADMAP success criteria):

- [ ] `localhost:5173` renders the heading "Trip Planner" and subtitle "Your
      itinerary, day by day." with no other content.
- [ ] DevTools Console shows **zero** errors and **zero** React warnings on
      first paint.
- [ ] DevTools Elements panel shows `<html lang="en">` and
      `<body class="...bg-background...">`.
- [ ] DevTools Network panel shows no Google Fonts request (system stack
      confirmed).
- [ ] `tailwind.config.ts` has `darkMode: 'class'` and content globs covering
      `./src/**/*.{ts,tsx}`.
- [ ] `components.json` exists and points to Tailwind v3 paths.
- [ ] `index.css` contains `:root` and `.dark` CSS variable blocks.
- [ ] At 360 px viewport (Chrome DevTools mobile sim), heading and subtitle
      remain readable, no horizontal scroll.
- [ ] Toggling the OS to dark mode does **not** swap colors yet (toggle UI
      deferred) — but inspecting `.dark` class manually in DevTools turns the
      page dark, proving CSS variables are wired.

## Where this plugs in

- The frontend talks to `api-gateway` at `${VITE_API_URL}` (default
  `http://localhost:8080`). Override via `.env.local` (gitignored) for
  alternate environments.
- In compose, the multi-stage Dockerfile builds the bundle inside the
  container — `args.VITE_API_URL` is passed at build time so the value is
  baked into the JS bundle. The nginx runtime stage serves the static bundle
  at `:5173`.
- Phase 7 lands the real auth + discovery pages.
- Phase 8 lands the trip planner with drag-drop + map.
- Phase 9 polishes loading states, accessibility, mobile responsiveness.
