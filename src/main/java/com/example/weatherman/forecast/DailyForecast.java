package com.example.weatherman.forecast;

import java.time.LocalDate;

public record DailyForecast(
        LocalDate date,
        double highC,
        double lowC,
        String conditions,
        String conditionCode,
        int precipitationProbabilityPct,
        double windSpeedKmh) {}
