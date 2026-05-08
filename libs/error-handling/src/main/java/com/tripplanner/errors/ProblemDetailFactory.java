// Source: 00-CONTEXT.md D-05, docs/04-api-spec.md §6 — Phase 0 minimal stub.
// Phase 1's gateway error WebFilter and Phase 2+ services call this to produce
// RFC 7807 ProblemDetail responses with stable `type` URIs and `code` extension.
package com.tripplanner.errors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.net.URI;

public final class ProblemDetailFactory {

    private static final String TYPE_BASE = "https://tripplanner.example.com/errors/";

    private ProblemDetailFactory() {}

    public static ProblemDetail of(HttpStatus status, ErrorCode code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create(TYPE_BASE + code.code()));
        pd.setProperty("code", code.code());
        return pd;
    }
}
