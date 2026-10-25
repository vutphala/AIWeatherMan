package com.example.weatherman.web;

import com.example.weatherman.forecast.Forecast;
import com.example.weatherman.llm.ForecastOrchestrator;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ForecastController {
    static final long DEADLINE_SECONDS = 30;

    private final ForecastOrchestrator orchestrator;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ForecastController(ForecastOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping(value = "/api/GetWeatherForecast", consumes = "application/json", produces = "application/json")
    public Forecast getWeatherForecast(@RequestBody JsonNode body) throws Exception {
        QueryValidator.Validation v = QueryValidator.validate(body);
        if (v instanceof QueryValidator.Validation.Invalid invalid) {
            throw new InvalidQueryException(invalid.rule(), invalid.message());
        }
        String query = ((QueryValidator.Validation.Valid) v).query();
        String requestId = MDC.get("requestId");

        // ExecutorService.submit (not CompletableFuture) so cancel(true) interrupts the worker
        // and in-flight Bedrock/tool calls stop once the deadline passes.
        Future<Forecast> future = executor.submit(() -> {
            if (requestId != null) MDC.put("requestId", requestId);
            try {
                return orchestrator.forecast(query);
            } finally {
                MDC.remove("requestId");
            }
        });
        try {
            return future.get(DEADLINE_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new DeadlineExceededException();
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new DeadlineExceededException();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw e;
        }
    }

    public static class InvalidQueryException extends RuntimeException {
        private final String rule;

        public InvalidQueryException(String rule, String message) {
            super(message);
            this.rule = rule;
        }

        public String rule() {
            return rule;
        }
    }

    public static class DeadlineExceededException extends RuntimeException {
        public DeadlineExceededException() {
            super("The request took longer than " + DEADLINE_SECONDS + " seconds.");
        }
    }
}
