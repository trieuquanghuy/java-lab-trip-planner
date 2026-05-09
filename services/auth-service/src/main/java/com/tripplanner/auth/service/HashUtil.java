package com.tripplanner.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 hex helper. Single call site avoids 4 lines of try/catch boilerplate at every callsite
 * (RefreshTokenService.rotate / .revokeChainHead / .create both hash the cookie value).
 *
 * Source: 02-RESEARCH.md §Don't Hand-Roll table — "Manual SHA-256 hex" → write a single helper.
 */
public final class HashUtil {

    private HashUtil() {}

    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xFF));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must be available on every JVM", e);
        }
    }
}
