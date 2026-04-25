package treepeater.ai.anthropic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageDeltaUsage;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawContentBlockDelta;
import com.anthropic.models.messages.RawContentBlockDeltaEvent;
import com.anthropic.models.messages.RawContentBlockStartEvent;
import com.anthropic.models.messages.RawMessageDeltaEvent;
import com.anthropic.models.messages.RawMessageStartEvent;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlockParam;
import com.anthropic.models.messages.Usage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import treepeater.ai.ChatMessage;
import treepeater.ai.ChatRole;
import treepeater.ai.ChatStreamMessage;
import treepeater.ai.ChatStreamSession;
import treepeater.ai.ChatToolCall;
import treepeater.ai.ChatToolDefinition;
import treepeater.ai.ChatStreamLogging;
import treepeater.ai.ChatTooling;
import treepeater.ai.ParallelToolExecution;
import treepeater.ai.StreamingChatClient;

/**
 * {@link StreamingChatClient} backed by Anthropic's Messages API (streaming).
 */
public class AnthropicStreamingChatClient implements StreamingChatClient {
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Must stay in sync with {@link #baseBuilder}'s {@code maxTokens}. */
    private static final long ANTHROPIC_MAX_OUTPUT_TOKENS = 4096L;

    private final AnthropicClientConfig config;

    public AnthropicStreamingChatClient(AnthropicClientConfig config) {
        this.config = config;
    }

    @Override
    public List<ChatMessage> streamChat(
            List<ChatMessage> messages, ChatTooling tooling, ChatStreamSession session) throws Exception {
        AnthropicClient client = AnthropicOkHttpClient.builder().apiKey(this.config.apiKey()).build();
        try {
            if (tooling == null || !tooling.isActive()) {
                return streamOncePlain(client, messages, session);
            }
            List<ChatMessage> work = new ArrayList<>(messages);
            for (int round = 0; round < StreamingChatClient.MAX_AGENT_TOOL_ROUNDS; round++) {
                if (session.isClosed() || Thread.currentThread().isInterrupted()) {
                    return work;
                }
                RoundResult rr = streamOneAssistantTurn(client, work, session, tooling, round);
                work.add(rr.assistant());
                if (session.isClosed() || Thread.currentThread().isInterrupted()) {
                    return work;
                }
                if (!rr.hadToolCalls()) {
                    return work;
                }
                List<ChatToolCall> calls = rr.assistant().assistantToolCalls();
                List<String> results =
                        ParallelToolExecution.executeRound(calls, tooling, session);
                for (int i = 0; i < calls.size(); i++) {
                    if (session.isClosed() || Thread.currentThread().isInterrupted()) {
                        return work;
                    }
                    work.add(new ChatMessage(ChatRole.TOOL, results.get(i), List.of(), calls.get(i).id()));
                }
            }
            return work;
        } finally {
            client.close();
        }
    }

    private List<ChatMessage> streamOncePlain(
            AnthropicClient client, List<ChatMessage> messages, ChatStreamSession session) throws Exception {
        ChatStreamLogging.logApiRequest("Anthropic", this.config.model(), 0, messages.size(), false);
        long tWallStart = System.nanoTime();
        MessageCreateParams.Builder paramsBuilder = baseBuilder(messages, ChatTooling.none());
        MessageCreateParams params = paramsBuilder.build();
        long tAfterBuild = System.nanoTime();
        StringBuilder assistantAccum = new StringBuilder();
        runTextOnlyStream(client, params, assistantAccum, session, 0, tWallStart, tAfterBuild);
        List<ChatMessage> history = new ArrayList<>(messages.size() + 1);
        history.addAll(messages);
        history.add(new ChatMessage(ChatRole.ASSISTANT, assistantAccum.toString()));
        return history;
    }

    private RoundResult streamOneAssistantTurn(
            AnthropicClient client,
            List<ChatMessage> messages,
            ChatStreamSession session,
            ChatTooling tooling,
            int round)
            throws Exception {
        ChatStreamLogging.logApiRequest("Anthropic", this.config.model(), round, messages.size(), true);
        long tWallStart = System.nanoTime();
        MessageCreateParams.Builder paramsBuilder = baseBuilder(messages, tooling);
        MessageCreateParams params = paramsBuilder.build();
        long tAfterBuild = System.nanoTime();

        StringBuilder textOut = new StringBuilder();
        List<ChatToolCall> toolCalls = new ArrayList<>();
        ToolStreamState st = new ToolStreamState();
        UsageAccumulator usage = new UsageAccumulator();

        try (StreamResponse<RawMessageStreamEvent> stream = client.messages().createStreaming(params)) {
            long tAfterOpen = System.nanoTime();
            long buildMs = (tAfterBuild - tWallStart) / 1_000_000L;
            long httpOpenMs = (tAfterOpen - tAfterBuild) / 1_000_000L;
            Iterator<RawMessageStreamEvent> it = stream.stream().iterator();
            long t0 = tAfterOpen;
            long ttfbMs = -1;
            int eventCount = 0;
            while (it.hasNext()
                    && !session.isClosed()
                    && !Thread.currentThread().isInterrupted()) {
                RawMessageStreamEvent event = it.next();
                eventCount++;
                if (ttfbMs < 0) {
                    ttfbMs = (System.nanoTime() - t0) / 1_000_000L;
                }
                if (event.isMessageStart()) {
                    absorbMessageStart(event.asMessageStart(), usage);
                } else if (event.isMessageDelta()) {
                    absorbMessageDelta(event.asMessageDelta(), usage);
                } else if (event.isContentBlockStart()) {
                    RawContentBlockStartEvent start = event.asContentBlockStart();
                    RawContentBlockStartEvent.ContentBlock block = start.contentBlock();
                    if (block.isText()) {
                        st.phase = StreamPhase.TEXT;
                    } else if (block.isThinking()) {
                        st.phase = StreamPhase.THINKING;
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
                            session.emit(new ChatStreamMessage.AssistantDelta(piece));
                        }
                    } else if (st.phase == StreamPhase.THINKING && delta.isThinking()) {
                        String piece = delta.asThinking().thinking();
                        if (piece != null && !piece.isEmpty()) {
                            session.emit(new ChatStreamMessage.ThinkingDelta(piece));
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
            }
            long streamMs = (System.nanoTime() - t0) / 1_000_000L;
            long wallMs = (System.nanoTime() - tWallStart) / 1_000_000L;
            if (ttfbMs < 0) {
                ttfbMs = 0;
            }
            ChatStreamLogging.logApiStreamComplete(
                    "Anthropic", round, wallMs, buildMs, httpOpenMs, ttfbMs, streamMs, eventCount);
            ChatStreamLogging.logApiUsage(
                    "Anthropic",
                    round,
                    usage.inputTokens,
                    usage.cacheReadTokens,
                    usage.cacheCreationTokens,
                    usage.outputTokens);
        }

        ChatMessage assistant =
                new ChatMessage(ChatRole.ASSISTANT, textOut.toString(), toolCalls, null);
        return new RoundResult(assistant, !toolCalls.isEmpty());
    }

    private void runTextOnlyStream(
            AnthropicClient client,
            MessageCreateParams params,
            StringBuilder assistantAccum,
            ChatStreamSession session,
            int round,
            long tWallStart,
            long tAfterBuild)
            throws Exception {
        UsageAccumulator usage = new UsageAccumulator();
        try (StreamResponse<RawMessageStreamEvent> stream = client.messages().createStreaming(params)) {
            long tAfterOpen = System.nanoTime();
            long buildMs = (tAfterBuild - tWallStart) / 1_000_000L;
            long httpOpenMs = (tAfterOpen - tAfterBuild) / 1_000_000L;
            Iterator<RawMessageStreamEvent> it = stream.stream().iterator();
            long t0 = tAfterOpen;
            long ttfbMs = -1;
            int eventCount = 0;
            StreamPhase plainPhase = StreamPhase.SKIP;
            while (it.hasNext()
                    && !session.isClosed()
                    && !Thread.currentThread().isInterrupted()) {
                RawMessageStreamEvent event = it.next();
                eventCount++;
                if (ttfbMs < 0) {
                    ttfbMs = (System.nanoTime() - t0) / 1_000_000L;
                }
                if (event.isMessageStart()) {
                    absorbMessageStart(event.asMessageStart(), usage);
                    continue;
                }
                if (event.isMessageDelta()) {
                    absorbMessageDelta(event.asMessageDelta(), usage);
                    continue;
                }
                if (event.isContentBlockStart()) {
                    RawContentBlockStartEvent.ContentBlock block = event.asContentBlockStart().contentBlock();
                    if (block.isText()) {
                        plainPhase = StreamPhase.TEXT;
                    } else if (block.isThinking()) {
                        plainPhase = StreamPhase.THINKING;
                    } else {
                        plainPhase = StreamPhase.SKIP;
                    }
                    continue;
                }
                if (event.isContentBlockStop()) {
                    plainPhase = StreamPhase.SKIP;
                    continue;
                }
                if (!event.isContentBlockDelta()) {
                    continue;
                }
                RawContentBlockDeltaEvent blockDelta = event.asContentBlockDelta();
                RawContentBlockDelta delta = blockDelta.delta();
                if (plainPhase == StreamPhase.TEXT && delta.isText()) {
                    String piece = delta.asText().text();
                    if (piece != null && !piece.isEmpty()) {
                        assistantAccum.append(piece);
                        session.emit(new ChatStreamMessage.AssistantDelta(piece));
                    }
                } else if (plainPhase == StreamPhase.THINKING && delta.isThinking()) {
                    String piece = delta.asThinking().thinking();
                    if (piece != null && !piece.isEmpty()) {
                        session.emit(new ChatStreamMessage.ThinkingDelta(piece));
                    }
                }
            }
            long streamMs = (System.nanoTime() - t0) / 1_000_000L;
            long wallMs = (System.nanoTime() - tWallStart) / 1_000_000L;
            if (ttfbMs < 0) {
                ttfbMs = 0;
            }
            ChatStreamLogging.logApiStreamComplete(
                    "Anthropic", round, wallMs, buildMs, httpOpenMs, ttfbMs, streamMs, eventCount);
            ChatStreamLogging.logApiUsage(
                    "Anthropic",
                    round,
                    usage.inputTokens,
                    usage.cacheReadTokens,
                    usage.cacheCreationTokens,
                    usage.outputTokens);
        }
    }

    private static void absorbMessageStart(RawMessageStartEvent event, UsageAccumulator usage) {
        try {
            Usage u = event.message().usage();
            usage.inputTokens = u.inputTokens();
            usage.outputTokens = Math.max(usage.outputTokens, u.outputTokens());
            u.cacheReadInputTokens().ifPresent(v -> usage.cacheReadTokens = v);
            u.cacheCreationInputTokens().ifPresent(v -> usage.cacheCreationTokens = v);
        } catch (Exception ignored) {
        }
    }

    private static void absorbMessageDelta(RawMessageDeltaEvent event, UsageAccumulator usage) {
        try {
            MessageDeltaUsage u = event.usage();
            u.inputTokens().ifPresent(v -> usage.inputTokens = v);
            usage.outputTokens = u.outputTokens();
            u.cacheReadInputTokens().ifPresent(v -> usage.cacheReadTokens = v);
            u.cacheCreationInputTokens().ifPresent(v -> usage.cacheCreationTokens = v);
        } catch (Exception ignored) {
        }
    }

    private MessageCreateParams.Builder baseBuilder(List<ChatMessage> messages, ChatTooling tooling) throws Exception {
        MessageCreateParams.Builder paramsBuilder =
                MessageCreateParams.builder()
                        .model(Model.of(this.config.model()))
                        .maxTokens(ANTHROPIC_MAX_OUTPUT_TOKENS)
                        .outputConfig(
                                OutputConfig.builder()
                                        .effort(this.config.outputEffort().toSdk())
                                        .build());
        applyExtendedThinkingConfig(paramsBuilder);

        if (tooling != null && tooling.isActive()) {
            List<ChatToolDefinition> defs = tooling.tools();
            for (int i = 0; i < defs.size(); i++) {
                boolean isLast = i == defs.size() - 1;
                paramsBuilder.addTool(anthropicTool(defs.get(i), isLast));
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
            // System as a single ephemeral-cached text block. Caches the stable
            // tools → system prefix so tool-loop rounds only re-bill growing messages.
            paramsBuilder.systemOfTextBlockParams(
                    List.of(
                            TextBlockParam.builder()
                                    .text(systemAccum.toString())
                                    .cacheControl(CacheControlEphemeral.builder().build())
                                    .build()));
        }

        appendConversation(paramsBuilder, messages);
        return paramsBuilder;
    }

    /**
     * Sonnet / Opus 4.6+ use {@link ThinkingConfigAdaptive}. Earlier 4.x thinking models use {@code enabled_thinking}
     * with {@code budget_tokens &lt; max_tokens} (Anthropic API constraint). Skipped when
     * {@link AnthropicClientConfig#extendedThinking()} is false.
     */
    private void applyExtendedThinkingConfig(MessageCreateParams.Builder b) {
        if (!this.config.extendedThinking()) {
            return;
        }
        String modelId = this.config.model();
        String m = modelId == null ? "" : modelId.toLowerCase(Locale.ROOT);
        if (m.isEmpty()) {
            return;
        }
        if (AnthropicModelSupport.isClaude46FamilyOrLater(m)) {
            b.thinking(ThinkingConfigAdaptive.builder().build());
            return;
        }
        if (AnthropicModelSupport.likelySupportsFixedBudgetExtendedThinking(m)) {
            long budget = Math.min(2048L, ANTHROPIC_MAX_OUTPUT_TOKENS - 1);
            b.enabledThinking(budget);
        }
    }

    /**
     * Builds an Anthropic {@link Tool}. The {@code isLast} flag attaches a {@code cache_control: ephemeral}
     * breakpoint to the final tool so the entire tool-definitions prefix is cached and reused across rounds.
     */
    static Tool anthropicTool(ChatToolDefinition def, boolean isLast) throws Exception {
        Tool.Builder b =
                Tool.builder()
                        .name(def.name())
                        .description(def.description())
                        .inputSchema(buildInputSchema(def.parametersJsonSchema()));
        if (isLast) {
            b.cacheControl(CacheControlEphemeral.builder().build());
        }
        return b.build();
    }

    static Tool.InputSchema buildInputSchema(String jsonSchema) throws Exception {
        Tool.InputSchema.Builder sb = Tool.InputSchema.builder().type(JsonValue.from("object"));
        if (jsonSchema == null || jsonSchema.isBlank()) {
            return sb.build();
        }
        final JsonNode root;
        try {
            root = JSON.readTree(jsonSchema);
        } catch (JsonProcessingException e) {
            return sb.build();
        }
        if (!root.isObject()) {
            return sb.build();
        }
        JsonNode propsNode = root.get("properties");
        if (propsNode != null && propsNode.isObject()) {
            Tool.InputSchema.Properties.Builder pb = Tool.InputSchema.Properties.builder();
            propsNode.properties()
                    .forEach(e -> pb.putAdditionalProperty(e.getKey(), JsonValue.fromJsonNode(e.getValue())));
            sb.properties(pb.build());
        }
        JsonNode requiredNode = root.get("required");
        if (requiredNode != null && requiredNode.isArray()) {
            List<String> required = new ArrayList<>();
            requiredNode.forEach(n -> required.add(n.asText()));
            if (!required.isEmpty()) {
                sb.required(required);
            }
        }
        JsonNode addlNode = root.get("additionalProperties");
        if (addlNode != null && (addlNode.isBoolean() || addlNode.isObject())) {
            sb.putAdditionalProperty("additionalProperties", JsonValue.fromJsonNode(addlNode));
        }
        return sb.build();
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

    private static ToolUseBlockParam.Input toolInputFromJsonString(String json) {
        if (json == null || json.isBlank()) {
            return ToolUseBlockParam.Input.builder().build();
        }
        final JsonNode n;
        try {
            n = JSON.readTree(json);
        } catch (JsonProcessingException e) {
            // Tool arguments are streamed; truncation (e.g. max output tokens) can leave invalid JSON
            // in history even after a tool round. Replay must not fail the next API request.
            return ToolUseBlockParam.Input.builder().build();
        }
        ToolUseBlockParam.Input.Builder ib = ToolUseBlockParam.Input.builder();
        if (n.isObject()) {
            n.properties()
                    .forEach(e -> ib.putAdditionalProperty(e.getKey(), JsonValue.from(e.getValue())));
        }
        return ib.build();
    }

    private enum StreamPhase {
        TEXT,
        THINKING,
        TOOL_INPUT,
        SKIP
    }

    private static final class ToolStreamState {
        StreamPhase phase = StreamPhase.SKIP;
        final StringBuilder toolJsonIn = new StringBuilder();
        String curToolId = "";
        String curToolName = "";
    }

    private static final class UsageAccumulator {
        long inputTokens;
        long outputTokens;
        long cacheReadTokens;
        long cacheCreationTokens;
    }

    private record RoundResult(ChatMessage assistant, boolean hadToolCalls) {}
}
