// Source: 01-VALIDATION.md Wave 0 contract "UserContext";
//         01-CONTEXT.md D-04 (UserContext shape — userId/email/verified);
//         01-PATTERNS.md Bucket B lines 220-249 (Principal contract).
//         Convention C35-P1.
//
// Verifies: (a) record accessors; (b) getName() = userId (Principal contract);
// (c) equals/hashCode for two identical records; (d) assignable to java.security.Principal.
package com.tripplanner.contracts;

import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;

class UserContextTest {

    @Test
    void accessorsReturnConstructorValues() {
        var ctx = new UserContext("uid-1", "user@example.com", true);
        assertThat(ctx.userId()).isEqualTo("uid-1");
        assertThat(ctx.email()).isEqualTo("user@example.com");
        assertThat(ctx.verified()).isTrue();
    }

    @Test
    void getNameReturnUserId() {
        var ctx = new UserContext("uid-42", "a@b.com", false);
        assertThat(ctx.getName()).isEqualTo("uid-42");
    }

    @Test
    void implementsPrincipal() {
        UserContext ctx = new UserContext("uid-1", "user@example.com", true);
        assertThat(ctx).isInstanceOf(Principal.class);
        Principal p = ctx;
        assertThat(p.getName()).isEqualTo("uid-1");
    }

    @Test
    void equalsAndHashCodeForIdenticalComponents() {
        var a = new UserContext("uid-1", "user@example.com", true);
        var b = new UserContext("uid-1", "user@example.com", true);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void notEqualWhenUserIdDiffers() {
        var a = new UserContext("uid-1", "user@example.com", true);
        var b = new UserContext("uid-2", "user@example.com", true);
        assertThat(a).isNotEqualTo(b);
    }
}
