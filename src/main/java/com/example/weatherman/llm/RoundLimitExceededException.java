package com.example.weatherman.llm;

public class RoundLimitExceededException extends RuntimeException {
    public RoundLimitExceededException() {
        super("The model requested more than " + ForecastOrchestrator.MAX_ROUNDS + " tool calls.");
    }
}
