package com.example.weatherman.config;

/** Startup configuration error. Messages name keys only, never values. */
public class ConfigurationException extends RuntimeException {
    public ConfigurationException(String message) {
        super(message);
    }
}
