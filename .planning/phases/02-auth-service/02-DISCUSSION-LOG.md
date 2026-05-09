# Phase 2: Auth Service - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-09
**Phase:** 2-Auth Service
**Areas discussed:** Email send + verification UX, Login rate limit (IP+email leg), Refresh rotation reuse + logout scope, BL-01 fix + Phase 2 security-test slice

---

## Email send + verification UX

### Q1 — Send timing

| Option | Description | Selected |
|--------|-------------|----------|
| Async @Async + thread pool | Signup writes user+token in tx, returns 201 immediately; ThreadPoolTaskExecutor sends email | ✓ |
| Sync inside signup transaction | Send email inside @Transactional before returning 201 | |
| Outbox pattern | Write outbox row in tx, drain via @Scheduled poller | |

**User's choice:** Async @Async + thread pool
**Notes:** Fast 201, SMTP outage doesn't fail signup. Recovery via re-signup (docs §9.1).

### Q2 — Verification link target

| Option | Description | Selected |
|--------|-------------|----------|
| Frontend route, backend redirects | Email link → backend consumes token → 302 → http://localhost:5173/verify?status=… | ✓ |
| Backend-only JSON (literal docs §3) | Backend GET returns 200 application/json {verified:true} | |
| Frontend deep-link in email | Email link → http://localhost:5173/verify?token=…; SPA calls verify endpoint | |

**User's choice:** Frontend route, backend redirects
**Notes:** Token never leaves server-side flow; only `status` reaches the FE. Demo works end-to-end without FE running (FE owns the success page in Phase 7, but the redirect already lands).

### Q3 — Email body

| Option | Description | Selected |
|--------|-------------|----------|
| Plain-text only | SimpleMailMessage with verification URL on its own line | ✓ |
| Multipart HTML + plain-text fallback | MimeMessageHelper + Thymeleaf template | |
| Plain-text now, leave a hook for HTML later | VerificationEmailComposer interface for Phase 9 swap-in | |

**User's choice:** Plain-text only
**Notes:** No template engine, no extra deps, no template-injection surface, works in every email client. Phase 9 can add HTML later without an interface hook.

### Q4 — SMTP failure handling

| Option | Description | Selected |
|--------|-------------|----------|
| Log WARN + leave token; user re-signs up to resend | Catch MailException, log WARN; token TTL or daily cleanup; re-signup path triggers fresh email | ✓ |
| Bounded retry then WARN | 3 tries with 2s/4s/8s backoff via Spring Retry | |
| Alert via WARN + Micrometer counter | Same as A but adds counter `auth.email.send.failed` for Phase 10 dashboards | |
| Both A and C | Log + counter, no retry | |

**User's choice:** Log WARN + leave token; user re-signs up to resend
**Notes:** Recovery path documented in docs §9.1; no extra deps; portfolio scope.

---

## Login rate limit (IP+email leg)

### Q1 — Where the check happens

| Option | Description | Selected |
|--------|-------------|----------|
| Inside @Service.login() before bcrypt verify | AuthService reads IP+email, calls RateLimiter.tryConsume, 429 before bcrypt | ✓ |
| OncePerRequestFilter pre-controller | Filter on URL pattern reads body via ContentCachingRequestWrapper | |
| @RateLimiter (Resilience4j) annotation | Declarative AOP-driven, in-memory by default | |

**User's choice:** Inside @Service.login() before bcrypt verify
**Notes:** Avoids 250ms bcrypt cost on rate-limited requests; no body-caching gymnastics; explicit.

### Q2 — Backing store

| Option | Description | Selected |
|--------|-------------|----------|
| Redis with TTL key | INCR + EXPIRE 900s, key 'rl:login:fail:{ip}:{email_lower}' | ✓ |
| In-process Caffeine cache | ConcurrentHashMap counters with 15-min expireAfterWrite | |
| Bucket4j with Redis backend | Token-bucket via Bucket4j 8.x with Lettuce/Jedis adapter | |

**User's choice:** Redis with TTL key
**Notes:** Matches gateway's `rl:` keyspace prefix from Phase 1 D-06. Redis already in compose. Cheap intra-compose roundtrip.

