---
phase: 02-auth-service
plan: 02
subsystem: jwt-issuance
tags: [jwt, hs256, jjwt-0.13, libs-jwt-common, auto-configuration, conditional-on-missing-bean, tdd, sibling-twin]

# Dependency graph
requires:
  - phase: 01-api-gateway
    provides: "libs/jwt-common (JwtVerifier + JwtProperties + JwtFixtures + JwtAuthenticationException + JwtAutoConfiguration with @ConditionalOnWebApplication-discriminated servlet/reactive configs); Convention C27-P1 (jjwt 0.13.0 modern API only); Convention C28-P1 (HS256 ≥32-byte secret fail-at-startup); UserContext record from libs/api-contracts"
  - phase: 02-auth-service
    plan: 01
    provides: "auth-service runtime dep on libs/jwt-common already wired in services/auth-service/build.gradle.kts (Plan 02-01) — no build-graph change required for 02-02"
provides:
  - "JwtIssuer in libs/jwt-common — sibling-twin to JwtVerifier; HS256 access-token issuance with 15-min TTL"
  - "JwtIssuer.issueAccess(userId, email, emailVerified) → compact JWS string with claims iss=tripplanner-auth, sub, iat, exp, jti=UUID, email, ver"
  - "JwtAutoConfiguration registers JwtIssuer as a @Bean with @ConditionalOnMissingBean(JwtIssuer.class)"
  - "@ConditionalOnMissingBean(JwtVerifier.class) added to existing jwtVerifier bean — Phase 1 IN-01 closure (both beans now cooperative against test-harness re-registration)"
  - "JwtIssuerTest with 7 tests: round-trip (verified=true/false), 15-min TTL, unique jti, iss=tripplanner-auth, defensive constructor (null + short secret) — all GREEN"
affects: [02-03, 02-04, 02-06]

# Tech tracking
tech-stack:
  added:
    - "(no new external deps — jjwt 0.13.0 already on classpath from Phase 1 / Plan 01-02)"
  patterns:
    - "Pattern: Sibling-twin token primitives — JwtIssuer.java and JwtVerifier.java live in the same package (com.tripplanner.jwt) inside libs/jwt-common, share the byte-for-byte-identical defensive constructor (null check → IllegalStateException; Keys.hmacShaKeyFor catching WeakKeyException → IllegalStateException), and consume the same JwtProperties (auth.jwt.secret backed by AUTH_JWT_SECRET env var). Maintenance unit is the pair, not the individual class."
    - "Pattern: Auto-configured JWT primitives via @AutoConfiguration + META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports. Both jwtVerifier and jwtIssuer beans are @ConditionalOnMissingBean — defense-in-depth against downstream service test harnesses that supply their own bean."
    - "Pattern: jjwt 0.13.0 modern builder API — Jwts.builder().issuer().subject().issuedAt().expiration().id().claim().signWith(SecretKey).compact(). signWith(SecretKey) auto-selects HS256 from the key (no explicit algorithm parameter — preempts algorithm-confusion vulnerabilities)."

key-files:
  created:
    - "libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtIssuer.java"
    - "libs/jwt-common/src/test/java/com/tripplanner/jwt/JwtIssuerTest.java"
  modified:
    - "libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtAutoConfiguration.java"

key-decisions:
  - "JwtIssuer location: libs/jwt-common (sibling to JwtVerifier), NOT auth-service-local — closes 02-CONTEXT.md Open Question 3 per 02-RESEARCH.md §Open Q3 + 02-PATTERNS.md analog map. Keeps the 'sign + verify' contract collocated; auth-service consumes via existing project(':libs:jwt-common') dep (no build.gradle.kts change)."
  - "Constructor body byte-for-byte identical to JwtVerifier lines 26-38 (sibling-twin convention) — null check + Keys.hmacShaKeyFor(bytes) + WeakKeyException remap to IllegalStateException. Same error messages so ops grep for 'AUTH_JWT_SECRET must be set' / 'must be at least 32 bytes' catches both."
  - "Phase 1 IN-01 closed: @ConditionalOnMissingBean now on BOTH jwtVerifier AND jwtIssuer beans. Without the conditional, a downstream service that defines its own @Bean JwtVerifier in a @TestConfiguration would crash with NoUniqueBeanDefinitionException. With it, downstream wins, auto-config backs off."
  - "Claim shape per docs/05-auth-security.md §2: iss=tripplanner-auth, sub=userId, iat=now, exp=now+15min, jti=UUID.randomUUID(), email=<email>, ver=<emailVerified>. Round-trip through JwtVerifier confirms email + ver claims survive serialization unchanged (Test 1 + Test 2)."
  - "TTL = Duration.ofMinutes(15) per docs/05 §1 (locked spec). Test asserts ttl is between 898 and 902 seconds (15 min ±2s) to absorb millisecond rounding when Date.from(Instant) truncates."

# Execution metrics
metrics:
  duration: ~6min
  tasks_completed: 2
  files_created: 2
  files_modified: 1
  tests_added: 7
  tests_passing: 7
  completed: 2026-05-09T15:38Z
