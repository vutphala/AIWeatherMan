package com.example.weatherman.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Pure check: returns the keys whose value is null or blank. */
public final class ConfigValidator {
    private ConfigValidator() {}

    public static List<String> missingKeys(Map<String, String> required) {
        List<String> missing = new ArrayList<>();
        required.forEach((key, value) -> {
            if (value == null || value.isBlank()) missing.add(key);
        });
        return missing;
    }
}