### Q3 — Counting model

| Option | Description | Selected |
|--------|-------------|----------|
| Failed attempts only, reset on success | Increment on auth.invalid_credentials only; success DELETEs key | ✓ |
| All attempts, token-bucket style | Increment on every login regardless of outcome | |
| Failed attempts, no reset on success | Increment on failure only; counter persists past success | |

**User's choice:** Failed attempts only, reset on success
**Notes:** Matches docs/05 §10 test #8 phrasing literally; doesn't punish legitimate users who mistyped before getting it right.

### Q4 — Limit-tripped response

| Option | Description | Selected |
|--------|-------------|----------|
| 429 + auth.rate_limited, generic message | ProblemDetail status=429, code already in docs §6, generic detail preserves §9.1 | ✓ |
| Same as A but with Retry-After mandatory | Adds Retry-After: <seconds-until-key-TTL> | |
| 403 + auth.account_locked + email notification | Treats 6th attempt as temporary lock + notification email | |

**User's choice:** 429 + auth.rate_limited, generic message
**Notes:** Same envelope as gateway's rate-limit response (after BL-01 fix); preserves enumeration policy.

---

## Refresh rotation reuse + logout scope

### Q1 — Replay revocation scope

| Option | Description | Selected |
|--------|-------------|----------|
| Revoke entire chain back to root + log WARN | Walk rotated_to back to root, forward to head; mark revoked_at=NOW() on every row | ✓ |
| Revoke ALL refresh tokens for this user | Stronger; kicks every device | |
| Revoke chain only + special replay error code | Same as A but emits new code 'auth.refresh_replay' | |

**User's choice:** Revoke entire chain back to root + log WARN
**Notes:** Matches docs/05 §2 verbatim. Doesn't kick legitimate sibling chains.

### Q2 — Logout scope

| Option | Description | Selected |
|--------|-------------|----------|
| Revoke only the cookie's chain | Hash cookie value, look up chain, mark head revoked, clear cookie | ✓ |
| Revoke ALL of user's active chains | Logout-everywhere semantics | |
| A by default, B as separate /logout-all endpoint | Ship both; new endpoint outside docs §3 catalog | |

**User's choice:** Revoke only the cookie's chain
**Notes:** Matches typical browser logout semantics; simplest. Logout-all deferred to v2.

### Q3 — Cookie scope

| Option | Description | Selected |
|--------|-------------|----------|
| Path=/api/auth, SameSite=Strict, HttpOnly, Secure-when-https | Matches docs §3 + §9.3 verbatim | ✓ |
| Path=/api/auth/refresh narrower | Only refresh route sees the cookie | |
| Path=/api/auth, SameSite=Lax | Looser cross-site nav behavior | |

**User's choice:** Path=/api/auth, SameSite=Strict, HttpOnly, Secure-when-https
**Notes:** Cookie reaches /refresh AND /logout (both need it server-side). Secure flag toggled via profile.

### Q4 — Concurrent-refresh edge case

| Option | Description | Selected |
|--------|-------------|----------|
| Database row pessimistic lock | SELECT FOR UPDATE; second arrival sees rotated_to non-null → replay branch | ✓ |
| Optimistic check via UPDATE WHERE rotated_to IS NULL | Rowcount-zero detection; race-window narrower but still real | |
| Best-effort, trust SPA isRefreshing guard | Skip server-side serialization | |

**User's choice:** Database row pessimistic lock (SELECT FOR UPDATE)
**Notes:** Defense-in-depth — server is correct independently of the SPA's Pitfall-9 client guard.

---

## BL-01 fix + Phase 2 security-test slice

### Q1 — BL-01 fix timing and approach

| Option | Description | Selected |
|--------|-------------|----------|
| Fix in Phase 2 — inject auto-configured ObjectMapper into both gateway emitters | Update ProblemDetailAuthEntryPoint, RateLimitProblemDetailFilter, plus 4 ITs | ✓ |
| Fix in Phase 9 polish | Defer; Phase 7 frontend handles both shapes | |
| Match auth-service to gateway's $.properties.code shape | Revert Phase 1 servlet pattern | |

