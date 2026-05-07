# Feature Research

**Domain:** Trip-planning / travel-itinerary web application
**Researched:** 2026-05-08
**Confidence:** MEDIUM-HIGH (product behaviors verified via official product sites and multiple review sources; market categorization is analyst synthesis)

---

## Competitive Reference Products

This analysis draws on seven landmark products across the category spectrum:

| Product | Category | Key Positioning |
|---------|----------|-----------------|
| **Wanderlog** | Full itinerary planner | Google Docs-style collaboration + map; 1M+ users; free tier generous |
| **TripIt** | Reservation organizer | Email-parsing auto-import; strength in post-booking logistics |
| **Sygic Travel (Tripomatic)** | POI-first planner | 50M places database; offline maps; day-by-day scheduling |
| **Roam Around / Layla** | AI itinerary generator | One-prompt itinerary generation; 10M+ itineraries created |
| **Hopper** | Price-prediction booking | AI fare prediction; price-freeze; booking-first not planning-first |
| **Google Maps "Lists"** | Saved-places lightweight | Favorite pinning + sharing; no structured day planning |
| **Notion Travel Templates** | General-purpose workspace | Free-form flexibility; zero built-in travel logic |

---

## Feature Landscape

### Table Stakes (Users Expect These)

Features users assume exist. Missing these = product feels incomplete or broken.

| Feature | Why Expected | Complexity | In v1? | v1 FRs |
|---------|--------------|------------|--------|--------|
| **Destination / attraction search** | Every planner (Wanderlog, Sygic, Google Maps) has search as entry point; users arrive with a city in mind | LOW | YES | FR-1, FR-2 |
| **Attraction detail view** (name, photo, description, hours, address) | Wanderlog, Sygic, TripIt all show rich place cards; users need context before adding to trip | LOW-MEDIUM | YES | FR-3, FR-4, FR-5 |
| **Day-by-day itinerary structure** | Wanderlog, Sygic, Tripomatic all organize around calendar days; users think in "Day 1, Day 2" | MEDIUM | YES | FR-6, FR-7, FR-8, FR-9 |
| **Add/remove destinations from days** | Core CRUD. Any planner without this is a viewer, not a planner | LOW | YES | FR-8, FR-9 |
| **Persistent sessions / saved trips** | Users plan over multiple sessions; TripIt and Wanderlog both require login to persist | LOW | YES | FR-11, FR-14 |
| **Email + password authentication** | Industry standard for persistence; every product uses auth to associate trips with users | LOW | YES | FR-12, FR-13, FR-14, FR-15 |
| **Map view of trip** | Wanderlog, Sygic, Google Maps all show trip pins on a map; spatial awareness is a core mental model | MEDIUM | YES | FR-19 |
| **Photo carousel for attractions** | Wanderlog and Sygic show multiple photos per POI; single photo feels like a stub | LOW | YES | FR-4 |
| **Opening hours display** | Sygic and Wanderlog both surface hours in detail view; absence creates planning anxiety | LOW | YES | FR-5 |
| **Empty states and "no results" feedback** | Basic UX table stake; missing this makes app feel broken | LOW | YES | Implicit in FR-1, FR-11 |
| **Responsive / mobile-usable interface** | Over 80% of travelers use mobile during trip research (Shivlab); planning continues on phone | MEDIUM | YES | NFR-8 |

**Summary:** All 22 v1 FRs map to table-stakes delivery. The v1 scope does not attempt differentiators at the expense of basics — it correctly lands the entire table-stakes layer first.

---

### Differentiators (Competitive Advantage)

Features that set a product apart. Not required, but valued.