---

# Phase 2 Plan 02: JwtIssuer (libs/jwt-common sibling-twin to JwtVerifier) Summary

HS256 access-token issuer landed in `libs/jwt-common` next to `JwtVerifier` — same package, same `JwtProperties`, same `Keys.hmacShaKeyFor` key construction, same fail-at-startup defensive contract. Auto-configured via `@ConditionalOnMissingBean`, closing Phase 1 IN-01 by adding the same conditional to the existing `JwtVerifier` bean.

---

## What was built

### JwtIssuer (`libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtIssuer.java`, new)

```java
public class JwtIssuer {
    private static final String ISSUER = "tripplanner-auth";
    private static final Duration TTL  = Duration.ofMinutes(15);
    private final SecretKey signingKey;

    public JwtIssuer(String secret) { /* fail-at-startup on null or <32-byte */ }

    public String issueAccess(String userId, String email, boolean emailVerified) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(TTL)))
                .id(UUID.randomUUID().toString())
                .claim("email", email)
                .claim("ver", emailVerified)
                .signWith(signingKey)   // HS256 auto-selected from key
                .compact();
    }
}
```

- Constructor body byte-for-byte from `JwtVerifier.java` lines 26-38 — sibling-twin maintenance contract.
- jjwt 0.13.0 modern builder API only (Convention C27-P1) — no `parserBuilder()`, no `setSigningKey(byte[])`.

### JwtAutoConfiguration extension

Added one bean and one annotation; added one import. Both `@Bean` methods now `@ConditionalOnMissingBean(<class>.class)` — closing Phase 1 IN-01 (defense against downstream service test harnesses supplying their own bean).

```java
@Bean
@ConditionalOnMissingBean(JwtVerifier.class)         // ← annotation added (IN-01)
public JwtVerifier jwtVerifier(JwtProperties props) { ... }

@Bean
@ConditionalOnMissingBean(JwtIssuer.class)           // ← new bean
public JwtIssuer jwtIssuer(JwtProperties props) {
    return new JwtIssuer(props.getSecret());
}
```

### JwtIssuerTest (7 tests, all GREEN)

| Test | Asserts |
|------|---------|
| `issued_token_round_trips_through_verifier` | issuer + verifier symmetry; UserContext fields (userId, email, verified=true) intact |
| `issued_token_unverified_account_round_trips` | verified=false survives serialization |
| `issued_token_has_15_minute_expiry` | `exp - iat` is 900s ±2s |
| `issued_token_has_unique_jti` | two issued tokens have distinct `jti` UUIDs |
| `issued_token_carries_iss_tripplanner_auth` | `iss` claim equals `"tripplanner-auth"` |
| `issuer_throws_on_null_secret` | `IllegalStateException` containing "must be set" |
| `issuer_throws_on_short_secret` | `IllegalStateException` containing "at least 32 bytes" |

Test execution: 7 passed, 0 skipped, 0 failures (`build/test-results/test/TEST-com.tripplanner.jwt.JwtIssuerTest.xml`). JwtVerifierTest regression check also passed (7/7 still GREEN — no behavioral drift from the @ConditionalOnMissingBean addition).

---

## TDD gate compliance

RED → GREEN sequence captured in git log:

```
cb83698 feat(02-02): implement JwtIssuer + auto-configure bean    ← GREEN
68a787d test(02-02): add failing JwtIssuerTest for HS256...       ← RED
```

The RED commit was verified RED before the GREEN commit landed:
```
$ ./gradlew :libs:jwt-common:compileTestJava
... error: cannot find symbol
    private final JwtIssuer issuer = new JwtIssuer(JwtFixtures.TEST_SECRET);
                  ^
  symbol:   class JwtIssuer
4 errors
BUILD FAILED
```

GREEN gate confirmed by `./gradlew :libs:jwt-common:check` exit 0 with all 14 tests passing (7 JwtIssuerTest + 7 JwtVerifierTest).

REFACTOR not needed — implementation came in clean per the verbatim 02-RESEARCH.md / 02-PATTERNS.md analog.

---

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Doc clarity] Reworded JwtIssuer.java header comment to avoid literal `setSigningKey` substring**

- **Found during:** Task 2.2 acceptance grep
- **Issue:** The plan's `<verify>` regex `! grep -q "parserBuilder\|setSigningKey" libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtIssuer.java` was over-strict — it also rejected the educational header comment `// .signWith(SecretKey).compact(). NEVER setSigningKey(byte[]).` (which mirrors JwtVerifier.java's identical doc-comment line 5).
- **Fix:** Reworded the comment to `// .signWith(SecretKey).compact(). NEVER the deprecated 0.11.x byte-array signing-key shape.` — preserves the educational intent (anti-pattern reminder) without tripping the literal grep. The actual *code* never used the deprecated API.
- **Files modified:** `libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtIssuer.java` (comment line only, included in the GREEN commit)
- **Commit:** `cb83698`
- **Note:** `JwtVerifier.java` line 5 still contains the literal phrase `parserBuilder().setSigningKey()` for the same anti-pattern reminder — it ships from Phase 1 and the verify regex specifically targeted only `JwtIssuer.java`, so no regression. If a future plan tightens C27-P1 enforcement repo-wide, that comment may need similar treatment.

