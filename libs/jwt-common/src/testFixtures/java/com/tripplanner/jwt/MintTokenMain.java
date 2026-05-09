package com.tripplanner.jwt;

/**
 * Standalone main class for minting a test JWT token.
 * Used by scripts/mint-test-token.sh via Gradle JavaExec task — avoids leaking
 * tokens into JUnit test report XML artifacts.
 */
public final class MintTokenMain {

    private MintTokenMain() {}

    public static void main(String[] args) {
        String token = JwtFixtures.mintValid("smoke-user", "smoke@example.com");
        System.out.println(token);
    }
}
