# 06 — Frontend Design

**Status**: Draft for review
**Last updated**: 2026-05-08

## 1. Tech stack recap

| Concern | Choice |
|---------|--------|
| Framework | React 18 |
| Build | Vite 5 + TypeScript 5 |
| Routing | React Router 6 |
| Server state | TanStack Query v5 |
| Client state | Zustand |
| Forms | React Hook Form + Zod |
| HTTP | Axios + interceptors |
| Drag-drop | dnd-kit |
| Map | Leaflet 1.9 + react-leaflet |
| Styling | Tailwind 3 + shadcn/ui |
| Testing | Vitest + Testing Library + Playwright |
| Package manager | pnpm |

## 2. Folder layout

```
frontend/
├── package.json
├── vite.config.ts
├── tsconfig.json
├── tailwind.config.ts
├── index.html
├── public/
├── src/
│   ├── main.tsx                    ← bootstrap, providers
│   ├── App.tsx                     ← top-level routes
│   ├── pages/                      ← route components
│   │   ├── HomePage.tsx
│   │   ├── DestinationDetailPage.tsx
│   │   ├── TripListPage.tsx
│   │   ├── TripDetailPage.tsx
│   │   ├── FavoritesPage.tsx
│   │   ├── LoginPage.tsx
│   │   ├── SignupPage.tsx
│   │   ├── VerifyEmailPage.tsx
│   │   └── NotFoundPage.tsx
│   ├── features/                   ← cohesive feature modules
│   │   ├── auth/
│   │   │   ├── AuthProvider.tsx
│   │   │   ├── ProtectedRoute.tsx
│   │   │   ├── useAuth.ts
│   │   │   ├── auth.api.ts
│   │   │   └── auth.store.ts       ← Zustand
│   │   ├── search/
│   │   │   ├── SearchInput.tsx
│   │   │   ├── useSearch.ts        ← TanStack Query + debounce
│   │   │   └── search.api.ts
│   │   ├── destinations/
│   │   │   ├── DestinationCard.tsx
│   │   │   ├── DestinationList.tsx
│   │   │   ├── DestinationDetailDialog.tsx
│   │   │   ├── PhotoCarousel.tsx
│   │   │   └── destinations.api.ts
│   │   ├── trips/
│   │   │   ├── TripCard.tsx
│   │   │   ├── TripList.tsx
│   │   │   ├── TripHeader.tsx
│   │   │   ├── DayColumn.tsx
│   │   │   ├── ItineraryItem.tsx
│   │   │   ├── AddToTripDialog.tsx
│   │   │   ├── ItineraryBoard.tsx  ← dnd-kit context
│   │   │   ├── trips.api.ts
│   │   │   └── trips.store.ts
│   │   ├── favorites/
│   │   │   ├── FavoriteButton.tsx
│   │   │   └── favorites.api.ts
│   │   └── map/
│   │       └── TripMap.tsx
│   ├── components/                 ← shared UI primitives (shadcn-derived)
│   │   ├── ui/                     ← Button, Input, Dialog, etc.
│   │   ├── EmptyState.tsx
│   │   ├── LoadingSkeleton.tsx
│   │   └── ErrorBoundary.tsx
│   ├── api/
│   │   ├── client.ts               ← axios instance, interceptors
│   │   ├── queryClient.ts          ← TanStack Query setup
│   │   └── errors.ts               ← maps backend error codes → UI messages
│   ├── lib/
│   │   ├── dates.ts                ← date math helpers
│   │   ├── debounce.ts
│   │   └── classnames.ts           ← cn(...)
│   └── types/                      ← shared TS types (mirror backend DTOs)
└── tests/
    ├── unit/
    └── e2e/                        ← Playwright
```

## 3. Routing

```tsx
<BrowserRouter>
  <Routes>
    <Route path="/" element={<HomePage />} />
    <Route path="/destinations/:providerRef" element={<DestinationDetailPage />} />
    <Route path="/login" element={<LoginPage />} />
    <Route path="/signup" element={<SignupPage />} />
    <Route path="/verify" element={<VerifyEmailPage />} />

    <Route element={<ProtectedRoute />}>
      <Route path="/trips" element={<TripListPage />} />
      <Route path="/trips/:tripId" element={<TripDetailPage />} />
      <Route path="/favorites" element={<FavoritesPage />} />
    </Route>

    <Route path="*" element={<NotFoundPage />} />
  </Routes>
</BrowserRouter>
```

`ProtectedRoute` redirects unauthenticated users to `/login?next=<currentPath>`,
restoring the original location after successful login (per FR-10).

## 4. State management strategy

