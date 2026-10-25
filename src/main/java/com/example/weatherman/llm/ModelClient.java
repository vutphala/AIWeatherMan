package com.example.weatherman.llm;

import java.util.List;

public interface ModelClient {
    /** @throws ModelUnavailableException on throttling, service errors or timeouts */
    ModelTurn converse(String systemPrompt, List<Message> messages, List<ToolDefinition> tools);
}