| Feature | Value Proposition | Complexity | In v1? | v1 FRs / Notes |
|---------|-------------------|------------|--------|----------------|
| **Drag-drop reorder within day** | Wanderlog has this; most Notion templates do not; lets users intuitively fine-tune sequence without editing fields | MEDIUM | YES | FR-16 — differentiates from Notion templates and basic list tools |
| **Drag item between days** | Wanderlog supports cross-day moves; competitors like Google Maps Lists do not have days at all | MEDIUM | YES | FR-17 — elevates above Google Maps Lists |
| **Optional time slot per item** | Sygic shows estimated travel times; assigning HH:mm lets users slot around fixed reservations (restaurant bookings, tours) | LOW-MEDIUM | YES | FR-18 — differentiates from pure unordered lists |
| **Per-item free-text notes** | TripIt stores booking confirmations but not user intent notes; Wanderlog has notes; useful for "must order X here" or "closes Monday" reminders | LOW | YES | FR-20 — adds personal context layer |
| **Favorites page** | Wanderlog has saved places; Google Maps "Want to Go" is popular; cross-session wishlist separate from trip planning is a power-user workflow | LOW-MEDIUM | YES | FR-21 |
| **Trip cover image** | Wanderlog shows visual trip cards; makes trips visually scannable in list view; low effort, high visual impact | LOW | YES | FR-22 |
| **Login-deferred "add to trip" flow** | Wanderlog requires login upfront; deferring login until the moment of save reduces conversion friction | MEDIUM | YES | FR-10 — explicit UX differentiator over competitors that gate browsing |
| **Email verification before login** | Security hardening above industry norm for portfolio tools; demonstrates OWASP awareness | LOW | YES | FR-13 — demonstrates security discipline |
| **Optimistic UI with rollback on drag-drop** | Most tutorial-grade apps skip this; visible in Wanderlog's implementation; shows production-level React discipline | MEDIUM | YES | FR-16 AC |
| **Provider circuit breaker + degraded-mode indicator** | Enterprise pattern rarely seen in portfolio apps; Resilience4j circuit breaker with UI feedback is a strong reviewer signal | HIGH | YES | NFR-3 |
| **W3C trace context + structured JSON logs** | Distributed tracing is invisible to users but visible to tech reviewers; demonstrates microservices operational maturity | HIGH | YES | NFR-9 |

**Note for portfolio defense:** FR-16 through FR-22 were added during design ("NEW" in PRD) precisely to clear the bar from "working CRUD app" to "product that a real traveler would actually use." The differentiators are deliberate, not accidental — each one addresses a named persona pain point (Mai's discovery loop, Long's reordering workflow).

---

### Anti-Features (Deliberately NOT Built)

Features that seem logical but were correctly excluded from v1 — with explicit rationale for portfolio defense.

