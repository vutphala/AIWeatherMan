package com.example.weatherman.llm;

import com.example.weatherman.config.WeatherManProperties;
import com.example.weatherman.logging.Secrets;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultStatus;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

/** Model client backed by the Amazon Bedrock Converse API. */
@Component
@DependsOn("startupConfigCheck")
public class BedrockModelClient implements ModelClient {
    private static final Logger log = LoggerFactory.getLogger(BedrockModelClient.class);
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final BedrockRuntimeClient client;
    private final String modelId;
    private final Secrets secrets;

    public BedrockModelClient(WeatherManProperties props, Secrets secrets) {
        this.modelId = props.bedrock().modelId();
        this.secrets = secrets;
        AwsCredentialsProvider credentials = DefaultCredentialsProvider.create();
        try {
            var resolved = credentials.resolveCredentials();
            secrets.add(resolved.accessKeyId());
            secrets.add(resolved.secretAccessKey());
            if (resolved instanceof software.amazon.awssdk.auth.credentials.AwsSessionCredentials session) {
                secrets.add(session.sessionToken());
            }
        } catch (RuntimeException e) {
            log.warn("AWS credentials could not be resolved at startup; Bedrock calls will fail until they are available.");
        }
        this.client = BedrockRuntimeClient.builder()
                .region(Region.of(props.bedrock().region()))
                .credentialsProvider(credentials)
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(60))
                        .build())
                .build();
    }

    @Override
    public ModelTurn converse(String systemPrompt, List<Message> messages, List<ToolDefinition> tools) {
        ConverseRequest.Builder req = ConverseRequest.builder()
                .modelId(modelId)
                .system(SystemContentBlock.builder().text(systemPrompt).build())
                .messages(messages.stream().map(BedrockModelClient::toBedrock).toList());
        if (!tools.isEmpty()) {
            req.toolConfig(ToolConfiguration.builder().tools(tools.stream().map(BedrockModelClient::toBedrock).toList()).build());
        }
        ConverseResponse response;
        try {
            response = client.converse(req.build());
        } catch (RuntimeException e) {
            String reason = secrets.redact(e.getClass().getSimpleName() + ": " + e.getMessage());
            log.error("requestId={} dependency=Bedrock failure={}", MDC.get("requestId"), reason);
            throw new ModelUnavailableException("The model service is unavailable.", e);
        }

        List<ModelTurn.ToolCall> calls = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        for (ContentBlock block : response.output().message().content()) {
            ToolUseBlock use = block.toolUse();
            if (use != null) {
                calls.add(new ModelTurn.ToolCall(use.toolUseId(), use.name(), toJson(use.input())));
            } else if (block.text() != null) {
                text.append(block.text());
            }
        }
        return calls.isEmpty() ? new ModelTurn.Final(text.toString()) : new ModelTurn.ToolUse(calls);
    }

    private static software.amazon.awssdk.services.bedrockruntime.model.Message toBedrock(Message m) {
        var b = software.amazon.awssdk.services.bedrockruntime.model.Message.builder();
        if (m instanceof Message.UserText u) {
            return b.role(ConversationRole.USER).content(ContentBlock.fromText(u.text())).build();
        }
        if (m instanceof Message.AssistantToolUse a) {
            List<ContentBlock> blocks = a.calls().stream()
                    .map(c -> ContentBlock.fromToolUse(ToolUseBlock.builder()
                            .toolUseId(c.id()).name(c.name()).input(toDocument(c.args())).build()))
                    .toList();
            return b.role(ConversationRole.ASSISTANT).content(blocks).build();
        }
        Message.ToolResults r = (Message.ToolResults) m;
        List<ContentBlock> blocks = r.results().stream()
                .map(x -> ContentBlock.fromToolResult(ToolResultBlock.builder()
                        .toolUseId(x.toolUseId())
                        .content(ToolResultContentBlock.fromText(x.result().text()))
                        .status(x.result().isError() ? ToolResultStatus.ERROR : ToolResultStatus.SUCCESS)
                        .build()))
                .toList();
        return b.role(ConversationRole.USER).content(blocks).build();
    }

    private static Tool toBedrock(ToolDefinition t) {
        return Tool.fromToolSpec(ToolSpecification.builder()
                .name(t.name())
                .description(t.description())
                .inputSchema(ToolInputSchema.fromJson(toDocument(t.inputSchema())))
                .build());
    }

    static Document toDocument(JsonNode n) {
        if (n == null || n.isNull()) return Document.fromNull();
        if (n.isObject()) {
            Map<String, Document> map = new LinkedHashMap<>();
            n.fields().forEachRemaining(e -> map.put(e.getKey(), toDocument(e.getValue())));
            return Document.fromMap(map);
        }
        if (n.isArray()) {
            List<Document> list = new ArrayList<>();
            n.forEach(x -> list.add(toDocument(x)));
            return Document.fromList(list);
        }
        if (n.isBoolean()) return Document.fromBoolean(n.booleanValue());
        if (n.isIntegralNumber()) return Document.fromNumber(n.longValue());
        if (n.isNumber()) return Document.fromNumber(n.doubleValue());
        return Document.fromString(n.asText());
    }

    static JsonNode toJson(Document d) {
        if (d == null || d.isNull()) return JSON.nullNode();
        if (d.isMap()) {
            ObjectNode o = JSON.objectNode();
            d.asMap().forEach((k, v) -> o.set(k, toJson(v)));
            return o;
        }
        if (d.isList()) {
            var a = JSON.arrayNode();
            d.asList().forEach(v -> a.add(toJson(v)));
            return a;
        }
        if (d.isBoolean()) return JSON.booleanNode(d.asBoolean());
        if (d.isNumber()) return JSON.numberNode(d.asNumber().bigDecimalValue());
        return JSON.textNode(d.asString());
    }
}
