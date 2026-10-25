# Implementation Plan: AI WeatherMan

## Overview

Implementation follows the design in Java 21 with Spring Boot, plus a static HTML/CSS/ES-module front end. Pure logic (validation, parsing, mapping, redaction, config checks, round-limit loop) is built first so it can be property-tested without AWS or the weather provider. The MCP server, Bedrock client and web layer are then wired on top, followed by the front end.

## Tasks

- [ ] 1. Set up project structure
  - [ ] 1.1 Create the Spring Boot (Java 21) Maven project and base configuration
    - Create `pom.xml` with Spring Boot web and actuator, AWS SDK v2 `bedrockruntime`, MCP Java SDK, Jackson (JSR-310), jqwik, JUnit 5, WireMock and spring-boot-starter-test, using pinned versions
    - Create `com.example.weatherman` package layout (`config`, `web`, `forecast`, `llm`, `mcp`, `logging`) and `WeatherManApplication`
    - Create `application.yml` with `server.address: 127.0.0.1`, actuator exposing only `health`, and non-secret defaults only
    - Create `.gitignore` excluding `.env*` and build output
    - Create `src/main/resources/static/` and a minimal front-end test setup (`package.json` with fast-check and jsdom, Node test runner)
    - _Requirements: 6.1, 6.4_

- [ ] 2. Implement forecast domain and pure validation logic
  - [ ] 2.1 Create forecast data models and Jackson configuration
    - Implement `Location`, `DailyForecast`, `Forecast` and `ErrorResponse` records
    - Configure ISO-8601 dates, fail-on-unknown-properties for model output parsing
    - _Requirements: 2.2, 3.2, 3.7_

  - [ ]* 2.2 Write property test for Forecast JSON round trip
    - **Property 2: Forecast JSON round trip**
    - **Validates: Requirements 3.7**

  - [ ] 2.3 Implement `ForecastValidator`
    - Check location fields, exactly Forecast_Days entries, consecutive ascending dates, required fields, precipitation 0 to 100, finite numbers
    - _Requirements: 2.7, 3.3_

  - [ ]* 2.4 Write property test for forecast validity classification
    - **Property 3: Forecast validity classification**
    - **Validates: Requirements 2.7, 3.3**

  - [ ] 2.5 Implement `ForecastParser`
    - Extract the outermost JSON object (tolerating prose and Markdown fences), deserialize, validate, return `Optional<Forecast>`
    - Recognize the `{"error":"location_not_resolved"}` marker as a distinct result
    - _Requirements: 3.6, 4.4, 4.5_

  - [ ]* 2.6 Write property test for model output parsing
    - **Property 4: Model output parsing**
    - **Validates: Requirements 4.4, 4.5**

  - [ ] 2.7 Implement `QueryValidator`
    - Rules in order: `missing`, `not_a_string`, `blank`, `too_long` (over 500 characters)
    - _Requirements: 3.4_

  - [ ]* 2.8 Write property test for query validation (Java)
    - **Property 1: Query validation classification**
    - **Validates: Requirements 1.2, 1.3, 1.4, 3.4**

- [ ] 3. Implement configuration and security utilities
  - [ ] 3.1 Implement `WeatherManProperties`, `ConfigValidator` and the startup check
    - Bind `weatherman.bedrock.model-id`, `region`, `provider.base-url`, `api-key` from environment variables
    - Bind `weatherman.forecast.days` (default 14) and fail startup unless it is 1 to 14
    - Pure `ConfigValidator` returns null or blank keys; startup bean throws `ConfigurationException` naming keys only, never values
    - _Requirements: 4.1, 4.10, 5.11, 5.12, 6.1, 6.2_

  - [ ]* 3.2 Write property test for required configuration check
    - **Property 7: Required configuration check**
    - **Validates: Requirements 4.10, 5.12, 6.2**

  - [ ] 3.3 Implement `Redactor`
    - Replace each non-empty secret with `***`; include resolved AWS credential values in the secrets set at startup
    - _Requirements: 6.3_

  - [ ]* 3.4 Write property test for secret redaction
    - **Property 8: Secret redaction**
    - **Validates: Requirements 6.3**

