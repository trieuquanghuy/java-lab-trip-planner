# 05 — Auth & Security Design

**Status**: Draft for review
**Last updated**: 2026-05-08

## 1. Authentication overview

- Email + password local accounts.
- Email verification required before login.
- JWT access tokens (HS256, 15 min) carried in `Authorization: Bearer` header.
- Refresh tokens (opaque, 7 days) carried in httpOnly cookie scoped to `/api/auth`.
- Refresh-token rotation on every refresh.
- Logout revokes the refresh token.
- No third-party social login in v1 (deferred to v2).

## 2. JWT design

### Claims
```json
{
  "iss": "tripplanner-auth",
  "sub": "5f3c… (userId)",
  "iat": 1715170800,
  "exp": 1715171700,
  "email": "mai@example.com",
  "ver": true,
  "jti": "8e2…"
}
```

- `ver` is `email_verified` flag at issue time. Flips to true after verification; the access token reflects that on next login or refresh, not retroactively.
- `jti` is the unique token id, useful for revocation lists in v2 (not used in v1).

### Signing
- Algorithm: HS256 (symmetric).
- Secret: 32+ byte random string in env var `AUTH_JWT_SECRET`.
- Why HS256 not RS256: simpler key distribution for v1 (every service in monorepo shares the secret via env). RS256 with public key distribution is a v2 upgrade.
- Library: `io.jsonwebtoken:jjwt-impl:0.13.0`.

### Refresh tokens
- 32 random bytes, hex-encoded → 64-char string.
- Stored hashed (SHA-256) server-side in `auth.refresh_tokens` table (added in Phase 2):

```
auth.refresh_tokens
  token_hash  CHAR(64) PK
  user_id     UUID FK → users
  expires_at  TIMESTAMPTZ
  rotated_to  CHAR(64) NULL   -- if rotated, points to successor; else null
  revoked_at  TIMESTAMPTZ NULL
  created_at  TIMESTAMPTZ
```
- Rotation: on successful refresh, mark the current token's `rotated_to` to the new token's hash. The new token replaces the cookie. Reusing a rotated token is a replay → revoke entire chain.
- Sliding expiration capped at 7 days from initial issue.

## 3. Sequence: signup → verify → login

```
Browser              Gateway          auth-service       SMTP
  │                     │                   │              │
  │ POST /signup        │                   │              │
  │────────────────────▶│ proxy             │              │
  │                     │──────────────────▶│              │
  │                     │                   │ insert user  │
  │                     │                   │ generate tok │
  │                     │                   │──────────────▶ verification email
  │                     │  201              │              │
  │◀────────────────────│ {userId}          │              │
  │                                                         │
  │ user clicks email link                                  │
  │ GET /verify?token=…                                     │
  │────────────────────▶│ proxy            │              │
  │                     │─────────────────▶│              │
  │                     │                   │ consume tok │
  │                     │                   │ users.email_verified=true │
  │                     │  200              │              │
  │◀────────────────────│                   │              │
  │                                                         │
  │ POST /login                                             │
  │────────────────────▶│─────────────────▶│              │
  │                     │                   │ verify pwd  │
  │                     │                   │ check ver   │
  │                     │                   │ issue tokens│
  │                     │  200 + Set-Cookie│              │
  │◀────────────────────│ {accessToken}    │              │
```

## 4. Sequence: authenticated request across services

```
Browser           Gateway                trip-service
  │                  │                        │
  │ GET /api/trips   │                        │
  │ Bearer eyJ…      │                        │
  │─────────────────▶│ verify JWT signature   │
  │                  │ check exp              │
  │                  │ extract sub → userId   │
  │                  │ inject:                │
  │                  │   X-User-Id: 5f3c…     │
  │                  │   X-User-Email: …      │
  │                  │   X-Request-Id: …      │
  │                  │───────────────────────▶│ JwtCommonFilter
  │                  │                         │  re-validate JWT (defense in depth)
  │                  │                         │  build SecurityContext
  │                  │                         │  controller reads currentUser
  │                  │                         │  service: WHERE user_id=?
  │                  │  200                    │
  │◀─────────────────│                         │
```

## 5. Authorization model

Authorization is **resource-ownership-based**. There are no roles in v1.

Rules:
- Every endpoint under `/api/trips/**` and `/api/favorites/**` filters by `user_id = currentUser.id` at the service layer (NOT only at the controller).
- Endpoints that look up a single trip/item/favorite by ID return `404` (not `403`) for non-owned resources — does not reveal existence.
- The `currentUser.id` is read from the SecurityContext, which is populated by `JwtCommonFilter` from the validated JWT — it is **never** read from a request body or path parameter.

### Implementation pattern (illustrative)
```java
@Service
class TripService {
  public TripView getTrip(UUID tripId, UUID currentUserId) {
    return tripRepository.findByIdAndUserId(tripId, currentUserId)
      .map(TripView::from)
      .orElseThrow(() -> new TripNotFoundException(tripId));
  }
}
```

There is no `@PreAuthorize("hasRole('USER')")` shortcut; instead each
service-layer method takes `currentUserId` and uses it in the query. This
makes ownership tests trivially mockable and impossible to forget at the
controller.

## 6. Password policy

- Minimum 8 characters.
- No maximum below 200 (NIST SP 800-63B).
- No mandatory complexity rules (NIST SP 800-63B advises against forced complexity).
- Hashed with bcrypt cost 12 (~250ms on commodity hardware in 2026).
- Password is **never** logged. Add `@JsonProperty(access = WRITE_ONLY)` on DTO field.
- A password breach-list check (k-anonymity against haveibeenpwned API) is on v2 backlog.

