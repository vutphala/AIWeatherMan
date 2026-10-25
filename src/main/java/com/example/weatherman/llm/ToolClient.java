package com.example.weatherman.llm;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public interface ToolClient {
    List<ToolDefinition> listTools();

    /** Never throws; failures become {@link ToolResult#error}. */
    ToolResult callTool(String name, JsonNode args);
}
