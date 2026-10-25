package com.example.weatherman.forecast;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** Extracts and validates a Forecast from free-form model text. */
public final class ForecastParser {

    public sealed interface Result {
        record Parsed(Forecast forecast) implements Result {}
        record LocationNotResolved() implements Result {}
        record Invalid(String reason) implements Result {}
    }

    private static final ObjectMapper STRICT = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES);

    private ForecastParser() {}

    public static Result parse(String text, int expectedDays) {
        if (text == null) return new Result.Invalid("empty model output");
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) return new Result.Invalid("no JSON object found");
        String json = text.substring(start, end + 1);
        try {
            JsonNode node = STRICT.readTree(json);
            if (node.isObject() && node.size() == 1 && "location_not_resolved".equals(node.path("error").asText(null))) {
                return new Result.LocationNotResolved();
            }
            Forecast forecast = STRICT.treeToValue(node, Forecast.class);
            var violations = ForecastValidator.violations(forecast, expectedDays);
            if (!violations.isEmpty()) return new Result.Invalid(String.join("; ", violations));
            return new Result.Parsed(forecast);
        } catch (Exception e) {
            return new Result.Invalid("unparseable forecast JSON");
        }
    }
}
