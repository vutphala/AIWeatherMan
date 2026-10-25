package com.example.weatherman.mcp;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import com.example.weatherman.llm.ToolClient;
import com.example.weatherman.llm.ToolDefinition;
import com.example.weatherman.llm.ToolResult;
import com.example.weatherman.logging.Secrets;
import com.fasterxml.jackson.databind.JsonNode;

/** Calls the MCP server with a 30 s per-call timeout; failures become error results. */
@Component
public class McpToolClient implements ToolClient {
    private static final Logger log = LoggerFactory.getLogger(McpToolClient.class);
    static final long TIMEOUT_SECONDS = 30;

    private final WeatherMcpServer server;
    private final Secrets secrets;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public McpToolClient(WeatherMcpServer server, Secrets secrets) {
        this.server = server;
        this.secrets = secrets;
    }

    @Override
    public List<ToolDefinition> listTools() {
        return server.listTools();
    }

    @Override
    public ToolResult callTool(String name, JsonNode args) {
        String requestId = MDC.get("requestId");
        // submit (not CompletableFuture) so cancel(true) actually interrupts the provider HTTP call.
        Future<ToolResult> f = executor.submit(() -> server.callTool(name, args));
        try {
            ToolResult r = f.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (r.isError()) logFailure(requestId, name, r.text());
            return r;
        } catch (TimeoutException e) {
            f.cancel(true);
            logFailure(requestId, name, "tool call timed out after " + TIMEOUT_SECONDS + " seconds");
            return ToolResult.error("tool call timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            f.cancel(true);
            return ToolResult.error("tool call was interrupted");
        } catch (ExecutionException e) {
            logFailure(requestId, name, e.getCause() == null ? "unknown" : e.getCause().getClass().getSimpleName());
            return ToolResult.error("tool call failed");
        }
    }

    private void logFailure(String requestId, String tool, String reason) {
        log.error("requestId={} dependency={} failure={}", requestId, tool, secrets.redact(reason));
    }
}
