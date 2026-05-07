# 01 — Product Requirements Document (PRD)

**Status**: Draft for review
**Last updated**: 2026-05-08
**Source**: `Trip Planner Feature List.pdf`

## 1. Vision

Help travelers go from "I want to visit somewhere" to a structured day-by-day
itinerary in minutes — without spreadsheet spaghetti or losing track of saved
places across browser tabs.

## 2. Problem statement

Casual trip planners today juggle Google Maps, hotel sites, "things to do"
articles, and notes apps. Saved places are scattered. There is no single place
that combines: discoverability of attractions, day-level scheduling, and
persistence across sessions, that is also free to use.

## 3. Personas

### P1 — Solo Traveler ("Mai", primary)
- Plans 1–2 week leisure trips 3–4 times a year.
- Mostly works on a laptop, occasionally adjusts trips on phone.
- Wants to discover attractions she hasn't heard of.
- Needs to remember what she saved across sessions.

### P2 — Returning User ("Long")
- Has built ≥3 trips. Has a personal shortlist of favorite places.
- Reuses ideas across trips. Wants reorder/move-day flexibility.
- Cares about a clean overview of his trip on a map.

## 4. Scope

### 4.1 In scope (v1)

| Group | Source | Items |
|-------|--------|-------|
| Authentication | F4 | Sign up, email verification, login, logout |
| Destination discovery | F1 | City/country search, recommended attractions list |
| Destination details | F2 | Detail view, photos, opening hours |
| Trip planning | F3 | Create trip, set date range, add/remove destinations to days, login-gated saves, restore saved trips on return |
| **In-day scheduling** ★ | NEW | Drag-drop reorder within day, move between days, optional time slot per item |
| **Map view** ★ | NEW | All destinations of a trip on an interactive map |
| **Personalization** ★ | NEW | Per-item notes, favorited destinations, trip cover image |

★ = added during design to make the product actually useful for the persona's
job-to-be-done; not in original feature list.

### 4.2 Out of scope (v1 → tracked in v2 backlog, see 09-roadmap)

- Trip sharing via public link
- Trip duplication / templates
- Real-time multi-user collaboration
- Native mobile apps (web is responsive)
- Push notifications, email reminders
- Budget tracking
- Weather forecast integration
- Travel time / distance between items
- PDF / .ics export
- Internationalization, multi-currency

## 5. Functional requirements