| Anti-Feature | Surface Appeal | Why Excluded (v1) | What's Done Instead | In v2 Backlog? |
|--------------|---------------|-------------------|---------------------|----------------|
| **Real-time multi-user collaboration** | Wanderlog's #1 differentiator; "Google Docs for trips" | Requires CRDTs or pessimistic locking; adds WebSocket/SSE infrastructure; operationally complex for a solo portfolio build | Single-user persistence done correctly; sharing deferred | YES (multi-traveler collaboration) |
| **Trip sharing via public link** | Expected by group travelers; Wanderlog and Google Maps both support link sharing | Adds auth-mode duality (public vs authenticated routes); public routes require separate gateway config and token-less access control | Users share by demoing the app directly; scoped out as v2 | YES |
| **AI itinerary generation** | Roam Around/Layla generate entire itineraries from a prompt; hot feature in 2026 | Requires LLM integration (OpenAI/Anthropic API) — paid tier; non-trivial prompt engineering; not a Spring Boot microservices skill demonstration | Manual curation via search + drag-drop demonstrates UX discipline better | NO (out of portfolio scope) |
| **Email-forwarding / booking import** | TripIt's core value prop; auto-populates trip from confirmation emails | Email parsing infrastructure (IMAP/MIME parsing, NLP extraction) is a separate product surface; no free-tier IMAP provider | User manually adds destinations found via search | NO |
| **Budget tracking / expense splitting** | Wanderlog has this; Stippl markets it as a differentiator | Adds a separate data model (expenses, currencies, splits); currency conversion requires a free FX feed; scope creep for planning-loop focus | Core planning loop ships clean; budget is v2 | YES |
| **Weather forecast integration** | Open-Meteo is free; Wanderlog and many competitors show weather | Weather is useful but does not affect the core add-to-trip flow; adds an external API dependency without changing what a user plans | Date range visible on trip; weather deferred to v2 | YES |
| **Travel time / routing between items** | Sygic shows walking/driving times; Wanderlog Pro offers route optimization | Requires routing API (OSRM, Google Directions); OSRM public instance is rate-limited; adds map overlay complexity | Map view shows spatial layout; time estimates deferred | YES |
| **PDF / .ics export** | TripIt exports .ics for calendar; users want offline printable plans | Requires server-side rendering (PDFKit, OpenHTMLtoPDF) or .ics generation (ical4j); out-of-band concern from core planning | Trip is accessible in-app on mobile (NFR-8) | YES |
| **OAuth (Google / GitHub) login** | Reduces signup friction; expected by modern users | Spring Authorization Server adds significant setup overhead; HS256 JWT + email auth demonstrates the auth pattern just as well | Email + password with email verification; OAuth is v2 | YES |
| **File-uploaded cover image** | More natural than URL; avoids needing external image URLs | Requires S3/pre-signed URL pipeline + image processing; storage cost on free tier; not a planning feature | URL-only cover image; avoids cloud storage dependency in v1 | YES |
| **Offline access / PWA** | Sygic's premium feature; critical for international travel | Service worker caching + IndexedDB sync is a frontend architecture project in itself; adds complexity disproportionate to portfolio signal | Responsive web (NFR-8) covers most adjustment use cases at wifi-available moments | YES (PWA) |
| **Push / email notifications** | "Trip starts in 7 days" — Wanderlog-style reminders | VAPID keys, background cron jobs, deliverability ops; entirely separate concern from trip planning logic | Email verification email demonstrates SMTP integration already | YES |

---

## Feature Dependencies

```
Authentication (FR-12 → FR-15)
    └──required by──> Trip CRUD (FR-6 → FR-11)
                         └──required by──> In-day scheduling (FR-16, FR-17, FR-18)
                         └──required by──> Favorites (FR-21)
                         └──required by──> Notes (FR-20)
                         └──required by──> Cover image (FR-22)

Destination Discovery (FR-1, FR-2)
    └──required by──> Destination Detail (FR-3, FR-4, FR-5)
                         └──required by──> Add to Trip (FR-8)

Add to Trip (FR-8)
    └──required by──> Map View (FR-19)  [needs geocoded items]
    └──required by──> Drag-drop (FR-16, FR-17)  [needs items to drag]

Trip Date Range (FR-7)
    └──required by──> Day materialization
                         └──required by──> Drag between days (FR-17)

Login-deferred flow (FR-10)
    └──enhances──> Add to Trip (FR-8)  [reduces signup friction before save]

Time Slot (FR-18)
    └──enhances──> Drag-drop ordering (FR-16)  [provides sort signal when set]

Favorites (FR-21)
    └──enhances──> Add to Trip (FR-8)  [add-to-trip CTA present on Favorites page]
```

### Dependency Notes

- **Auth gates everything writable:** FR-6 through FR-22 (all writes) require a verified session. Browsing (FR-1 through FR-5) is explicitly public. This is the correct split and matches all competitor implementations.
- **Day materialization is the load-bearing abstraction:** FR-7's idempotent reconcile of days against a date range is what makes FR-16/FR-17 (cross-day drag) coherent. Skipping or weakening this creates orphaned items — a critical correctness bug.
- **Map view is a read-only derived view:** FR-19 depends on items having geocoordinates, which come from the destination service (FR-2/FR-3). No separate geocoding pipeline needed because coordinates come from OpenTripMap POI data.
- **Cover image is cosmetic, not structural:** FR-22 has zero upstream dependencies and zero downstream consumers. It is safe to defer if time pressure hits Phase 6.

---

## MVP Definition

### Launch With (v1) — All 22 FRs

