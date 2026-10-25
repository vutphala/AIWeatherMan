package com.example.weatherman.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Pure mapping from Open-Meteo style payloads to tool results. */
public final class ProviderMapper {
    /** Maximum location candidates returned by search_locations (also the geocoding request size). */
    static final int MAX_CANDIDATES = 14;
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private ProviderMapper() {}

    /** Returns {"timezone": ..., "days": [dayCount entries in date order]}. */
    public static ObjectNode mapForecast(JsonNode body, int dayCount) {
        JsonNode daily = body.path("daily");
        JsonNode time = daily.path("time");
        if (!time.isArray() || time.size() < dayCount) {
            throw new IllegalArgumentException("provider returned fewer than " + dayCount + " days");
        }
        ObjectNode out = JSON.objectNode();
        out.put("timezone", body.path("timezone").asText(""));
        ArrayNode days = out.putArray("days");
        for (int i = 0; i < dayCount; i++) {
            int code = daily.path("weather_code").path(i).asInt(-1);
            ObjectNode d = days.addObject();
            d.put("date", time.get(i).asText());
            d.put("highC", requiredNumber(daily, "temperature_2m_max", i));
            d.put("lowC", requiredNumber(daily, "temperature_2m_min", i));
            d.put("conditions", describe(code));
            d.put("conditionCode", codeKey(code));
            d.put("precipitationProbabilityPct",
                    (int) Math.max(0, Math.min(100, Math.round(requiredNumber(daily, "precipitation_probability_max", i)))));
            d.put("windSpeedKmh", requiredNumber(daily, "wind_speed_10m_max", i));
        }
        return out;
    }

    /** Fails instead of inventing a 0 when the provider omits a value (null or missing). */
    private static double requiredNumber(JsonNode daily, String field, int day) {
        JsonNode v = daily.path(field).path(day);
        if (!v.isNumber()) {
            throw new IllegalArgumentException("provider returned no " + field + " for day " + (day + 1));
        }
        return v.doubleValue();
    }

    /** Returns {"candidates": [0..MAX_CANDIDATES entries]}. An empty match is a success. */
    public static ObjectNode mapLocations(JsonNode body) {
        ObjectNode out = JSON.objectNode();
        ArrayNode list = out.putArray("candidates");
        JsonNode results = body.path("results");
        if (results.isArray()) {
            for (int i = 0; i < results.size() && i < MAX_CANDIDATES; i++) {
                JsonNode r = results.get(i);
                ObjectNode c = list.addObject();
                c.put("displayName", displayName(r));
                c.put("latitude", r.path("latitude").asDouble());
                c.put("longitude", r.path("longitude").asDouble());
                c.put("timezone", r.path("timezone").asText(""));
            }
        }
        return out;
    }

    private static String displayName(JsonNode r) {
        StringBuilder sb = new StringBuilder(r.path("name").asText(""));
        for (String f : new String[] {"admin1", "country"}) {
            String v = r.path(f).asText("");
            if (!v.isEmpty()) sb.append(", ").append(v);
        }
        return sb.toString();
    }

    static String describe(int code) {
        return switch (code) {
            case 0 -> "Clear sky";
            case 1 -> "Mainly clear";
            case 2 -> "Partly cloudy";
            case 3 -> "Overcast";
            case 45, 48 -> "Fog";
            case 51, 53, 55, 56, 57 -> "Drizzle";
            case 61, 63, 65, 66, 67 -> "Rain";
            case 71, 73, 75, 77 -> "Snow";
            case 80, 81, 82 -> "Rain showers";
            case 85, 86 -> "Snow showers";
            case 95, 96, 99 -> "Thunderstorm";
            default -> "Unknown";
        };
    }

    static String codeKey(int code) {
        return switch (code) {
            case 0, 1 -> "clear";
            case 2 -> "partly-cloudy";
            case 3 -> "cloudy";
            case 45, 48 -> "fog";
            case 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> "rain";
            case 71, 73, 75, 77, 85, 86 -> "snow";
            case 95, 96, 99 -> "storm";
            default -> "unknown";
        };
    }
}
