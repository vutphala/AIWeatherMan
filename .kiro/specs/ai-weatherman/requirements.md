# Requirements Document

## Introduction

AI WeatherMan is a Java Spring Boot web application. A web page presents a weather graphics area, a natural-language input field and a submit button. Submitting a query calls the GetWeatherForecast API. That API uses an Amazon Bedrock client with a GPT-backed model and an MCP server that retrieves data from a third-party weather source. The model interprets the query, uses MCP tools to fetch weather data, and returns a 14-day (Forecast_Days) forecast that the page renders as graphics.

## Glossary

- **WeatherMan_App**: The Spring Boot web application as a whole.
- **Web_Page**: The browser-based user interface served by WeatherMan_App.
- **Query_Input**: The natural-language text input field on the Web_Page.
- **Submit_Button**: The button on the Web_Page that submits the Query_Input content.
- **Graphics_Area**: The region of the Web_Page that renders the forecast visually.
- **GetWeatherForecast_API**: The HTTP endpoint that accepts a natural-language query and returns a forecast.
- **Bedrock_Client**: The component that invokes the GPT-backed model through Amazon Bedrock.
- **Model**: The GPT-backed large language model hosted on Amazon Bedrock.
- **MCP_Server**: The Model Context Protocol server created by WeatherMan_App that exposes weather tools to the Model.
- **Weather_Provider**: The third-party weather data source queried by the MCP_Server.
- **Forecast_Days**: The configured number of daily entries in every Forecast, set by `weatherman.forecast.days` (environment variable `WEATHERMAN_FORECAST_DAYS`); default 14, allowed range 1 to 14.
- **Forecast**: A structured result containing the resolved location and Forecast_Days consecutive daily entries.
- **Daily_Forecast**: One day's entry in a Forecast, with date, temperature high and low in Celsius, conditions summary, precipitation probability percentage and wind speed.
- **Location**: The place name and coordinates the Model resolves from the query.

## Requirements

### Requirement 1: Web Page Query Input

**User Story:** As a user, I want a web page with a query field and a submit button, so that I can ask for weather in plain language.

#### Acceptance Criteria

1. WHEN a user opens the root URL, THE WeatherMan_App SHALL serve the Web_Page containing the Graphics_Area, the Query_Input and the Submit_Button within 2 seconds.
2. WHEN a user activates the Submit_Button with Query_Input text that contains at least 1 non-whitespace character and no more than 500 characters, THE Web_Page SHALL send the text to the GetWeatherForecast_API.
3. IF the Query_Input is empty or contains only whitespace characters when the Submit_Button is activated, THEN THE Web_Page SHALL display a validation message indicating that a query is required, send no request, retain the Query_Input contents, and keep the Submit_Button enabled.
4. IF the Query_Input text exceeds 500 characters when the Submit_Button is activated, THEN THE Web_Page SHALL display a validation message indicating the 500-character limit, send no request, and retain the Query_Input contents.
5. WHILE a request to the GetWeatherForecast_API is pending, THE Web_Page SHALL show a loading indicator, disable the Submit_Button, and send no additional request when the Enter key is pressed or the Submit_Button is activated.
6. WHEN a request to the GetWeatherForecast_API completes with a response or an error, or 30 seconds elapse without a response, THE Web_Page SHALL hide the loading indicator and re-enable the Submit_Button.
7. WHEN a user presses the Enter key while the Query_Input has focus, THE Web_Page SHALL behave as if the Submit_Button were activated.

### Requirement 2: Forecast Graphics

**User Story:** As a user, I want to see the forecast as graphics, so that I can grasp the coming 14 days (Forecast_Days) quickly.

#### Acceptance Criteria