### 4.1 Server state — TanStack Query
All data fetched from the backend goes through TanStack Query. Cache key
conventions:

| Resource | Query key |
|----------|-----------|
| Search results | `['search', q, type]` |
| Nearby destinations | `['destinations', lat, lng, radius]` |
| Destination detail | `['destination', providerRef]` |
| Trip list | `['trips']` |
| Trip detail | `['trip', tripId]` |
| Favorites | `['favorites']` |

Invalidation rules:
- After `POST /trips` → invalidate `['trips']`.
- After any item mutation → invalidate `['trip', tripId]`.
- After favorite mutation → invalidate `['favorites']`.

Mutations that affect ordering use **optimistic updates**:
- `onMutate`: snapshot current data, apply the change locally.
- `onError`: rollback to snapshot.
- `onSettled`: invalidate the affected query.

### 4.2 Client state — Zustand

Two small stores. No Redux.

```ts
// auth.store.ts
type AuthState = {
  accessToken: string | null;
  user: { id: string; email: string } | null;
  setSession: (s: Session) => void;
  clearSession: () => void;
};

// ui.store.ts
type UiState = {
  addToTripContext: { destinationRef: string } | null;  // for the "log in to add" deferred action
  setAddToTripContext: (ctx: AddToTripContext | null) => void;
};
```

Why Zustand: ~1 KB gzipped, zero context ceremony, easy to mock in tests, and
state survives React DevTools nicely.

### 4.3 Form state — React Hook Form + Zod
Each form defines a Zod schema (mirroring backend validation), uses
`useForm({ resolver: zodResolver(schema) })`. Submit handler calls the
mutation; backend `validation.failed` errors are mapped to per-field errors.

## 5. API client

```ts
// api/client.ts
const client = axios.create({
  baseURL: import.meta.env.VITE_API_URL,    // http://localhost:8080
  withCredentials: true,                     // sends refresh-token cookie
});

client.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  config.headers['X-Request-Id'] = crypto.randomUUID();
  return config;
});

client.interceptors.response.use(undefined, async (error) => {
  if (error.response?.status === 401 && !error.config._retried) {
    error.config._retried = true;
    const ok = await refreshAccessToken();
    if (ok) return client(error.config);
    useAuthStore.getState().clearSession();
    location.href = '/login';
  }
  throw error;
});
```

A single `refreshAccessToken()` function de-duplicates concurrent refresh
calls (multiple parallel requests get 401 → all share the same refresh
promise).

## 6. Critical user flows

### 6.1 Search → recommendation list
1. User types in `SearchInput`.
2. `useSearch(q)` debounces input by 250 ms.
3. TanStack Query fires `GET /api/search?q=…`.
4. Results render below input. Selecting a city sets the active location in `ui.store`.
5. `DestinationList` reads location and fires `GET /api/destinations?lat=…&lng=…&radius=20000`.
6. Results render as `DestinationCard`s with photo, name, category, rating, favorite button.

### 6.2 Add to trip (logged in)
1. User clicks "Add to trip" on a `DestinationCard` or detail dialog.
2. `AddToTripDialog` opens, listing user's trips with day picker.
3. User picks trip + day, clicks Add.
4. Optimistic update: item appears in TanStack cache for `['trip', tripId]` immediately.
5. `POST /api/trips/{id}/days/{dayId}/items` fires.
6. On success: cache aligned with server response. On error: rollback + toast.

### 6.3 Add to trip (logged out → deferred action)
1. User clicks "Add to trip" while unauthenticated.
2. `ui.store.addToTripContext = { destinationRef }`.
3. Navigate to `/login?next=/destinations/{ref}`.
4. After successful login, `LoginPage` checks `addToTripContext`. If set, completes the original mutation and navigates back. If not, navigate to `next`.

### 6.4 Drag-drop reorder
1. `ItineraryBoard` wraps days in a `DndContext` from dnd-kit.
2. Each `DayColumn` is a `SortableContext`; each `ItineraryItem` is `useSortable`.
3. On drag end:
   - Compute new `position` (midpoint of neighbors).
   - If moved to a different day, set `itineraryDayId` to the new day's id.
   - Optimistic update to `['trip', tripId]`.
   - `PATCH /api/trips/{id}/items/{itemId}`.
4. Reindex trigger: when local cache shows two items < 2 apart, schedule a backend reindex via a `POST /api/trips/{id}/reindex` (admin-style, idempotent). This endpoint is added later if needed; v1 starts with 100-spaced positions and falls back to client-side renumber on collision.

