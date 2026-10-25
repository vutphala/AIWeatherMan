package com.example.weatherman.forecast;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Pure validation of a Forecast. An empty list of violations means valid. */
public final class ForecastValidator {
    private ForecastValidator() {}

    /** @param expectedDays the configured forecast length (weatherman.forecast.days) */
    public static List<String> violations(Forecast f, int expectedDays) {
        List<String> v = new ArrayList<>();
        if (f == null) {
            v.add("forecast is null");
            return v;
        }
        Location loc = f.location();
        if (loc == null) {
            v.add("location is missing");
        } else {
            if (isBlank(loc.name())) v.add("location.name is empty");
            if (isBlank(loc.timezone())) v.add("location.timezone is empty");
            if (!finite(loc.latitude()) || loc.latitude() < -90 || loc.latitude() > 90) v.add("location.latitude out of range");
            if (!finite(loc.longitude()) || loc.longitude() < -180 || loc.longitude() > 180) v.add("location.longitude out of range");
        }
        List<DailyForecast> days = f.days();
        if (days == null || days.size() != expectedDays) {
            v.add("days must contain exactly " + expectedDays + " entries");
            return v;
        }
        LocalDate prev = null;
        for (int i = 0; i < days.size(); i++) {
            DailyForecast d = days.get(i);
            String p = "days[" + i + "].";
            if (d == null) {
                v.add(p + "entry is null");
                continue;
            }
            if (d.date() == null) {
                v.add(p + "date is missing");
            } else {
                if (prev != null && !d.date().equals(prev.plusDays(1))) v.add(p + "date is not the day after the previous entry");
                prev = d.date();
            }
            if (!finite(d.highC())) v.add(p + "highC is not finite");
            if (!finite(d.lowC())) v.add(p + "lowC is not finite");
            if (!finite(d.windSpeedKmh())) v.add(p + "windSpeedKmh is not finite");
            if (isBlank(d.conditions())) v.add(p + "conditions is empty");
            if (isBlank(d.conditionCode())) v.add(p + "conditionCode is empty");
            if (d.precipitationProbabilityPct() < 0 || d.precipitationProbabilityPct() > 100) {
                v.add(p + "precipitationProbabilityPct must be between 0 and 100");
            }
        }
        return v;
    }

    public static boolean isValid(Forecast f, int expectedDays) {
        return violations(f, expectedDays).isEmpty();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static boolean finite(double d) {
        return !Double.isNaN(d) && !Double.isInfinite(d);
    }
}
