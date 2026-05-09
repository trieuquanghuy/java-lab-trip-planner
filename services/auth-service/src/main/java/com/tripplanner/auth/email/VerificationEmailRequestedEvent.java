package com.tripplanner.auth.email;

import java.util.UUID;

/**
 * Published by AuthService.signup (Plan 05); consumed by EmailVerificationSender (Task 4.2).
 *
 * The publish/listen indirection forces a fresh AOP proxy hop, ensuring the listener's @Async
 * actually goes async (Pitfall 1 — self-invocation @Async bypasses the proxy because Spring's
 * AOP proxy only intercepts calls that arrive from outside the bean instance).
 *
 * Spring's ApplicationEventPublisher accepts any object as an event since Spring 4.2 — no need
 * to extend ApplicationEvent.
 */
public record VerificationEmailRequestedEvent(String email, UUID userId, String token) {}