### 6.5 Trip map view
1. `TripDetailPage` has a tab toggle: List | Map.
2. `TripMap` reads items from query cache, hydrates each `destinationRef` via parallel `GET /api/destinations/{ref}` calls (TanStack Query handles in-flight dedup).
3. Markers render with day-index color coding.
4. `fitBounds` to all markers on first render.

### 6.6 Signup → verify → first action
1. User submits signup form. On success, redirect to `/signup?status=check-email`.
2. Page shows "Check your email" with a "Resend" button.
3. User clicks email link → `/verify?token=…` → POST verify → success message → CTA to login.
4. Post-login, user lands at `/` with a logged-in nav bar.

## 7. Key UI components

### 7.1 EmptyState
Used on `TripListPage` (no trips), `FavoritesPage` (no favorites), search with
no results. Single component with `title`, `description`, optional `action`.

### 7.2 LoadingSkeleton
Per-page skeletons (e.g. trip card skeleton, attraction card skeleton). Avoids
layout shift on first render.

### 7.3 ErrorBoundary
Wraps each page route. Renders friendly fallback + retry button + (in dev)
the error stack. Does not catch errors inside async event handlers — those
are surfaced via toast notifications.

### 7.4 Toast notifications
Single Sonner-style toast component for success ("Added to trip"), error
("Couldn't reorder, please try again"), and pending states.

## 8. Accessibility

- All interactive elements reachable by Tab.
- Focus visible (Tailwind `focus-visible:` ring).
- Drag-drop has keyboard alternative: select item with space, arrow keys to move, space to drop. dnd-kit supports this out of the box; we expose it on each item.
- Form errors announced via `aria-live="polite"`.
- Color contrast: text ≥ 4.5:1, large text ≥ 3:1. Tailwind palette pre-checked.
- Map view also exposes a list view (List | Map toggle) so users with screen readers don't depend on map.

## 9. Performance

- Code-split per route via `React.lazy` and `Suspense`. Trip detail page (heaviest, with dnd-kit and Leaflet) loads on demand.
- Image lazy-loading: `<img loading="lazy" />` on all destination photos.
- TanStack Query `staleTime: 5_minutes` for search and destination queries to avoid refetch storms.
- Bundle target: < 250 KB gzipped initial load.
- Lighthouse target: 90+ Performance, 95+ Accessibility on `/` and `/trips/:id`.

## 10. Environment configuration

```
frontend/.env.example
VITE_API_URL=http://localhost:8080
VITE_TILE_SERVER_URL=https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png
VITE_SENTRY_DSN=                 # optional, off by default
```

`.env.development.local` and `.env.production.local` are gitignored.

## 11. Wireframe sketches (text)

```
HomePage
┌───────────────────────────────────────────────────────────────────┐
│ TripPlanner          [Search…  Tokyo]    My Trips ♡  Login Sign Up │
├───────────────────────────────────────────────────────────────────┤
│  Showing 20 attractions near Tokyo, Japan                         │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐                              │
│  │photo │ │photo │ │photo │ │photo │  cards…                       │
│  │name  │ │name  │ │name  │ │name  │                              │
│  │★4.7 ♡│ │★4.4  │ │★…    │ │★…    │                              │
│  └──────┘ └──────┘ └──────┘ └──────┘                              │
└───────────────────────────────────────────────────────────────────┘

TripDetailPage (board view)
┌───────────────────────────────────────────────────────────────────┐
│ ← Tokyo 2026   Sep 10–15, 2026   [edit dates]   [List|Map] [Cover]│
├───────────────────────────────────────────────────────────────────┤
│  Day 1 (Sep 10)   Day 2 (Sep 11)   Day 3 (Sep 12)   …             │
│  ┌────────────┐   ┌────────────┐   ┌────────────┐                  │
│  │ ⠿ 10:00    │   │ ⠿          │   │ ⠿ 14:00    │                  │
│  │ Sensoji    │   │ Asakusa    │   │ Shibuya    │                  │
│  │ note: …    │   │            │   │ Crossing   │                  │
│  ├────────────┤   ├────────────┤   ├────────────┤                  │
│  │ ⠿          │   │ ⠿          │   │ + Add      │                  │
│  │ Akihabara  │   │ Ueno Park  │   └────────────┘                  │
│  └────────────┘   └────────────┘                                   │
│  + Add            + Add                                            │
└───────────────────────────────────────────────────────────────────┘
```

## 12. Out of scope for v1 frontend
- Dark mode toggle (CSS prepared, toggle deferred).
- i18n (English only).
- Service worker / PWA (deferred to v2).
- Lighthouse CI (deferred; manual checks during Phase 9 polish).