1. WHEN the GetWeatherForecast_API returns a Forecast, THE Graphics_Area SHALL render exactly Forecast_Days entries, one for each Daily_Forecast item, in ascending date order from earliest to latest, within 1000 milliseconds of receiving the response.
2. THE Graphics_Area SHALL display for each Daily_Forecast entry the date, a conditions icon, the temperature high and low (each with its unit), the precipitation probability as a percentage from 0 to 100, and the wind speed (with its unit).
3. WHEN the Graphics_Area renders a Forecast, THE Graphics_Area SHALL display a temperature trend chart plotting the high and low temperature for each of the Forecast_Days days, with the dates on the horizontal axis and temperature on the vertical axis.
4. WHEN the Graphics_Area renders a Forecast, THE Graphics_Area SHALL display the resolved Location name, truncated to a maximum of 100 characters if longer.
5. WHEN a new Forecast is received, THE Graphics_Area SHALL replace all entries, the temperature trend chart and the Location name of the previously displayed Forecast with those of the new Forecast, leaving no data from the previous Forecast visible.
6. IF the GetWeatherForecast_API returns an error or does not respond within 10 seconds, THEN THE Web_Page SHALL display an error message indicating the failure reason and SHALL continue to display the previously displayed Forecast, unchanged, in the Graphics_Area.
7. IF the GetWeatherForecast_API returns a Forecast containing fewer or more than Forecast_Days Daily_Forecast items, or a Daily_Forecast missing any of the fields listed in criterion 2, THEN THE Web_Page SHALL display an error message indicating the Forecast is incomplete and SHALL retain the previously displayed Forecast, unchanged, in the Graphics_Area.
8. THE Web_Page SHALL obtain Forecast_Days from the WeatherMan_App (GET /api/config) rather than hardcoding it.

### Requirement 3: GetWeatherForecast API

**User Story:** As a client of the API, I want a GetWeatherForecast endpoint, so that I can request a forecast with a natural-language query.

#### Acceptance Criteria

1. WHEN an HTTP POST request with a JSON body containing a query string is received, THE GetWeatherForecast_API SHALL process the request.
2. WHEN a request is valid (the query is a string of 1 to 500 characters containing at least one non-whitespace character, and the Model resolves a Location), THE GetWeatherForecast_API SHALL return an HTTP 200 response with a JSON Forecast.
3. WHEN a valid request succeeds, THE GetWeatherForecast_API SHALL return a Forecast containing exactly Forecast_Days Daily_Forecast entries on consecutive calendar dates, where the first entry's date is the current date at the Location and each later entry is one day after the previous entry.
4. IF the query is missing, is not a string, is empty, contains only whitespace characters, or is longer than 500 characters, THEN THE GetWeatherForecast_API SHALL return an HTTP 400 response with a JSON error message indicating which validation rule failed, and SHALL NOT invoke the Model.
5. IF the request body is not valid JSON, THEN THE GetWeatherForecast_API SHALL return an HTTP 400 response with a JSON error message indicating the body is malformed.
6. IF the Model cannot resolve a Location from the query, THEN THE GetWeatherForecast_API SHALL return an HTTP 422 response with a JSON error message asking the client to provide a specific location.
7. FOR ALL valid Forecast objects, serializing to JSON then deserializing SHALL produce an equivalent Forecast (round-trip property).
8. WHEN a valid request is received, THE GetWeatherForecast_API SHALL return the HTTP 200 response within 30 seconds of receiving the request.
9. IF a valid request is not completed within 30 seconds or the Model is unavailable, THEN THE GetWeatherForecast_API SHALL return an HTTP 504 response for a timeout or an HTTP 502 response for Model unavailability, each with a JSON error message indicating the cause.

### Requirement 4: Amazon Bedrock Integration

**User Story:** As a developer, I want the API to call the Model through Amazon Bedrock, so that natural-language queries are interpreted by the GPT-backed model.

#### Acceptance Criteria

1. WHEN the WeatherMan_App starts, THE Bedrock_Client SHALL read the model identifier and the AWS region from application configuration and use them for every Model invocation.
2. WHEN the GetWeatherForecast_API receives a valid query, THE Bedrock_Client SHALL send the query and the definitions of all tools exposed by the MCP_Server to the Model in the first Model invocation of the request.
3. WHEN the Model requests a tool call, THE Bedrock_Client SHALL execute the call through the MCP_Server, return the result to the Model, and count the exchange as one Model and tool-call round.
4. WHEN the Model completes without requesting a further tool call, THE WeatherMan_App SHALL parse the Model output into a Forecast.
5. IF the Model output cannot be parsed into a Forecast with exactly Forecast_Days Daily_Forecast entries, THEN THE GetWeatherForecast_API SHALL return an HTTP 502 response with an error message indicating that the Model output was invalid, and SHALL NOT return a partial Forecast.
6. IF Amazon Bedrock returns a throttling error or a service error, or does not respond within 60 seconds of a Model invocation, THEN THE GetWeatherForecast_API SHALL return an HTTP 503 response with an error message indicating that the Model service is unavailable.
7. THE Bedrock_Client SHALL obtain AWS credentials from the AWS default credentials provider chain.
8. WHILE a request is being processed, THE WeatherMan_App SHALL permit at most 5 Model and tool-call rounds for that request.
9. IF the Model requests a tool call after the 5th round has completed, THEN THE GetWeatherForecast_API SHALL stop invoking the Model and return an HTTP 502 response with an error message indicating that the round limit was exceeded.
10. IF the model identifier or the AWS region is missing or empty in application configuration, THEN THE WeatherMan_App SHALL fail to start and report an error identifying the missing setting.
11. IF an MCP_Server tool call fails or does not complete within 30 seconds, THEN THE Bedrock_Client SHALL return an error result for that call to the Model and continue the request, and that exchange SHALL count toward the 5-round limit.
12. IF Forecast_Days is not an integer from 1 to 14, THEN THE WeatherMan_App SHALL fail to start and report an error naming WEATHERMAN_FORECAST_DAYS.

