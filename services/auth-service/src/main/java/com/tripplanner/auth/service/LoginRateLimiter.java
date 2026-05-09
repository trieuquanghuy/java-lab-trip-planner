// Source: 02-RESEARCH.md §Pattern 6 lines 615-650; 02-CONTEXT.md D-06/D-07/D-09.
//
// Why Lua, not separate INCR + EXPIRE: non-atomic INCR-then-EXPIRE has a documented race —
//   client INCRs, crashes before EXPIRE → immortal counter (02-RESEARCH.md §Don't Hand-Roll).
//
// Why `>= 5` not `> 5`: D-06 wording — "5 failed-attempts / 15min". The 6th attempt trips:
//   values 1, 2, 3, 4, 5 are "ok"; on attempt 6, exceeded() reads 5 and trips → 429.
package com.tripplanner.auth.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LoginRateLimiter {

    /** KEYS[1]=full key; ARGV[1]=ttl seconds. Atomic INCR + first-time EXPIRE. */
    private static final String LUA_INCR_WITH_TTL = """
        local count = redis.call('INCR', KEYS[1])
        if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
        return count
        """;

    private final StringRedisTemplate redis;
    private final RedisScript<Long> script = RedisScript.of(LUA_INCR_WITH_TTL, Long.class);

    public LoginRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Read-only check before bcrypt verify (D-05). */
    public boolean exceeded(String ip, String emailLower) {
        String v = redis.opsForValue().get(key(ip, emailLower));
        return v != null && Long.parseLong(v) >= 5;
    }

    public void recordFailure(String ip, String emailLower) {
        redis.execute(script, List.of(key(ip, emailLower)),
                "900",   // 15-min TTL — D-06 / docs/04 §7
                "5");
    }

    /** D-07: successful login clears the counter. */
    public void clear(String ip, String emailLower) {
        redis.delete(key(ip, emailLower));
    }

    /** D-06 keyspace prefix `rl:` matches gateway's existing namespace; D-09 lower-only email. */
    private String key(String ip, String emailLower) {
        return "rl:login:fail:" + ip + ":" + emailLower;
    }
}