Each FR is traceable to a source user story (F#-US#) or marked NEW. Acceptance
criteria preserved verbatim from the source where applicable.

### FR-1 City/country search (F1-US1, High)
User can type ≥1 character and see up to 5 ranked city/country results.
Case-insensitive partial match. No duplicates. "No results" message when empty.
Selecting a result becomes the active search value. Performance per NFR-1.

### FR-2 Recommended attractions (F1-US2, High)
After a valid location search, return up to 20 attractions for that location
with: name, category/tags, popularity/rating, thumbnail, placeholder when any
field missing. Default radius for city = 20 km. For country, return top
attractions in major cities OR require user to pick a city first.

### FR-3 Destination detail view (F2-US1, Medium)
User can open a detail view from the list showing: name, category, short
description, photos (when available), address, opening hours, website,
"Add to Trip" CTA. Detail must open even when some fields are missing.
"Add to Trip" disabled when not logged in.

### FR-4 Photo carousel (F2-US2, Medium)
View ≥1 photo when available. Swipe through multiple. Placeholder when none.

### FR-5 Opening hours (F2-US3, Low)
Display when present. Show "Opening hours not available" when missing.

### FR-6 Create trip (F3-US1, High)
User can create a trip with a required name. Trip appears in "My Trips" list.
Planner opens after creation.

### FR-7 Set trip date range (F3-US2, High)
User can set start/end dates (start ≤ end). One itinerary day is materialized
per date in range. Reducing range that would drop planned items requires
confirmation.

### FR-8 Add destination to trip (F3-US3, High)
User can add a destination from list view OR from detail view. User picks the
target day. Destination appears immediately under the chosen day in the
selected trip.

### FR-9 Remove destination from itinerary (F3-US7, High)
User can remove an item from a day. Item disappears immediately after confirm.

### FR-10 Login required to save (F3-US8, Medium)
Browse without login. Logged-out users attempting to create-trip or
add-destination are prompted to log in. After successful login, original
action completes and user lands on the same page.

### FR-11 Restore saved trips on return (F3-US10, Medium)
After login, user sees their previously created trips, items, and days. Empty
state when no saved trips.

### FR-12 Sign up with email and password (F4-US1, Medium)
Email + password sign-up. Email must be unique. Password min 8 characters.
Generic error messages (no enumeration). User signed in upon successful sign-up
but cannot use authenticated actions until verified (FR-13).

### FR-13 Verify email (F4-US2, Medium)
Verification email with unique link sent on signup. Link activates account.
Login blocked before verification. Show success on verify, error on
invalid/expired token.

### FR-14 Log in (F4-US3, Medium)
Email + password login. Generic error on bad credentials. Session persists
across page refresh.

### FR-15 Log out (F4-US4, Medium)
"Log out" ends session immediately. App returns to logged-out state.
Authenticated routes inaccessible.

### FR-16 Drag-drop reorder within day (NEW, High)
Logged-in user can drag an itinerary item to a new position within the same
day. Order persists immediately. Optimistic update with rollback on server
error.

### FR-17 Drag item between days (NEW, High)
Logged-in user can drag an itinerary item from one day to another within the
same trip. Order persists.

### FR-18 Optional time slot per item (NEW, Medium)
Each item can have an optional time-of-day (HH:mm). Display sorted by time
when set, otherwise by position.

### FR-19 Trip map view (NEW, Medium)
For a trip with at least one geocoded item, render all items on an interactive
map. Each marker shows item name on click. Map auto-fits to bounds of items.

### FR-20 Per-item notes (NEW, Medium)
Each itinerary item can have a free-text note (≤500 chars). Editable inline.
Server-side sanitized to prevent stored XSS.

### FR-21 Favorite destinations (NEW, Medium)
Logged-in user can favorite/unfavorite a destination from list or detail view.
"My Favorites" page lists all favorites and supports add-to-trip from there.

### FR-22 Trip cover image (NEW, Low)
User can set a cover image URL for a trip (defaults to first item's photo if
unset). Displayed on trip card and trip header.

## 6. Non-functional requirements

| ID | Category | Requirement | Source |
|----|----------|-------------|--------|
| NFR-1 | Performance | Search returns within 500ms p95 for cached/seeded results | PDF NFR1 |
| NFR-2 | Authorization | Users can only read/modify their own trips, items, favorites | PDF NFR2 |
| NFR-3 | Resilience | If OpenTripMap or Foursquare is down, search/details still work using cached data; user sees a "Showing cached results" indicator | NEW |
| NFR-4 | Cost | All external services in v1 must operate on free tiers. No credit-card-required signups. | NEW |
| NFR-5 | Quality | Backend service-layer line coverage ≥70%. Auth + ownership-check paths 100%. | NEW |
| NFR-6 | Security | OWASP Top 10 explicitly addressed (see 05-auth-security.md threat model) | NEW |
| NFR-7 | Accessibility | All interactive controls keyboard-navigable; color contrast WCAG AA on text | NEW |
| NFR-8 | Mobile | Trip planner usable on screens ≥360px wide (responsive, not native app) | NEW |
| NFR-9 | Observability | All inter-service requests carry W3C trace context; structured JSON logs | NEW |

## 7. Success metrics (for self-evaluation)

Since this is a portfolio project, "success" is technical-quality oriented:

- Demo: a fresh user can sign up → verify → log in → search "Tokyo" → see 20 attractions → create a 5-day trip → drag 3 items into 3 days → see them on the map, in under 5 minutes.
- Tests pass in CI on a green main branch with ≥70% backend coverage.
- Lighthouse performance ≥90 on the trip detail page.
- p95 search latency ≤500ms locally with cached results.

## 8. Out-of-scope clarifications

- **Payments / subscriptions**: not in v1; product is free.
- **Admin tools**: no admin UI. Database access via psql / pgAdmin only.
- **GDPR-grade data export**: not in v1 (out-of-band per request only).
- **Multi-language**: English only in v1.
- **Mobile native**: web responsive only; PWA deferred to v2.

## 9. Assumptions

- A1: External free-tier rate limits are sufficient for portfolio-scale traffic (<100 users).
- A2: GeoNames cities-15000 data set is acceptable seed for city/country search (~25k cities, public domain).
- A3: A single Postgres instance with per-service schemas is acceptable for v1 (v2 may split).
- A4: Mailhog/Mailtrap for email in dev; SMTP relay (Resend/SendGrid free tier) for any deployed env.
- A5: User uploads (cover image) accepted as URL only in v1 (no file upload to v1 — defer S3 integration).

## 10. Open questions for review

> Items below are flagged for the reviewer to confirm or revise.

- OQ-1: Should the cover-image field accept user-uploaded files in v1, or URL-only? (Current assumption: URL-only.)
- OQ-2: For country search results (FR-2), pick "show top attractions in capital city" vs "force user to pick a city". Current design recommends forcing city pick to keep UX consistent.
- OQ-3: Item time slot (FR-18) — store as `LocalTime` (no timezone) or as `Instant`? Current design: `LocalTime`, since trips can span timezones and storing absolute timestamps creates display ambiguity.
