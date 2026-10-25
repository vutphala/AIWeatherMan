package com.example.weatherman.llm;

import com.fasterxml.jackson.databind.JsonNode;

public record ToolDefinition(String name, String description, JsonNode inputSchema) {}