The v1 scope is already the MVP. No further reduction is recommended for portfolio purposes — the 22 FRs collectively demonstrate: end-to-end auth, external API integration, microservice orchestration, drag-drop UX, and map rendering. Cutting any group (e.g., removing map view or drag-drop) would demonstrate a narrower skill set.

- [x] **Authentication** (FR-12 to FR-15) — gate for everything else; demonstrates security discipline
- [x] **Destination discovery + detail** (FR-1 to FR-5) — entry point to the planning loop; covers external API integration
- [x] **Trip CRUD + day materialization** (FR-6 to FR-11) — the core planning loop; demonstrates date-range logic
- [x] **Drag-drop scheduling** (FR-16 to FR-18) — converts this from a list app to an itinerary tool; demonstrates React dnd-kit and optimistic UI
- [x] **Map view** (FR-19) — spatial overview; demonstrates Leaflet/OSM integration without billing risk
- [x] **Personalization** (FR-20 to FR-22) — notes, favorites, cover image; makes the product feel complete vs a tech demo

### Add After Validation (v1.x — near-term)

Triggers: positive portfolio reviewer feedback; decision to make app publicly accessible.

- [ ] **Trip sharing via public link** — first request from any user who wants to share a trip
- [ ] **OAuth login (Google)** — drops signup friction for a real user audience
- [ ] **Weather integration** — high discoverability, low-effort via Open-Meteo free API

### Future Consideration (v2+ — see docs/09-roadmap.md)

- [ ] **Real-time collaboration** — only meaningful once multi-user adoption exists
- [ ] **Budget tracking** — adds a second workflow that competes with the planning-loop focus
- [ ] **AI itinerary generation** — requires paid API budget and product direction rethink
- [ ] **Native mobile app** — only after web product is stable and has real users

---

## Feature Prioritization Matrix

| Feature Group | User Value | Implementation Cost | Priority |
|---------------|------------|---------------------|----------|
| Auth (FR-12 to FR-15) | HIGH (gates saving) | MEDIUM | P1 |
| City search (FR-1) | HIGH (entry point) | LOW (seeded DB) | P1 |
| Attraction list + detail (FR-2 to FR-5) | HIGH (discovery loop) | MEDIUM (external API) | P1 |
| Trip CRUD + days (FR-6 to FR-9, FR-11) | HIGH (core loop) | MEDIUM | P1 |
| Login-deferred flow (FR-10) | MEDIUM (reduces friction) | MEDIUM (state machine) | P1 |
| Drag-drop (FR-16, FR-17) | HIGH (differentiator) | MEDIUM (dnd-kit) | P1 |
| Time slot (FR-18) | MEDIUM (power-user) | LOW | P2 |
| Map view (FR-19) | HIGH (spatial overview) | MEDIUM (Leaflet) | P1 |
| Per-item notes (FR-20) | MEDIUM (personalization) | LOW | P2 |
| Favorites (FR-21) | MEDIUM (wishlist) | MEDIUM | P2 |
| Cover image URL (FR-22) | LOW (cosmetic) | LOW | P3 |
| Provider circuit breaker (NFR-3) | LOW for users, HIGH for reviewers | HIGH | P1 (portfolio signal) |
| Distributed tracing (NFR-9) | LOW for users, HIGH for reviewers | MEDIUM | P1 (portfolio signal) |

**Priority key:**
- P1: Must have for v1 launch / portfolio demo
- P2: Should ship in v1; defer only under severe time constraint
- P3: Nice to have; cut-safe if phases run long

---

## Competitor Feature Analysis