## 7. Email verification

- 32 random bytes hex-encoded (64 chars), stored as primary key.
- 24-hour expiry.
- Single-use: `consumed_at` set on first valid use; subsequent uses return `auth.token_invalid`.
- A user can request a fresh verification email via re-signup attempt that detects an unverified account (returns 200 + sends new email; does NOT leak existence).

## 8. Threat model — OWASP Top 10 (2021) coverage

| Threat | Mitigation in v1 |
|--------|------------------|
| **A01 Broken Access Control** | Service-layer ownership filter on every authenticated endpoint. 404 not 403 for non-owned. Integration tests prove cross-user access returns 404. |
| **A02 Cryptographic Failures** | Bcrypt for passwords. JWT signed HS256 with ≥32-byte secret from env. TLS in deployed envs (out of scope for v1 local). |
| **A03 Injection** | All DB access via JPA parameterized queries. No string concatenation into SQL. Notes/cover-image-url server-side sanitized via OWASP Java HTML Sanitizer (allowlist: empty — strip all tags). |
| **A04 Insecure Design** | Generic error messages on auth failure (no enumeration). Refresh-token rotation. Verify-before-login. |
| **A05 Security Misconfiguration** | Spring Security defaults. CORS allowlist not wildcard. Stack traces not returned in prod profile. Actuator endpoints behind `management.server.port` in prod path. |
| **A06 Vulnerable Components** | Dependabot enabled in repo. `./gradlew dependencyCheck` (OWASP Dependency-Check plugin) in CI fails build on CVSS ≥ 7. |
| **A07 Identification & Authentication Failures** | Bcrypt-hashed passwords. Login rate limit (5/15min/IP+email). Generic error. Email verification required. JWT short-lived. Refresh rotation. |
| **A08 Software & Data Integrity** | Gradle wrapper checksum verified in CI. Container images pinned by digest in compose for prod path. |
| **A09 Logging & Monitoring Failures** | Structured JSON logs include userId (when authenticated) and traceId. Login failures logged at WARN. Verify failures at INFO. Brute-force pattern alertable in Phase 10. |
| **A10 Server-Side Request Forgery** | No user-supplied URL is fetched by the server in v1 (cover image is stored as a string and only rendered by the browser). When file upload arrives in v2, S3 pre-signed URLs avoid SSRF surface. |

## 9. Specific concerns and mitigations

### 9.1 Account enumeration
- Signup of an existing email returns the same response shape as success; verification email is sent only on truly new accounts but the **HTTP response** is indistinguishable.
- Login with unknown email returns the same `auth.invalid_credentials` as wrong password.
- Verify with an unknown token returns `auth.token_invalid` (same as expired/consumed).

### 9.2 Cross-Site Scripting (XSS)
- React escapes all rendered text by default — no `dangerouslySetInnerHTML`.
- User-provided fields persisted in DB (note, cover image URL, trip name) sanitized server-side via OWASP Java HTML Sanitizer (strip all HTML tags).
- Content-Security-Policy header from gateway: `default-src 'self'; img-src 'self' https: data:; ...`. Restricts external script execution.

### 9.3 CSRF
- JWT bearer tokens in Authorization header are not auto-sent by browsers → no CSRF risk on access-token endpoints.
- Refresh-token cookie is `SameSite=Strict` — browser will not send it on cross-site requests.
- For belt-and-suspenders, refresh endpoint requires `Origin` header to match allowlisted origins.

### 9.4 Secrets management
- v1 (local): secrets in `.env` file gitignored; sample values in `.env.example` committed.
- v2 (deployed): platform secrets manager (Fly.io secrets, GitHub Actions secrets for CI).
- JWT secret rotation: planned approach is to support two valid signing keys at once (current + previous) for a grace period. Implementation deferred to v2.

### 9.5 Dependency hygiene
- Gradle version catalog (`gradle/libs.versions.toml`) is the single dependency source of truth.
- Dependabot weekly PRs; CI runs OWASP Dependency-Check.
- React side: `pnpm audit` in frontend CI step; `--audit-level=high` fails build.

### 9.6 Logging hygiene
- Never log: passwords, raw JWTs, refresh tokens, email contents.
- Log: userId, email (login attempts at WARN), traceId, IP, user-agent.
- Logback masks fields named `password`, `token`, `secret` via a custom converter.

## 10. Authorization tests (mandatory in CI)

The following integration tests must exist and pass — failure of any blocks merge:

1. **AnonymousCannotAccessAuthedEndpoints**: every protected route returns 401 without a token.
2. **InvalidTokenIsRejected**: malformed JWT, wrong signature, expired token → 401.
3. **CrossUserTripAccessReturns404**: User A creates trip; User B's GET on it returns 404 (not 200, not 403).
4. **CrossUserItemPatchReturns404**: User A creates item; User B's PATCH returns 404.
5. **DeletedRefreshTokenCannotBeUsed**: logout invalidates refresh token; subsequent refresh returns 401.
6. **RotatedRefreshTokenCannotBeReused**: replay attack → entire token chain revoked.
7. **EmailNotVerifiedCannotLogin**: signup → login without verify → 403.
8. **LoginRateLimitTriggers**: 6 failed logins in 15 min → 429.

These tests are tagged `@Tag("security")` and run as a separate CI step that
must pass on every PR.
