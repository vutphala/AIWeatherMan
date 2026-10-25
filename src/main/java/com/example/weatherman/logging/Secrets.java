package com.example.weatherman.logging;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import com.example.weatherman.config.WeatherManProperties;

/** Holds the secret values known at runtime so logs and error bodies can be redacted. */
@Component
@DependsOn("startupConfigCheck")
public class Secrets {
    private final Set<String> values = ConcurrentHashMap.newKeySet();

    public Secrets(WeatherManProperties props) {
        add(props.provider().apiKey());
        add(System.getenv("AWS_BEARER_TOKEN_BEDROCK"));
    }

    public void add(String secret) {
        if (secret != null && !secret.isEmpty()) values.add(secret);
    }

    public String redact(String text) {
        return Redactor.redact(text, values);
    }
}
