package com.example.weatherman.llm;

import com.example.weatherman.config.WeatherManProperties;
import com.example.weatherman.forecast.Forecast;
import com.example.weatherman.forecast.ForecastParser;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class ForecastOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(ForecastOrchestrator.class);
    /** Maximum number of tool-call exchanges per request. */
    public static final int MAX_ROUNDS = 5;

    /** %1$d is replaced with the configured forecast length. */
    static final String SYSTEM_PROMPT_TEMPLATE = """
            You are a weather assistant. Answer the user's question with a %1$d-day forecast.
            1. Call search_locations to resolve the place named in the query to coordinates.
            2. Call get_daily_forecast with those coordinates to get %1$d days of data starting today.
            3. Reply with ONLY a JSON object, no prose and no Markdown, in exactly this shape:
            {"location":{"name":string,"latitude":number,"longitude":number,"timezone":string},
             "days":[{"date":"YYYY-MM-DD","highC":number,"lowC":number,"conditions":string,
                      "conditionCode":string,"precipitationProbabilityPct":integer 0-100,"windSpeedKmh":number}]}
            "days" must have exactly %1$d entries on consecutive dates, the first being today at the location.
            Copy values from the tool results; do not invent data.
            If no location can be determined from the query, reply with exactly {"error":"location_not_resolved"}.
            """;

    private final ModelClient model;
    private final ToolClient tools;
    private final int forecastDays;
    private final String systemPrompt;

    public ForecastOrchestrator(ModelClient model, ToolClient tools, WeatherManProperties props) {
        this.model = model;
        this.tools = tools;
        this.forecastDays = props.forecastDays();
        this.systemPrompt = SYSTEM_PROMPT_TEMPLATE.formatted(forecastDays);
    }

    public Forecast forecast(String query) {
        List<ToolDefinition> toolDefs = tools.listTools();
        List<Message> messages = new ArrayList<>();
        messages.add(new Message.UserText(query));
        int rounds = 0;

        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("request cancelled after the deadline");
            }
            long t0 = System.nanoTime();
            ModelTurn turn = model.converse(systemPrompt, List.copyOf(messages), toolDefs);
            long modelMs = (System.nanoTime() - t0) / 1_000_000;
            if (turn instanceof ModelTurn.Final done) {
                log.debug("model final answer after {} tool round(s): {} chars in {} ms", rounds, done.text().length(), modelMs);
                return toForecast(done.text());
            }
            ModelTurn.ToolUse use = (ModelTurn.ToolUse) turn;
            log.debug("model requested {} tool call(s) in {} ms (rounds used so far: {}/{})",
                    use.calls().size(), modelMs, rounds, MAX_ROUNDS);
            if (rounds + use.calls().size() > MAX_ROUNDS) {
                log.debug("round limit exceeded: {} used + {} requested > {}", rounds, use.calls().size(), MAX_ROUNDS);
                throw new RoundLimitExceededException();
            }
            messages.add(new Message.AssistantToolUse(use.calls()));
            List<Message.ToolResults.Result> results = new ArrayList<>();
            for (ModelTurn.ToolCall call : use.calls()) {
                long t1 = System.nanoTime();
                ToolResult r = tools.callTool(call.name(), call.args());
                rounds++;
                log.debug("round {}: {} args={} -> {} ({} chars) in {} ms", rounds, call.name(), call.args(),
                        r.isError() ? "ERROR: " + r.text() : "ok", r.text().length(), (System.nanoTime() - t1) / 1_000_000);
                results.add(new Message.ToolResults.Result(call.id(), r));
            }
            messages.add(new Message.ToolResults(results));
        }
    }

    private Forecast toForecast(String text) {
        ForecastParser.Result result = ForecastParser.parse(text, forecastDays);
        if (result instanceof ForecastParser.Result.LocationNotResolved) {
            throw new LocationNotResolvedException();
        }
        if (result instanceof ForecastParser.Result.Invalid invalid) {
            log.warn("requestId={} model output rejected: {}", MDC.get("requestId"), invalid.reason());
            throw new InvalidModelOutputException("The model output was invalid: " + invalid.reason());
        }
        Forecast forecast = ((ForecastParser.Result.Parsed) result).forecast();
        LocalDate today;
        try {
            today = LocalDate.now(ZoneId.of(forecast.location().timezone()));
        } catch (DateTimeException e) {
            throw new InvalidModelOutputException("The model output was invalid: unknown timezone");
        }
        if (!forecast.days().get(0).date().equals(today)) {
            log.warn("requestId={} model output rejected: first day {} is not today ({}) in {}",
                    MDC.get("requestId"), forecast.days().get(0).date(), today, forecast.location().timezone());
            throw new InvalidModelOutputException("The model output was invalid: first day is not today at the location");
        }
        return forecast;
    }
}
