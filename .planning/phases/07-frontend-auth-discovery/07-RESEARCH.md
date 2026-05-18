# Phase 7: Frontend — Auth + Discovery — Research

**Researched:** 2026-05-18
**Phase goal:** A user can search for destinations and view their details in the browser; auth pages work end-to-end including the signup verification flow.
**Requirement IDs:** TRIP-05

---

## 1. Standard Stack (Already in package.json)

| Concern | Package | Version | Notes |
|---------|---------|---------|-------|
| Framework | react | ^18.3.0 | Locked React 18 |
| Build | vite | ^6.0.0 | Vite 6, `@` alias configured |
| Routing | react-router-dom | ^6.30.0 | `BrowserRouter` already in `main.tsx` |
| Server state | @tanstack/react-query | ^5.100.0 | `QueryClientProvider` already wrapping app |
| Client state | zustand | ^5.0.0 | Minimal store exists (`useAppStore`) |
| HTTP | axios | ^1.16.0 | CVE-safe; `apiClient` with `withCredentials: true` exists |
| Styling | tailwindcss | ^3.4.0 | shadcn/ui configured (components.json present, no components generated yet) |
| Icons | lucide-react | ^0.460.0 | Already installed |
| Testing | vitest + @testing-library/react | ^3.0.0 / ^16.0.0 | jsdom env configured |

## 2. New Dependencies Required

| Package | Version | Purpose |
|---------|---------|---------|
| react-hook-form | ^7.55.0 | Form state management for login/signup |
| @hookform/resolvers | ^5.0.0 | Zod resolver integration |
| zod | ^3.23.0 | Schema validation (v3 per stack constraints) |
| sonner | ^2.0.0 | Toast notifications (shadcn/ui uses Sonner) |

