package com.example.weatherman.forecast;

/** error is a stable code such as "invalid_query". */
public record ErrorResponse(String error, String message) {}
