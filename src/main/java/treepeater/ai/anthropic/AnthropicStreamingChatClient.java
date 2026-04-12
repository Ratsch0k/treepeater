package treepeater.ai.anthropic;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.RawContentBlockDelta;
import com.anthropic.models.messages.RawContentBlockDeltaEvent;
import com.anthropic.models.messages.RawContentBlockStartEvent;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlockParam;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import treepeater.ai.ChatMessage;
import treepeater.ai.ChatRole;
import treepeater.ai.ChatStreamMessage;
import treepeater.ai.ChatToolCall;
import treepeater.ai.ChatToolDefinition;
import treepeater.ai.ChatTooling;
import treepeater.ai.HttpTargetTools;
import treepeater.ai.StreamingChatClient;

/**
 * {@link StreamingChatClient} backed by Anthropic's Messages API (streaming).
 */
public class AnthropicStreamingChatClient implements StreamingChatClient {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AnthropicClientConfig config;

    public AnthropicStreamingChatClient(AnthropicClientConfig config) {
        this.config = config;
    }

    @Override
    public List<ChatMessage> streamChat(
            List<ChatMessage> messages, ChatTooling tooling, Consumer<ChatStreamMessage> onMessage) throws Exception {
        if (tooling == null || !tooling.isActive()) {
            return streamOncePlain(messages, onMessage);
        }
        List<ChatMessage> work = new ArrayList<>(messages);
        int maxRounds = 16;
        for (int round = 0; round < maxRounds; round++) {
            RoundResult rr = streamOneAssistantTurn(work, onMessage, tooling);
            work.add(rr.assistant());
            if (!rr.hadToolCalls()) {
                return work;
            }
            for (ChatToolCall tc : rr.assistant().assistantToolCalls()) {
                String argsJson = tc.argumentsJson() != null ? tc.argumentsJson() : "";
                onMessage.accept(
                        new ChatStreamMessage.ToolUsage(
                                tc.name() != null ? tc.name() : "",
                                argsJson,
                                HttpTargetTools.humanReadableUsage(tc.name(), argsJson)));
                String result = tooling.executor().invoke(tc.name(), argsJson);
                work.add(new ChatMessage(ChatRole.TOOL, result, List.of(), tc.id()));
            }
        }
        return work;
    }

    private List<ChatMessage> streamOncePlain(List<ChatMessage> messages, Consumer<ChatStreamMessage> onMessage)
            throws Exception {
        MessageCreateParams.Builder paramsBuilder = baseBuilder(messages, ChatTooling.none());
        MessageCreateParams params = paramsBuilder.build();
        StringBuilder assistantAccum = new StringBuilder();
        runTextOnlyStream(params, assistantAccum, onMessage);
        List<ChatMessage> history = new ArrayList<>(messages.size() + 1);
        history.addAll(messages);
        history.add(new ChatMessage(ChatRole.ASSISTANT, assistantAccum.toString()));
        return history;
    }

    private RoundResult streamOneAssistantTurn(
            List<ChatMessage> messages, Consumer<ChatStreamMessage> onMessage, ChatTooling tooling) throws Exception {
        MessageCreateParams.Builder paramsBuilder = baseBuilder(messages, tooling);
        MessageCreateParams params = paramsBuilder.build();

        StringBuilder textOut = new StringBuilder();
        List<ChatToolCall> toolCalls = new ArrayList<>();
        ToolStreamState st = new ToolStreamState();

        AnthropicClient client = AnthropicOkHttpClient.builder().apiKey(this.config.apiKey()).build();
        try {
            try (StreamResponse<RawMessageStreamEvent> stream = client.messages().createStreaming(params)) {
                stream.stream()
                        .forEach(
                                event -> {
                                    if (event.isContentBlockStart()) {
                                        RawContentBlockStartEvent start = event.asContentBlockStart();
                                        RawContentBlockStartEvent.ContentBlock block = start.contentBlock();
                                        if (block.isText()) {
                                            st.phase = StreamPhase.TEXT;
                                        } else if (block.isToolUse()) {
                                            st.phase = StreamPhase.TOOL_INPUT;
                                            st.toolJsonIn.setLength(0);
                                            st.curToolId = block.asToolUse().id();
                                            st.curToolName = block.asToolUse().name();
                                        } else {
                                            st.phase = StreamPhase.SKIP;
                                        }
                                    } else if (event.isContentBlockDelta()) {
                                        RawContentBlockDeltaEvent blockDelta = event.asContentBlockDelta();
                                        RawContentBlockDelta delta = blockDelta.delta();
                                        if (st.phase == StreamPhase.TEXT && delta.isText()) {
                                            String piece = delta.asText().text();
                                            if (piece != null && !piece.isEmpty()) {
                                                textOut.append(piece);
                                                onMessage.accept(new ChatStreamMessage.AssistantDelta(piece));
                                            }
                                        } else if (st.phase == StreamPhase.TOOL_INPUT && delta.isInputJson()) {
                                            String part = delta.asInputJson().partialJson();
                                            if (part != null && !part.isEmpty()) {
                                                st.toolJsonIn.append(part);
                                            }
                                        }
                                    } else if (event.isContentBlockStop()) {
                                        if (st.phase == StreamPhase.TOOL_INPUT) {
                                            toolCalls.add(
                                                    new ChatToolCall(
                                                            st.curToolId,
                                                            st.curToolName,
                                                            st.toolJsonIn.toString()));
                                        }
                                        st.phase = StreamPhase.SKIP;
                                    }
                                });
            }
        } finally {
            client.close();
        }

        ChatMessage assistant =
                new ChatMessage(ChatRole.ASSISTANT, textOut.toString(), toolCalls, null);
        return new RoundResult(assistant, !toolCalls.isEmpty());
    }

