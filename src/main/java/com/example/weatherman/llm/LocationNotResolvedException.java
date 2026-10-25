package com.example.weatherman.llm;

public class LocationNotResolvedException extends RuntimeException {
    public LocationNotResolvedException() {
        super("Could not determine a location. Please provide a specific place name.");
    }
}