- [ ] 4. Implement the MCP weather server
  - [ ] 4.1 Implement `ToolArgumentValidator`
    - Validate `search_locations` (name 1 to 100 chars) and `get_daily_forecast` (latitude, longitude ranges and types); errors name the argument
    - _Requirements: 5.3, 5.4, 5.8_

  - [ ]* 4.2 Write property test for tool argument validation
    - **Property 9: Tool argument validation**
    - **Validates: Requirements 5.8**

  - [ ] 4.3 Implement `ProviderMapper` and `WeatherProviderClient`
    - Pure mapper: Forecast_Days daily entries in date order, at most 14 location candidates, empty list is a success
    - HTTP client with base URL and API key from config and a 10 s timeout; map unreachable, error status and timeout to tool errors with a reason
    - _Requirements: 5.3, 5.4, 5.5, 5.9, 5.10, 5.11_

  - [ ]* 4.4 Write property test for provider mapping
    - **Property 10: Provider mapping**
    - **Validates: Requirements 5.3, 5.4, 5.5**

  - [ ] 4.5 Implement `WeatherMcpServer`, `McpLifecycle` and the `ToolClient` implementation
    - Register `search_locations` and `get_daily_forecast` with names, descriptions and JSON input schemas
    - Validate arguments before calling the provider; return results in MCP tool result format
    - `SmartLifecycle` with early phase creates the server before the web server accepts traffic; failure aborts startup with an "MCP_Server failed to initialize" error
    - In-memory MCP client transport with 30 s per-call timeout; failures become error results and never throw
    - _Requirements: 4.11, 5.1, 5.2, 5.6, 5.7, 5.8_

  - [ ]* 4.6 Write unit tests for the MCP server
    - WireMock provider: valid call, provider 500, unreachable, 10 s timeout, invalid arguments do not reach provider, tool list metadata
    - _Requirements: 5.6, 5.7, 5.8, 5.9, 5.10_

- [ ] 5. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 6. Implement the Bedrock integration and orchestrator
  - [ ] 6.1 Implement `ForecastOrchestrator` with `ModelClient`, `ToolClient`, `ModelTurn` and exceptions
    - First invocation sends query and all tool definitions; loop with `MAX_ROUNDS = 5`; failures returned to the model as error results and counted
    - Throw `RoundLimitExceededException` on a tool request after round 5; parse final text via `ForecastParser`
    - Check first date equals today in the Location timezone; mismatch or invalid output is invalid model output; location marker yields location-not-resolved
    - Include the system prompt describing the tools and Forecast schema
    - _Requirements: 3.3, 3.6, 4.2, 4.3, 4.4, 4.5, 4.8, 4.9, 4.11_

  - [ ]* 6.2 Write property test for orchestrator round limit
    - **Property 6: Orchestrator round limit**
    - **Validates: Requirements 4.3, 4.8, 4.9, 4.11**

  - [ ] 6.3 Implement `BedrockModelClient`
    - Use `BedrockRuntimeClient.converse` with `DefaultCredentialsProvider`, model id and region from config, 60 s API call timeout
    - Translate throttling, service errors and timeouts to `ModelUnavailableException`; log failures with redaction
    - _Requirements: 4.1, 4.6, 4.7, 6.6_