**User's choice:** Fix in Phase 2 — inject auto-configured ObjectMapper into both gateway emitters
**Notes:** Public-API contract divergence becomes user-visible the moment SPA hits both surfaces. Fix lands alongside Phase 2's auth-service work; ~4 file touches in gateway + 4 IT updates.

### Q2 — Phase 2's slice of the 8 mandatory security tests

| Option | Description | Selected |
|--------|-------------|----------|
| Phase 2 owns #5 #6 #7 + email-aware #8; #3 #4 defer to Phase 5/6 | Phase 1 already shipped #1 #2 #8-IP-only + Pitfall-1 test | ✓ |
| Phase 2 owns all 8 — stub trip endpoints just to ship #3 #4 here | Tests against stubs, rewritten in Phase 5/6 | |
| Phase 2 owns #5 #6 #7 #8 + writes @Disabled shells for #3 #4 | Disabled tests as TODOs naming Phase 5/6 | |

**User's choice:** Phase 2 owns #5 #6 #7 + email-aware #8; #3 #4 defer to Phase 5/6
**Notes:** Tests assert real surface, not stubs; cleaner than @Disabled. Traceability stays clean — REQUIREMENTS.md already maps #3 to Phase 5 and #4 to Phase 6.

### Q3 — Token cleanup scheduler

| Option | Description | Selected |
|--------|-------------|----------|
| Single @Scheduled daily 02:00 UTC, deletes both tables, retention = expires_at + 7 days | One bean, one test surface | ✓ |
| Two separate @Scheduled methods | One per table, more granular logs | |
| Defer cleanup to Phase 9 polish | Phase 2 ships without cleanup | |

**User's choice:** Single @Scheduled daily 02:00 UTC, deletes both tables
**Notes:** Matches docs/03 §3.2 expectation. No ShedLock (single-instance auth-service per docs/02 §3).

### Q4 — Bean Validation feedback

| Option | Description | Selected |
|--------|-------------|----------|
| @Email + @Size on DTO; emit auth.invalid_email or auth.weak_password | @ControllerAdvice maps MethodArgumentNotValidException to specific catalog codes | ✓ |
| Single validation.failed with field-level details | Simpler, less specific | |
| Manual validation in service layer | No Bean Validation; doesn't compose with Spring's exception handler | |

**User's choice:** Bean Validation @Email + @Size with specific catalog codes
**Notes:** docs/04 §6 lists both codes precisely so they're meant to be distinct. Generic detail messages — no field-name leak.

---

## Claude's Discretion

- `JwtIssuer` placement: `libs/jwt-common` (alongside `JwtVerifier`) vs auth-service-local — researcher decides based on dependency graph and Phase-1 placement consistency.
- Verification email body copy (subject + body text) — keep short, plain, no marketing.
- Whether to ship a tiny static `verify-success.html` page in auth-service for the redirect target as fallback — researcher decides based on demo-recording needs.
- Whether `/api/auth/me` is shipped in Phase 2 (recommended) or whether SPA decodes JWT client-side (Phase 7).
- DTO field-level naming (`SignupRequest`, `LoginResponse`, `RefreshResponse`) — match `docs/04 §3` shapes verbatim, otherwise pick what reads cleanest.
- Whether `TokenCleanupJob` DELETEs run in one or two transactions — no functional difference.

## Deferred Ideas

- **Logout-everywhere endpoint** (`POST /api/auth/logout-all`) — v2 (needs UI surface to view active sessions).
- **Email-only (no-IP) rate-limit layer** — v2 hardening (defense vs IP-rotating attackers).
- **Retry-After header on 429** — v2 (RFC 6585 §4 UX win).
- **Account-takeover notification email** — v2 (needs IP→geo + deliverability).
- **HTML email templates** — Phase 9 polish (no interface hook pre-extracted).
- **Bounded retry on async email send** — v2 (near-zero value with local MailHog).
- **JWT key rotation (two valid signing keys)** — v2 per `docs/05 §9.4`.
- **ShedLock on TokenCleanupJob** — v2 (auth-service single-instance in v1).
- **Password breach-list check (haveibeenpwned k-anonymity)** — v2 per `docs/05 §6`.
