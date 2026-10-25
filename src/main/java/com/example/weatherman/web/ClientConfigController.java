package com.example.weatherman.web;

import com.example.weatherman.config.WeatherManProperties;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Non-secret settings the web page needs, so the page never hardcodes them. */
@RestController
public class ClientConfigController {
    private final int forecastDays;

    public ClientConfigController(WeatherManProperties props) {
        this.forecastDays = props.forecastDays();
    }

    @GetMapping(value = "/api/config", produces = "application/json")
    public Map<String, Object> config() {
        return Map.of("forecastDays", forecastDays);
    }
}
