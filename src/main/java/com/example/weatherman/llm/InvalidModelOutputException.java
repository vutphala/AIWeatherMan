package com.example.weatherman.llm;

public class InvalidModelOutputException extends RuntimeException {
    public InvalidModelOutputException(String message) {
        super(message);
    }
}
