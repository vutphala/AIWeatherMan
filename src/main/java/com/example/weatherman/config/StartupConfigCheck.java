package com.example.weatherman.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Fails startup when a required setting is missing. Other infrastructure beans use
 * {@code @DependsOn("startupConfigCheck")} so this runs before the web server accepts traffic.
 */
@Component("startupConfigCheck")
public class StartupConfigCheck {

    public StartupConfigCheck(WeatherManProperties props) {
        Map<String, String> required = new LinkedHashMap<>();
        required.put("WEATHERMAN_BEDROCK_MODEL_ID", props.bedrock().modelId());
        required.put("WEATHERMAN_BEDROCK_REGION", props.bedrock().region());
        required.put("WEATHERMAN_PROVIDER_BASE_URL", props.provider().baseUrl());
        required.put("WEATHERMAN_PROVIDER_API_KEY", props.provider().apiKey());
        List<String> missing = ConfigValidator.missingKeys(required);
        if (!missing.isEmpty()) {
            throw new ConfigurationException("Missing required configuration: " + String.join(", ", missing));
        }
        int days = props.forecastDays();
        if (days < WeatherManProperties.Forecast.MIN_DAYS || days > WeatherManProperties.Forecast.MAX_DAYS) {
            throw new ConfigurationException("WEATHERMAN_FORECAST_DAYS must be between "
                    + WeatherManProperties.Forecast.MIN_DAYS + " and " + WeatherManProperties.Forecast.MAX_DAYS
                    + ", got " + days);
        }
    }
}
