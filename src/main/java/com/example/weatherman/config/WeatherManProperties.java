package com.example.weatherman.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("weatherman")
public record WeatherManProperties(
        @DefaultValue Bedrock bedrock, @DefaultValue Provider provider, @DefaultValue Forecast forecast) {
    public record Bedrock(String modelId, String region) {}

    public record Provider(String baseUrl, String apiKey, String geocodingUrl) {}

    /** days: number of daily entries in every forecast (single source of truth, 1 to 14). */
    public record Forecast(@DefaultValue("14") int days) {
        public static final int MIN_DAYS = 1;
        public static final int MAX_DAYS = 16; // app limit; Open-Meteo itself allows up to 16
    }

    public int forecastDays() {
        return 14;// forecast.days();
    }
}