### Auth gates encountered

None. Pure local Gradle build + test execution.

### Architectural changes

None. The plan's analog map was followed verbatim.

---

## Decision log additions (for STATE.md)

- **02-02:** JwtIssuer location RESOLVED — sibling-twin to JwtVerifier in `libs/jwt-common`. Constructor body byte-for-byte identical to JwtVerifier lines 26-38. Closes 02-CONTEXT.md Open Question 3.
- **02-02:** Phase 1 IN-01 closed — `@ConditionalOnMissingBean` now on BOTH `jwtVerifier` AND `jwtIssuer` beans in `JwtAutoConfiguration`. Downstream service test harnesses can override either bean without crashing the auto-config.
- **02-02:** jjwt 0.13.0 modern issuance API locked — `Jwts.builder().issuer().subject().issuedAt().expiration().id().claim().signWith(SecretKey).compact()`. Algorithm auto-selected from the key (HS256 for any `SecretKey` produced by `Keys.hmacShaKeyFor(>=32-byte)`). No explicit algorithm parameter prevents algorithm-confusion at the issuance boundary (T-2-02-01 mitigation).
- **02-02:** Access-token TTL = `Duration.ofMinutes(15)` (docs/05 §1). Test tolerance ±2s for millisecond rounding when `Date.from(Instant)` truncates.

---

## Threat model — runtime check

Mitigations from the plan's threat register all applied:

| Threat ID | Mitigation status |
|-----------|-------------------|
| T-2-02-01 (algorithm confusion) | `signWith(SecretKey)` auto-selects HS256 — no explicit `SignatureAlgorithm.HS256` parameter that could be downgraded |
| T-2-02-02 (weak key / null secret) | Constructor throws `IllegalStateException` on null OR `WeakKeyException` (< 32 byte) — verified by tests `issuer_throws_on_null_secret` + `issuer_throws_on_short_secret` |
| T-2-02-03 (PII in claims) | Accepted (docs/05 §2) — email is signed but unencrypted; v1 scope; v2 may move to opaque session IDs |
| T-2-02-04 (jti collision) | `UUID.randomUUID()` — astronomical collision probability; verified by `issued_token_has_unique_jti` |
| T-2-02-05 (clock skew) | Single-instance auth-service in v1 (docs/02 §3); ±2s test tolerance |
| T-2-02-06 (bean double-registration) | `@ConditionalOnMissingBean` on both jwtVerifier AND jwtIssuer; downstream test harnesses cooperative |

No new threat surface introduced beyond the plan's register.

---

## Verification

- `./gradlew :libs:jwt-common:check` exits 0 ✓
- `./gradlew :libs:jwt-common:test --tests "com.tripplanner.jwt.JwtIssuerTest"` exits 0 (7/7) ✓
- `./gradlew :libs:jwt-common:test --tests "com.tripplanner.jwt.JwtVerifierTest"` exits 0 (7/7 — no regression from @ConditionalOnMissingBean addition) ✓
- `git grep "parserBuilder()\|setSigningKey(" libs/jwt-common/src/main/` returns zero CODE matches (only doc-comment references in JwtVerifier.java line 5; JwtIssuer.java reworded to avoid the literal) ✓
- All `<acceptance_criteria>` literal grep substrings present in JwtIssuer.java and JwtAutoConfiguration.java ✓

---

## Files

### Created (2)
- `libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtIssuer.java` — 49 lines (incl. header comment block); HS256 access-token issuer
- `libs/jwt-common/src/test/java/com/tripplanner/jwt/JwtIssuerTest.java` — 95 lines; 7 tests covering round-trip + claim correctness + defensive constructor

### Modified (1)
- `libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtAutoConfiguration.java` — +1 import, +1 annotation on existing `jwtVerifier` bean, +5 lines for new `jwtIssuer` bean method

---

## Commits

| Commit | Type | Message |
|--------|------|---------|
| `68a787d` | test | test(02-02): add failing JwtIssuerTest for HS256 access-token issuer |
| `cb83698` | feat | feat(02-02): implement JwtIssuer + auto-configure bean |

(metadata commit `<this commit>` will follow with SUMMARY.md + STATE.md + ROADMAP.md updates)

---

## Self-Check: PASSED

- File `libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtIssuer.java`: FOUND
- File `libs/jwt-common/src/test/java/com/tripplanner/jwt/JwtIssuerTest.java`: FOUND
- File `libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtAutoConfiguration.java`: FOUND (modified)
- Commit `68a787d` (RED): FOUND in `git log`
- Commit `cb83698` (GREEN): FOUND in `git log`
- `./gradlew :libs:jwt-common:check`: exit 0 confirmed
- 7/7 JwtIssuerTest passing; 7/7 JwtVerifierTest passing (no regression)
