package com.example.weatherman.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;

/** Pure argument checks. Returns an error message naming the offending argument, or empty if valid. */
public final class ToolArgumentValidator {
    private ToolArgumentValidator() {}

    public static Optional<String> validateSearchLocations(JsonNode args) {
        JsonNode name = args == null ? null : args.get("name");
        if (name == null || name.isNull()) return error("name", "is required");
        if (!name.isTextual()) return error("name", "must be a string");
        int len = name.asText().length();
        if (len < 1 || len > 100) return error("name", "must be 1 to 100 characters");
        return Optional.empty();
    }

    public static Optional<String> validateGetDailyForecast(JsonNode args) {
        Optional<String> lat = checkRange(args, "latitude", -90, 90);
        if (lat.isPresent()) return lat;
        return checkRange(args, "longitude", -180, 180);
    }

    private static Optional<String> checkRange(JsonNode args, String field, double min, double max) {
        JsonNode v = args == null ? null : args.get(field);
        if (v == null || v.isNull()) return error(field, "is required");
        if (!v.isNumber()) return error(field, "must be a number");
        double d = v.doubleValue();
        if (Double.isNaN(d) || d < min || d > max) return error(field, "must be between " + (int) min + " and " + (int) max);
        return Optional.empty();
    }

    private static Optional<String> error(String arg, String problem) {
        return Optional.of("invalid argument '" + arg + "': " + problem);
    }
}