| Feature | Wanderlog | TripIt | Sygic Travel | Roam Around | Google Maps Lists | Notion Templates | **This v1** |
|---------|-----------|--------|--------------|-------------|-------------------|-----------------|-------------|
| Day-by-day itinerary | YES (drag-drop) | YES (auto-built from bookings) | YES | YES (AI-generated) | NO (flat list only) | Manual (no logic) | YES (FR-6/7/8) |
| Drag-drop reorder | YES | NO | LIMITED | NO | NO | NO | YES (FR-16/17) |
| Map of trip items | YES (Google Maps) | NO | YES (offline capable) | NO | YES (base Maps) | NO | YES (FR-19, Leaflet/OSM) |
| Destination search | YES | NO (import only) | YES (50M POIs) | AI-generated list | YES | NO | YES (FR-1/2) |
| Per-item notes | YES | YES (on bookings) | YES | NO | YES (place notes) | YES (free-form) | YES (FR-20) |
| Favorites / saved places | YES | YES | YES | NO | YES (Want to Go) | Manual list | YES (FR-21) |
| Trip cover image | YES | NO | NO | NO | NO | Manual | YES (FR-22) |
| Real-time collaboration | YES (Google Docs-style) | NO | YES (invite) | NO | YES (link share) | YES (Notion native) | NO (v2) |
| Budget tracking | YES | NO | YES | NO | NO | Manual | NO (v2) |
| Email booking import | NO | YES (core) | NO | NO | NO | NO | NO (anti-feature) |
| AI itinerary generation | Pro only | Partial (2025) | YES | YES (core) | Gemini integration | AI assist | NO (anti-feature) |
| Offline maps | Pro only | NO | YES (premium) | NO | Partial | NO | NO (v2 PWA) |
| Weather forecast | NO | NO | NO | Basic info | NO | NO | NO (v2) |
| Travel time between items | Pro only | NO | YES | NO | YES (directions) | NO | NO (v2) |
| Mobile app | YES (iOS/Android) | YES | YES | YES | YES | YES | Responsive web (NFR-8) |

**Portfolio positioning takeaway:** This v1 matches or exceeds Wanderlog's core free-tier feature set (itinerary builder, drag-drop, map, notes, favorites) while intentionally omitting features that require paid APIs (AI, routing), live infrastructure (collaboration, notifications), or separate product surfaces (booking import, budget). The deliberate omissions are defensible because the added value does not come from feature breadth — it comes from demonstrating production-grade engineering (microservices, distributed tracing, circuit breakers, OWASP compliance) behind a clean feature set.

---

## Sources

- Wanderlog product review: [Wandrly detailed review](https://www.wandrly.app/reviews/wanderlog) — MEDIUM confidence (third-party review, cross-validated with official site)
- TripIt review and features: [Going.com TripIt Review 2026](https://www.going.com/guides/tripit-review) — MEDIUM confidence
- Sygic Travel / Tripomatic: [App Store listing](https://apps.apple.com/us/app/tripomatic-trip-planner-maps/id519058033) and [Sygic Travel site](https://www.sygic.com/travel) — MEDIUM confidence
- Roam Around / Layla AI: [roamaround.io](https://roamaround.io/) and [NexusAI profile](https://www.nexusai-tech.com/ai-apps/roam-around-ai-travel-itinerary-planner) — MEDIUM confidence
- Hopper features: [Hopper App Review 2025](https://www.thetraveler.org/hopper-app-review-2025-still-worth-downloading/) — MEDIUM confidence
- Google Maps Lists: [TechCrunch Google Maps vacation features 2025](https://techcrunch.com/2025/03/27/google-rolls-out-new-vacation-planning-features-to-search-maps-and-gemini/) — HIGH confidence (TechCrunch / Google official announcement)
- AI travel planner landscape: [Stippl Best AI Travel Planner 2026](https://www.stippl.io/blog/best-ai-travel-planner-2026) — MEDIUM confidence
- Must-have features analysis: [Shivlab Top Features 2025](https://shivlab.com/blog/top-features-for-travel-planning-app/) — LOW-MEDIUM confidence (vendor blog, but consistent with other sources)
- dnd-kit: [dndkit.com official](https://dndkit.com/) — HIGH confidence (official docs)
- Wanderlog vs TripIt comparison: [Wanderlog blog](https://wanderlog.com/blog/2024/11/26/wanderlog-vs-tripit/) — MEDIUM confidence (vendor content, acknowledged bias)

---

*Feature research for: Trip-planning / travel-itinerary web application*
*Researched: 2026-05-08*