**NOT needed for Phase 7:** react-leaflet, @dnd-kit/* (those are Phase 8).

## 3. Backend API Contracts (Source of Truth)

### 3.1 Auth Endpoints (`/api/auth/*`)

| Method | Path | Request | Response | Cookie |
|--------|------|---------|----------|--------|
| POST | `/api/auth/signup` | `{ email: string, password: string }` | `201 { userId: UUID }` | — |
| GET | `/api/auth/verify?token=...` | query param | `302` redirect to `${FRONTEND}/verify?status={success\|invalid\|expired}` | — |
| POST | `/api/auth/login` | `{ email: string, password: string }` | `200 { accessToken, expiresIn, user: { id, email, emailVerified } }` | `Set-Cookie: refresh_token=...` (httpOnly, path=/api/auth, 7d) |
| POST | `/api/auth/refresh` | — (cookie) | `200 { accessToken, expiresIn }` | `Set-Cookie: refresh_token=...` (rotated) |
| POST | `/api/auth/logout` | — (Bearer + cookie) | `204` | Clears cookie |

### Error Response Shape (RFC 7807 ProblemDetail)

```ts
type ApiError = {
  type: string;      // e.g. "about:blank"
  title: string;     // HTTP status text
  status: number;    // HTTP status code
  detail: string;    // Human-readable message (UI-SPEC verbatim)
  code: string;      // Machine-readable: "auth.invalid_credentials", "auth.rate_limited", etc.
};
```

**Error codes (verbatim from AuthControllerAdvice):**
- `auth.invalid_email` → 400 "Invalid email format."
- `auth.weak_password` → 400 "Password does not meet minimum requirements."
- `auth.invalid_credentials` → 400 "Email or password is incorrect."
- `auth.email_not_verified` → 403 "Please verify your email before logging in."
- `auth.token_invalid` → 400 "This verification link is invalid."
- `auth.token_expired` → 400 "This verification link has expired."
- `auth.refresh_invalid` → 401 "Session expired. Please log in again."
- `auth.rate_limited` → 429 "Too many attempts. Please try again later."
- `validation.failed` → 400 "Request validation failed."

### 3.2 Search Endpoint

| Method | Path | Params | Response |
|--------|------|--------|----------|
| GET | `/api/search` | `q` (string), `type` (default "city,country"), `limit` (default 5) | `200 { items: CitySearchItem[] }` |

```ts
type CitySearchItem = {
  type: string;      // "city" | "country"
  name: string;
  country: string;
  lat: number;
  lng: number;
};
```

### 3.3 Destination Endpoints

| Method | Path | Params | Response |
|--------|------|--------|----------|
| GET | `/api/destinations` | `lat`, `lng`, `radius` (default 5000), `limit` (default 20) | `200 NearbyResponse` |
| GET | `/api/destinations/:providerRef` | path param (format: `otm:xxx` or `fsq:xxx`) | `200 DestinationDetailResponse` or `404` |

```ts
type NearbyItem = {
  providerRef: string;
  name: string;
  category: string | null;
  rating: number | null;
  photoUrl: string | null;
  lat: number;
  lng: number;
};

type NearbyResponse = {
  items: NearbyItem[];
  fromCache: boolean;
  providerStatus: { openTripMap: string; foursquare: string };
};

type DestinationDetailResponse = {
  providerRef: string;
  name: string;
  category: string | null;
  shortDescription: string | null;
  rating: number | null;
  lat: number;
  lng: number;
  address: string | null;
  website: string | null;
  photos: string[];
  openingHours: Record<string, string> | null;
  fromCache: boolean;
  fetchedAt: string; // ISO instant
};
```

## 4. Architecture Patterns

### 4.1 Folder Structure (from docs/06-frontend-design.md)

```
src/
├── pages/              ← Route-level components (one per route)
├── features/           ← Cohesive feature modules
│   ├── auth/           ← AuthProvider, ProtectedRoute, useAuth, auth.api, auth.store
│   ├── search/         ← SearchInput, useSearch, search.api
│   └── destinations/   ← DestinationCard, DestinationList, PhotoCarousel, destinations.api
├── components/         ← Shared UI (shadcn/ui generated + custom)
│   ├── ui/             ← shadcn/ui primitives
│   ├── Layout.tsx      ← Header + nav + outlet
│   ├── EmptyState.tsx
│   └── LoadingSkeleton.tsx
├── api/                ← Already exists as src/lib/ (reuse)
├── types/              ← Shared TS types mirroring backend DTOs
└── hooks/              ← Shared custom hooks
```

### 4.2 Routing Strategy

```tsx
<Routes>
  <Route element={<Layout />}>
    <Route path="/" element={<HomePage />} />
    <Route path="/destinations/:providerRef" element={<DestinationDetailPage />} />
    <Route path="/login" element={<LoginPage />} />
    <Route path="/signup" element={<SignupPage />} />
    <Route path="/verify" element={<VerifyEmailPage />} />
    {/* Phase 8 routes behind ProtectedRoute */}
    <Route path="*" element={<NotFoundPage />} />
  </Route>
</Routes>
```

### 4.3 Auth State Management

**Zustand store (`auth.store.ts`):**
```ts
type AuthState = {
  accessToken: string | null;
  user: { id: string; email: string; emailVerified: boolean } | null;
  addToTripContext: { destinationRef: string } | null;
  setSession: (token: string, user: User) => void;
  clearSession: () => void;
  setAddToTripContext: (ctx: { destinationRef: string } | null) => void;
};
```

- Access token in memory only (Zustand) — never localStorage
- Refresh token in httpOnly cookie (handled by browser/backend)
- On page load: call `/api/auth/refresh` once to restore session
- On 401 from any request: trigger refresh flow (Pitfall 9 pattern)

### 4.4 Axios 401 Interceptor (Pitfall 9 — CRITICAL)

The interceptor MUST implement:
1. **`isRefreshing` flag** — prevents multiple concurrent refresh calls
2. **`failedQueue`** — queues parallel failed requests while refresh is in-flight
3. **Skip list** — never retry `/auth/refresh` or `/auth/login` endpoints
4. **On refresh failure** — clear Zustand auth state, clear QueryClient cache, do NOT redirect (let components react to null user)

Full implementation from PITFALLS.md §Pitfall 9 (verified TypeScript):
```ts
let isRefreshing = false;
let failedQueue: Array<{resolve: (t: string) => void; reject: (e: unknown) => void}> = [];