- [ ] 7. Implement the web layer, logging and health
  - [ ] 7.1 Implement `ForecastController` and `ApiExceptionHandler`
    - `POST /api/GetWeatherForecast` taking `JsonNode`, validating via `QueryValidator` before any model call, with a 30 s deadline
    - Map errors: 400 (invalid or malformed), 422, 502 (invalid output, round limit), 503 (Bedrock unavailable), 504 (deadline); redact error bodies
    - _Requirements: 3.1, 3.2, 3.4, 3.5, 3.6, 3.8, 3.9, 4.5, 4.6, 4.9, 6.3_

  - [ ] 7.2 Implement `RequestLoggingFilter` and the health endpoint
    - Request id in MDC; one log entry per request with id, status and whole-millisecond duration, applied only to the forecast endpoint
    - ERROR log entries for Bedrock and MCP failures with request id, dependency name and redacted reason
    - Actuator `/actuator/health` with a simple indicator and no Bedrock or MCP calls
    - _Requirements: 6.5, 6.6, 6.7_

  - [ ]* 7.3 Write controller and logging tests
    - MockMvc: 200, 400, 422, 502, 503 and 504 mappings; log entry format; health without credentials
    - _Requirements: 3.1, 3.2, 3.5, 3.6, 3.8, 3.9, 6.5, 6.6, 6.7_

- [ ] 8. Implement the front end
  - [ ] 8.1 Implement `validation.js`
    - `validateQuery(text)` and `validateForecast(f, dayCount)` matching server rules; `dayCount` comes from `GET /api/config`
    - _Requirements: 1.3, 1.4, 2.7_

  - [ ]* 8.2 Write fast-check property tests for client validation
    - **Property 1: Query validation classification**
    - **Property 3: Forecast validity classification**
    - **Validates: Requirements 1.2, 1.3, 1.4, 2.7, 3.3**

  - [ ] 8.3 Implement `viewModel.js`
    - `buildViewModel(forecast)`: ascending sort, location truncation to 100 chars, formatted day labels with units, chart series
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

  - [ ]* 8.4 Write fast-check property test for view model fidelity
    - **Property 5: View model fidelity**
    - **Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5**

  - [ ] 8.5 Implement `index.html`, `app.css`, `chart.js` and `app.js`
    - Page with Graphics_Area, Query_Input (no `maxlength`), Submit_Button, `aria-live` status region and labels
    - SVG temperature trend chart with a visually hidden data table
    - Submit handling: validate, pending state (loading indicator, disabled button, ignore Enter and repeat clicks), fetch with abort timeout, validate response, replace graphics entirely, always clear pending state in `finally`; keep previous Forecast on any error
    - Show a "still working" notice at 10 s; fail at 30 s
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7_

  - [ ]* 8.6 Write jsdom tests for page behavior
    - Pending state, Enter submit, validation messages retain input, error and incomplete forecast retain previous graphics, pending cleared after response, error and 30 s
    - _Requirements: 1.3, 1.4, 1.5, 1.6, 1.7, 2.6, 2.7_

- [ ] 9. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 10. Integration and startup verification
  - [ ]* 10.1 Write startup smoke and integration tests
    - Missing settings fail startup naming the key; MCP server exists before requests are accepted; default bind is `127.0.0.1`; root page is served
    - Opt-in end-to-end test against real Bedrock and provider, excluded from the default build
    - _Requirements: 1.1, 4.1, 4.7, 4.10, 5.1, 5.2, 5.11, 5.12, 6.2, 6.4_

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties
- Unit tests validate specific examples and edge cases
- Requirements 2.6 (10 s error) and 1.6/3.8 (30 s) are inconsistent; the design resolves this with a 10 s notice and 30 s failure point

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["2.1", "3.1", "3.3", "4.1", "8.1", "8.3"] },
    { "id": 2, "tasks": ["2.2", "2.3", "2.7", "3.2", "3.4", "4.2", "4.3", "8.2", "8.4", "8.5"] },
    { "id": 3, "tasks": ["2.4", "2.5", "2.8", "4.4", "4.5", "8.6"] },
    { "id": 4, "tasks": ["2.6", "4.6", "6.1", "6.3"] },
    { "id": 5, "tasks": ["6.2", "7.1", "7.2"] },
    { "id": 6, "tasks": ["7.3", "10.1"] }
  ]
}
```
