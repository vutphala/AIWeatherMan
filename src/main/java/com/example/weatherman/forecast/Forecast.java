package com.example.weatherman.forecast;

import java.util.List;

public record Forecast(Location location, List<DailyForecast> days) {}