apiClient.interceptors.response.use(undefined, async (error) => {
  const original = error.config;
  if (error.response?.status !== 401 || original._retry) return Promise.reject(error);
  if (original.url?.includes('/auth/refresh') || original.url?.includes('/auth/login'))
    return Promise.reject(error);
  if (isRefreshing) {
    return new Promise((resolve, reject) => {
      failedQueue.push({
        resolve: (token: string) => {
          original.headers.Authorization = `Bearer ${token}`;
          resolve(apiClient(original));
        },
        reject,
      });
    });
  }
  original._retry = true;
  isRefreshing = true;
  try {
    const { data } = await apiClient.post<{ accessToken: string }>('/api/auth/refresh');
    useAuthStore.getState().setSession(data.accessToken, useAuthStore.getState().user!);
    original.headers.Authorization = `Bearer ${data.accessToken}`;
    failedQueue.forEach(p => p.resolve(data.accessToken));
    return apiClient(original);
  } catch (e) {
    failedQueue.forEach(p => p.reject(e));
    useAuthStore.getState().clearSession();
    queryClient.clear();
    return Promise.reject(e);
  } finally {
    isRefreshing = false;
    failedQueue = [];
  }
});
```

### 4.5 TanStack Query Configuration

- Default `retry: 1` already set in `queryClient.ts`
- For auth-related queries, override with `retry: false` (prevent retry on 401)
- Query key conventions:
  - `['search', q]` — city search
  - `['destinations', { lat, lng, radius }]` — nearby attractions
  - `['destination', providerRef]` — detail view

### 4.6 Search UX Pattern (D-01 through D-04)

1. **Debounced input** (250ms) using a custom `useDebounce` hook
2. **Dropdown suggestions** — overlay showing max 5 city results
3. **On selection** — fetch nearby destinations for that city's lat/lng
4. **Results below** — responsive card grid (1/2/3 cols)
5. All inline on home page (no route transition for search)

## 5. shadcn/ui Components Needed

Generate via `pnpm dlx shadcn@latest add <component>`:

| Component | Used For |
|-----------|----------|
| button | CTAs, form submissions |
| input | Search input, form fields |
| card | Destination cards |
| form | Login/signup forms (RHF integration) |
| label | Form field labels |
| badge | Category badges on cards |
| toast (sonner) | Success/error notifications |
| skeleton | Loading states |
| dropdown-menu | City suggestions overlay |
| dialog | Future: AddToTrip (not yet needed) |
| popover | D-14: logged-out "Add to Trip" tooltip |
| separator | Visual dividers |
| avatar | User nav display |

## 6. Critical Implementation Notes

### 6.1 Deferred Login Intent (TRIP-05)

The flow for "Add to Trip" when logged out:
1. Click "Add to Trip" → show popover: "Log in to add destinations to your trip" + "Log in" button
2. Store `{ destinationRef }` in Zustand `addToTripContext`
3. Navigate to `/login`
4. After successful login, check `addToTripContext`:
   - If present: navigate back to `/destinations/:providerRef` + auto-open AddToTrip dialog
   - If absent: navigate to `/trips` (or `next` query param)

**Note:** The actual "Add to Trip" dialog/mutation is Phase 8. Phase 7 only needs the deferred intent storage and the login-redirect-back flow. The button should be visible but disabled with the popover when logged out.

### 6.2 Session Restoration on Page Load

On app mount (in `AuthProvider` or a top-level hook):
1. Call `POST /api/auth/refresh` (cookie is sent automatically)
2. If success: store access token + decode user from response
3. If failure (401): user is logged out, no error shown
4. Show a loading state during this check (prevents flash of logged-out UI)

### 6.3 JWT Decoding

The SPA does NOT have a `/me` endpoint. User info comes from:
- **Login response:** `{ accessToken, expiresIn, user: { id, email, emailVerified } }`
- **Refresh response:** Only `{ accessToken, expiresIn }` — keep user from the login response in Zustand

If user info is needed after a refresh (e.g., after page reload where Zustand is empty), decode it from the JWT payload (base64url decode the second segment). This avoids a /me endpoint.

### 6.4 Form Validation (Zod Schemas)

```ts
// Login schema
const loginSchema = z.object({
  email: z.string().email("Invalid email format."),
  password: z.string().min(8, "Password must be at least 8 characters."),
});

