package com.example.weatherman.logging;

import java.util.Collection;

/** Pure: replaces every occurrence of each non-empty secret with "***". */
public final class Redactor {
    private Redactor() {}

    public static String redact(String text, Collection<String> secrets) {
        if (text == null || secrets == null) return text;
        String out = text;
        // Longest first so a secret that contains another is fully masked.
        var sorted = secrets.stream().filter(s -> s != null && !s.isEmpty())
                .sorted((a, b) -> Integer.compare(b.length(), a.length())).toList();
        for (String s : sorted) out = out.replace(s, "***");
        return out;
    }
}
