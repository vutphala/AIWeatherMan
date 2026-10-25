package com.example.weatherman.mcp;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import com.example.weatherman.config.WeatherManProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** HTTP client for the third-party weather provider (Open-Meteo style API). */
@Component
@DependsOn("startupConfigCheck")
public class WeatherProviderClient {
    public static class ProviderException extends Exception {
        public ProviderException(String message) {
            super(message);
        }
    }

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String baseUrl;
    private final String geocodingUrl;
    private final String apiKey;
    private final int forecastDays;

    public WeatherProviderClient(WeatherManProperties props) {
        this.forecastDays = props.forecastDays();
        this.baseUrl = props.provider().baseUrl();
        this.geocodingUrl = props.provider().geocodingUrl();
        this.apiKey = props.provider().apiKey();
    }

    public JsonNode searchLocations(String name) throws ProviderException {
        String url = geocodingUrl + "?name=" + enc(name) + "&count=" + ProviderMapper.MAX_CANDIDATES + "&language=en&format=json" + keyParam();
        return get(url);
    }

    public JsonNode dailyForecast(double latitude, double longitude) throws ProviderException {
        String url = baseUrl + "?latitude=" + latitude + "&longitude=" + longitude
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_speed_10m_max"
                + "&forecast_days=" + forecastDays + "&timezone=auto" + keyParam();
        return get(url);
    }

    private String keyParam() {
        // Open-Meteo's free hosts take no key and 303-redirect keyed requests to the paid host,
        // so the key is only sent to other (for example customer-api) endpoints.
        if (baseUrl.contains("://api.open-meteo.com")) return "";
        return "&apikey=" + enc(apiKey);
    }

    private JsonNode get(String url) throws ProviderException {
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();
        } catch (IllegalArgumentException e) {
            throw new ProviderException("weather provider URL is invalid");
        }
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                throw new ProviderException("weather provider returned status " + res.statusCode());
            }
            return mapper.readTree(res.body());
        } catch (HttpTimeoutException e) {
            throw new ProviderException("weather provider timed out after 10 seconds");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderException("weather provider call was interrupted");
        } catch (IOException e) {
            throw new ProviderException("weather provider is unreachable or returned an unreadable response");
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