    private void runTextOnlyStream(
            MessageCreateParams params, StringBuilder assistantAccum, Consumer<ChatStreamMessage> onMessage)
            throws Exception {
        AnthropicClient client = AnthropicOkHttpClient.builder().apiKey(this.config.apiKey()).build();
        try {
            try (StreamResponse<RawMessageStreamEvent> stream = client.messages().createStreaming(params)) {
                stream.stream()
                        .forEach(
                                event -> {
                                    if (!event.isContentBlockDelta()) {
                                        return;
                                    }
                                    RawContentBlockDeltaEvent blockDelta = event.asContentBlockDelta();
                                    RawContentBlockDelta delta = blockDelta.delta();
                                    if (!delta.isText()) {
                                        return;
                                    }
                                    String piece = delta.asText().text();
                                    if (piece != null && !piece.isEmpty()) {
                                        assistantAccum.append(piece);
                                        onMessage.accept(new ChatStreamMessage.AssistantDelta(piece));
                                    }
                                });
            }
        } finally {
            client.close();
        }
    }

    private MessageCreateParams.Builder baseBuilder(List<ChatMessage> messages, ChatTooling tooling) throws Exception {
        MessageCreateParams.Builder paramsBuilder =
                MessageCreateParams.builder().model(Model.of(this.config.model())).maxTokens(4096);

        if (tooling != null && tooling.isActive()) {
            for (ChatToolDefinition def : tooling.tools()) {
                paramsBuilder.addTool(anthropicTool(def));
            }
        }

        StringBuilder systemAccum = new StringBuilder();
        for (ChatMessage m : messages) {
            if (m.role() == ChatRole.SYSTEM) {
                if (!systemAccum.isEmpty()) {
                    systemAccum.append('\n');
                }
                systemAccum.append(m.content());
            }
        }
        if (!systemAccum.isEmpty()) {
            paramsBuilder.system(systemAccum.toString());
        }

        appendConversation(paramsBuilder, messages);
        return paramsBuilder;
    }

    private static Tool anthropicTool(ChatToolDefinition def) {
        return Tool.builder()
                .name(def.name())
                .description(def.description())
                .inputSchema(
                        Tool.InputSchema.builder()
                                .type(JsonValue.from("object"))
                                .build())
                .build();
    }

    private static void appendConversation(MessageCreateParams.Builder b, List<ChatMessage> messages) throws Exception {
        int i = 0;
        while (i < messages.size()) {
            ChatMessage m = messages.get(i);
            if (m.role() == ChatRole.SYSTEM) {
                i++;
                continue;
            }
            if (m.role() == ChatRole.USER) {
                b.addUserMessage(m.content());
                i++;
            } else if (m.role() == ChatRole.ASSISTANT) {
                if (m.hasAssistantToolCalls()) {
                    List<ContentBlockParam> parts = new ArrayList<>();
                    if (!m.content().isBlank()) {
                        parts.add(
                                ContentBlockParam.ofText(
                                        TextBlockParam.builder().text(m.content()).build()));
                    }
                    for (ChatToolCall tc : m.assistantToolCalls()) {
                        parts.add(
                                ContentBlockParam.ofToolUse(
                                        ToolUseBlockParam.builder()
                                                .id(tc.id())
                                                .name(tc.name())
                                                .input(toolInputFromJsonString(tc.argumentsJson()))
                                                .build()));
                    }
                    b.addAssistantMessageOfBlockParams(parts);
                } else {
                    b.addAssistantMessage(m.content());
                }
                i++;
            } else if (m.role() == ChatRole.TOOL) {
                List<ContentBlockParam> toolResults = new ArrayList<>();
                while (i < messages.size() && messages.get(i).role() == ChatRole.TOOL) {
                    ChatMessage t = messages.get(i);
                    String id = t.toolCallId() != null ? t.toolCallId() : "";
                    toolResults.add(
                            ContentBlockParam.ofToolResult(
                                    ToolResultBlockParam.builder().toolUseId(id).content(t.content()).build()));
                    i++;
                }
                b.addUserMessageOfBlockParams(toolResults);
            } else {
                throw new IllegalStateException("Unexpected role: " + m.role());
            }
        }
    }

    private static ToolUseBlockParam.Input toolInputFromJsonString(String json) throws Exception {
        if (json == null || json.isBlank()) {
            return ToolUseBlockParam.Input.builder().build();
        }
        JsonNode n = JSON.readTree(json);
        ToolUseBlockParam.Input.Builder ib = ToolUseBlockParam.Input.builder();
        if (n.isObject()) {
            n.properties()
                    .forEach(e -> ib.putAdditionalProperty(e.getKey(), JsonValue.from(e.getValue())));
        }
        return ib.build();
    }

    private enum StreamPhase {
        TEXT,
        TOOL_INPUT,
        SKIP
    }

    private static final class ToolStreamState {
        StreamPhase phase = StreamPhase.SKIP;
        final StringBuilder toolJsonIn = new StringBuilder();
        String curToolId = "";
        String curToolName = "";
    }

    private record RoundResult(ChatMessage assistant, boolean hadToolCalls) {}
}
