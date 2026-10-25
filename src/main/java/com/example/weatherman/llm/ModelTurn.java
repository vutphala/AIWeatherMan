package com.example.weatherman.llm;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public sealed interface ModelTurn {
    record ToolCall(String id, String name, JsonNode args) {}

    /** The model asked for one or more tool calls. */
    record ToolUse(List<ToolCall> calls) implements ModelTurn {}

    /** The model finished without requesting a tool call. */
    record Final(String text) implements ModelTurn {}
}
