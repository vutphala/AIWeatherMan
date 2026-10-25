package com.example.weatherman.web;

import com.fasterxml.jackson.databind.JsonNode;

/** Pure validation of the request body. Rules in order: missing, not_a_string, blank, too_long. */
public final class QueryValidator {
    public static final int MAX_LENGTH = 500;

    public sealed interface Validation {
        record Valid(String query) implements Validation {}
        record Invalid(String rule, String message) implements Validation {}
    }

    private QueryValidator() {}

    public static Validation validate(JsonNode body) {
        JsonNode q = body == null ? null : body.get("query");
        if (q == null || q.isNull()) return new Validation.Invalid("missing", "The 'query' field is required.");
        if (!q.isTextual()) return new Validation.Invalid("not_a_string", "The 'query' field must be a string.");
        String s = q.asText();
        if (s.isBlank()) return new Validation.Invalid("blank", "The query must contain at least one non-whitespace character.");
        if (s.length() > MAX_LENGTH) return new Validation.Invalid("too_long", "The query must be at most " + MAX_LENGTH + " characters.");
        return new Validation.Valid(s);
    }
}
