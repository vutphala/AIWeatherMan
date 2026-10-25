package com.example.weatherman.web;

import com.example.weatherman.forecast.ErrorResponse;
import com.example.weatherman.llm.InvalidModelOutputException;
import com.example.weatherman.llm.LocationNotResolvedException;
import com.example.weatherman.llm.ModelUnavailableException;
import com.example.weatherman.llm.RoundLimitExceededException;
import com.example.weatherman.logging.Secrets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private final Secrets secrets;

    public ApiExceptionHandler(Secrets secrets) {
        this.secrets = secrets;
    }

    @ExceptionHandler(ForecastController.InvalidQueryException.class)
    ResponseEntity<ErrorResponse> invalidQuery(ForecastController.InvalidQueryException e) {
        return respond(HttpStatus.BAD_REQUEST, "invalid_query_" + e.rule(), e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> malformed(HttpMessageNotReadableException e) {
        return respond(HttpStatus.BAD_REQUEST, "malformed_body", "The request body is not valid JSON.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorResponse> mediaType(HttpMediaTypeNotSupportedException e) {
        return respond(HttpStatus.BAD_REQUEST, "malformed_body", "The request body must be JSON (Content-Type: application/json).");
    }

    @ExceptionHandler(LocationNotResolvedException.class)
    ResponseEntity<ErrorResponse> location(LocationNotResolvedException e) {
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, "location_not_resolved", e.getMessage());
    }

    @ExceptionHandler(InvalidModelOutputException.class)
    ResponseEntity<ErrorResponse> invalidOutput(InvalidModelOutputException e) {
        return respond(HttpStatus.BAD_GATEWAY, "invalid_model_output", "The model output was invalid.");
    }

    @ExceptionHandler(RoundLimitExceededException.class)
    ResponseEntity<ErrorResponse> rounds(RoundLimitExceededException e) {
        return respond(HttpStatus.BAD_GATEWAY, "round_limit_exceeded", "The round limit was exceeded.");
    }

    @ExceptionHandler(ModelUnavailableException.class)
    ResponseEntity<ErrorResponse> unavailable(ModelUnavailableException e) {
        return respond(HttpStatus.SERVICE_UNAVAILABLE, "model_unavailable", "The model service is unavailable.");
    }

    @ExceptionHandler(ForecastController.DeadlineExceededException.class)
    ResponseEntity<ErrorResponse> deadline(ForecastController.DeadlineExceededException e) {
        return respond(HttpStatus.GATEWAY_TIMEOUT, "timeout", e.getMessage());
    }

    /**
     * Spring's own web exceptions (404 no resource, 405 wrong method, 406, ...) already carry the
     * right status. Keep it instead of letting the catch-all below turn them into a 500.
     */
    private ResponseEntity<ErrorResponse> springWeb(org.springframework.web.ErrorResponse e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        if (status == null) status = HttpStatus.BAD_REQUEST;
        String code = switch (status) {
            case NOT_FOUND -> "not_found";
            case METHOD_NOT_ALLOWED -> "method_not_allowed";
            default -> "request_error";
        };
        String message = switch (status) {
            case NOT_FOUND -> "The requested resource was not found.";
            case METHOD_NOT_ALLOWED -> "This HTTP method is not supported for this URL.";
            default -> status.getReasonPhrase();
        };
        return respond(status, code, message);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> other(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse springError) {
            return springWeb(springError);
        }
        log.error("requestId={} unexpected failure: {}", MDC.get("requestId"),
                secrets.redact(e.getClass().getSimpleName() + ": " + e.getMessage()));
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "An unexpected error occurred.");
    }

    private ResponseEntity<ErrorResponse> respond(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(code, secrets.redact(message)));
    }
}