// Signup schema (same fields, same validation)
const signupSchema = z.object({
  email: z.string().email("Invalid email format."),
  password: z.string().min(8, "Password must be at least 8 characters."),
});
```

Validation happens client-side first (RHF + Zod), then server errors from `ProblemDetail.detail` are shown as toasts.

### 6.5 Error Handling Strategy

| Error Type | Handling |
|------------|----------|
| Field validation (Zod) | Inline per-field red text |
| Server validation (`auth.invalid_email`, `auth.weak_password`) | Inline per-field (map `code` to field) |
| Auth errors (`auth.invalid_credentials`, etc.) | Toast notification |
| Network errors | Toast: "Network error. Please try again." |
| 404 on destination detail | Show NotFound page |
| 401 intercepted | Automatic refresh attempt; if fails, clear session |

## 7. Don't Hand-Roll

| Concern | Use This | Not This |
|---------|----------|----------|
| Toast | shadcn/ui toast (Sonner) | Custom portal toasts |
| Form validation | React Hook Form + Zod | Manual useState + onChange |
| Debounce | Custom `useDebounce` hook (5 lines) | lodash.debounce (tree-shaking issues) |
| Route guards | `ProtectedRoute` component with `<Navigate>` | useEffect-based redirects |
| Loading states | shadcn/ui Skeleton | Spinner components |
| HTTP client | Existing `apiClient` singleton | New axios instances |

## 8. Common Pitfalls (Phase 7 Specific)

1. **Pitfall 9 (Axios 401 loop):** Never retry `/auth/refresh` or `/auth/login`; use `isRefreshing` flag + `failedQueue`. Test: network tab shows exactly ONE failed refresh attempt then session clears.

2. **TanStack Query retry on 401:** Default `retry: 1` will retry 401s unless overridden. Set `retry: (failureCount, error) => error.response?.status !== 401` globally OR `retry: false` on auth queries.

3. **Refresh token cookie path:** Cookie is scoped to `/api/auth` — only `/api/auth/refresh` and `/api/auth/logout` requests will send it. Don't try to read the cookie in JS (it's httpOnly).

4. **Flash of logged-out state:** On page reload, Zustand resets to null. If the first render shows logged-out nav and then flips to logged-in after refresh succeeds, it's jarring. Use an `isInitializing` flag to show a skeleton/spinner during the initial refresh attempt.

5. **CORS with credentials:** `withCredentials: true` requires the backend CORS config to use a specific origin (not `*`). The gateway's CORS is already configured for `localhost:5173`.

6. **shadcn/ui Tailwind v3 path:** When running `pnpm dlx shadcn@latest add`, the CLI may default to Tailwind v4. The existing `components.json` is correctly configured for Tailwind v3 + CSS variables, so generated components should work. If prompted for framework, select "Vite".

## 9. Validation Architecture

### Dimension 1: Functional Correctness
- Search returns results matching backend contract shape
- Auth flow completes end-to-end (signup → verify → login → authenticated state)
- Deferred intent persists across login redirect

### Dimension 2: Integration Correctness
- Axios interceptor correctly handles 401 → refresh → retry
- TanStack Query cache invalidates on auth state change
- Zustand auth store syncs with interceptor state

### Dimension 3: Error Handling
- ProblemDetail errors parsed and displayed correctly
- Network failures show toast, not crash
- 401 on refresh clears session cleanly

### Dimension 4: Security
- No access token in localStorage (memory-only via Zustand)
- Refresh token never readable by JS (httpOnly cookie)
- CSRF: not applicable (SameSite=Strict cookie + bearer token)
- XSS: React's JSX escaping + no dangerouslySetInnerHTML

### Dimension 5: User Experience
- No flash of logged-out state on page reload
- Exactly one failed refresh shown in network tab (not infinite)
- Search debounce prevents request flood
- Loading skeletons prevent layout shift

---

*Research complete. Ready for planning.*
