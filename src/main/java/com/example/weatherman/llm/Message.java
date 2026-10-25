package com.example.weatherman.llm;

import java.util.List;

/** Conversation entries exchanged with the model. */
public sealed interface Message {
    record UserText(String text) implements Message {}

    record AssistantToolUse(List<ModelTurn.ToolCall> calls) implements Message {}

    record ToolResults(List<Result> results) implements Message {
        public record Result(String toolUseId, ToolResult result) {}
    }
}