### Requirement 5: MCP Weather Server

**User Story:** As a developer, I want an MCP server that wraps the third-party weather source, so that the Model can fetch real weather data through standard tools.

#### Acceptance Criteria

1. WHEN WeatherMan_App starts, THE WeatherMan_App SHALL create the MCP_Server before accepting the first user request.
2. IF the MCP_Server cannot be created at startup, THEN THE WeatherMan_App SHALL stop startup and report an error indicating the MCP_Server failed to initialize.
3. THE MCP_Server SHALL expose a tool that accepts a Location (latitude from -90 to 90 and longitude from -180 to 180) and returns Forecast_Days days of daily weather data from the Weather_Provider, one entry per day, starting with the current date.
4. THE MCP_Server SHALL expose a tool that accepts a place name of 1 to 100 characters and returns between 0 and 14 matching Location candidates, each with a display name, latitude and longitude.
5. WHEN the place name search matches no Location, THE MCP_Server SHALL return an empty candidate list as a successful MCP tool result.
6. THE MCP_Server SHALL describe each tool with a name, a description and a JSON input schema.
7. WHEN a tool is called with valid arguments, THE MCP_Server SHALL query the Weather_Provider and return the result in the MCP tool result format.
8. IF a tool is called with a missing, wrongly typed or out-of-range argument, THEN THE MCP_Server SHALL return an MCP tool error naming the invalid argument and SHALL NOT query the Weather_Provider.
9. IF the Weather_Provider is unreachable or returns an error status, THEN THE MCP_Server SHALL return an MCP tool error indicating the failure reason.
10. IF the Weather_Provider does not respond within 10 seconds, THEN THE MCP_Server SHALL return an MCP tool error indicating a timeout.
11. THE WeatherMan_App SHALL read the Weather_Provider base URL and API key from application configuration.
12. IF the Weather_Provider base URL or API key is missing or empty in application configuration, THEN THE WeatherMan_App SHALL stop startup and report an error naming the missing setting.

### Requirement 6: Security and Observability

**User Story:** As an operator, I want secure configuration and observability, so that I can run the application safely and diagnose problems.

#### Acceptance Criteria

1. THE WeatherMan_App SHALL read secrets only from environment variables or an external secrets store and keep them out of source control.
2. IF a required secret is missing or empty at startup, THEN THE WeatherMan_App SHALL refuse to start, exit with a non-zero exit code, and write a startup error to the log that names the missing configuration key without including any secret value.
3. THE WeatherMan_App SHALL exclude secrets and AWS credentials from logs and API responses.
4. THE WeatherMan_App SHALL bind to the loopback network interface (127.0.0.1) by default and SHALL accept connections from non-loopback interfaces only WHERE the operator explicitly configures a different bind address.
5. WHEN a request to the GetWeatherForecast_API completes, THE WeatherMan_App SHALL write one log entry containing the request identifier, the status code, and the duration in milliseconds, with the duration rounded to a whole millisecond.
6. WHEN a Bedrock call or an MCP tool call fails, THE WeatherMan_App SHALL write one log entry at error level containing the request identifier, the name of the failed dependency (Bedrock or the MCP tool name), and the failure reason, without including secret values or AWS credentials.
7. THE WeatherMan_App SHALL expose a health endpoint that, WHEN requested, responds within 1000 milliseconds with an indication of application status (healthy or unhealthy) and requires no credentials and no calls to Bedrock or MCP tools.
