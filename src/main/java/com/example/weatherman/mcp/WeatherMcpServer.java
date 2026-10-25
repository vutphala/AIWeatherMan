package com.example.weatherman.mcp;

import com.example.weatherman.config.WeatherManProperties;
import com.example.weatherman.llm.ToolDefinition;
import com.example.weatherman.llm.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

/**
 * In-process MCP-style weather server. It exposes tools with a name, description and JSON input
 * schema, validates arguments, queries the provider, and answers in MCP tool-result form
 * (single text item plus an isError flag). Nothing is exposed on the network.
 *
 * Constructed as a bean at startup, so a failure here aborts application startup before the web
 * server accepts requests.
 */
@Component
@DependsOn("startupConfigCheck")
public class WeatherMcpServer {
    public static final String SEARCH_LOCATIONS = "search_locations";
    public static final String GET_DAILY_FORECAST = "get_daily_forecast";

    private final ObjectMapper mapper = new ObjectMapper();
    private final WeatherProviderClient provider;
    private final List<ToolDefinition> tools;
    private final int forecastDays;

    public WeatherMcpServer(WeatherProviderClient provider, WeatherManProperties props) {
        this.provider = provider;
        this.forecastDays = props.forecastDays();
        try {
            this.tools = List.of(
                    new ToolDefinition(SEARCH_LOCATIONS,
                            "Search for places by name. Returns up to " + ProviderMapper.MAX_CANDIDATES
                                    + " candidates with displayName, latitude, longitude and timezone.",
                            mapper.readTree("""
                                    {"type":"object","properties":{"name":{"type":"string","minLength":1,"maxLength":100,
                                    "description":"Place name, for example 'Seattle'"}},"required":["name"]}""")),
                    new ToolDefinition(GET_DAILY_FORECAST,
                            "Get " + forecastDays + " days of daily weather for a coordinate, starting today in the location's timezone.",
                            mapper.readTree("""
                                    {"type":"object","properties":{
                                    "latitude":{"type":"number","minimum":-90,"maximum":90},
                                    "longitude":{"type":"number","minimum":-180,"maximum":180}},
                                    "required":["latitude","longitude"]}""")));
        } catch (Exception e) {
            throw new IllegalStateException("MCP_Server failed to initialize", e);
        }
    }

    public List<ToolDefinition> listTools() {
        return tools;
    }

    public ToolResult callTool(String name, JsonNode args) {
        try {
            if (SEARCH_LOCATIONS.equals(name)) {
                Optional<String> err = ToolArgumentValidator.validateSearchLocations(args);
                if (err.isPresent()) return ToolResult.error(err.get());
                ObjectNode out = ProviderMapper.mapLocations(provider.searchLocations(args.get("name").asText()));
                return ToolResult.ok(mapper.writeValueAsString(out));
            }
            if (GET_DAILY_FORECAST.equals(name)) {
                Optional<String> err = ToolArgumentValidator.validateGetDailyForecast(args);
                if (err.isPresent()) return ToolResult.error(err.get());
                ObjectNode out = ProviderMapper.mapForecast(
                        provider.dailyForecast(args.get("latitude").asDouble(), args.get("longitude").asDouble()),
                        forecastDays);
                return ToolResult.ok(mapper.writeValueAsString(out));
            }
            return ToolResult.error("unknown tool '" + name + "'");
        } catch (WeatherProviderClient.ProviderException e) {
            return ToolResult.error(e.getMessage());
        } catch (IllegalArgumentException e) {
            // Raised by ProviderMapper for incomplete provider payloads; the message names the gap.
            return ToolResult.error(e.getMessage());
        } catch (Exception e) {
            return ToolResult.error("tool failed: " + e.getClass().getSimpleName());
        }
    }
}
