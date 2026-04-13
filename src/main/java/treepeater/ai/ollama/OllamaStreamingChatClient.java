package treepeater.ai.ollama;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.ollama4j.Ollama;
import io.github.ollama4j.models.chat.OllamaChatMessage;
import io.github.ollama4j.models.chat.OllamaChatMessageRole;
import io.github.ollama4j.models.chat.OllamaChatRequest;
import io.github.ollama4j.models.chat.OllamaChatResult;
import io.github.ollama4j.models.chat.OllamaChatTokenHandler;
import io.github.ollama4j.models.chat.OllamaChatToolCalls;
import io.github.ollama4j.tools.OllamaToolCallsFunction;
import io.github.ollama4j.tools.ToolFunction;
import io.github.ollama4j.tools.Tools;
import io.github.ollama4j.utils.Utils;

import treepeater.ai.ChatMessage;
import treepeater.ai.ChatRole;
import treepeater.ai.ChatStreamMessage;
import treepeater.ai.ChatToolCall;
import treepeater.ai.ChatToolDefinition;
import treepeater.ai.ChatToolExecutor;
import treepeater.ai.ChatTooling;
import treepeater.ai.HttpTargetTools;
import treepeater.ai.StreamingChatClient;

/**
 * {@link StreamingChatClient} backed by a local or remote Ollama server (ollama4j).
 */
public class OllamaStreamingChatClient implements StreamingChatClient {
    static {
        // Ollama ≥0.12.10 includes tool_calls[].id; older ollama4j could not deserialize it (ollama4j#237).
        // 1.1.4+ maps id; this keeps parsing tolerant if the API adds more fields.
        Utils.getObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private final OllamaClientConfig config;
    private final Ollama api;

    public OllamaStreamingChatClient(OllamaClientConfig config) {
        this.config = config;
        this.api = new Ollama(config.baseUrl());
        this.api.setMaxChatToolCallRetries(16);
    }

    @Override
    public List<ChatMessage> streamChat(
            List<ChatMessage> messages, ChatTooling tooling, Consumer<ChatStreamMessage> onMessage) throws Exception {
        if (tooling == null || !tooling.isActive()) {
            return streamPlain(messages, onMessage, false);
        }
        try {
            for (ChatToolDefinition def : tooling.tools()) {
                this.api.registerTool(toOllamaTool(def, tooling.executor(), onMessage));
            }
            return streamPlain(messages, onMessage, true);
        } finally {
            this.api.deregisterTools();
        }
    }

    private List<ChatMessage> streamPlain(
            List<ChatMessage> messages, Consumer<ChatStreamMessage> onMessage, boolean useTools) throws Exception {
        List<OllamaChatMessage> ollamaMessages = new ArrayList<>(messages.size());
        for (ChatMessage m : messages) {
            ollamaMessages.add(toOllama(m));
        }

        OllamaChatRequest partial =
                OllamaChatRequest.builder()
                        .withModel(this.config.model())
                        .withMessages(ollamaMessages)
                        .withStreaming();
        if (useTools) {
            partial = partial.withUseTools(true);
        }
        OllamaChatRequest request = partial.build();

        OllamaChatTokenHandler handler =
                chunk -> {
                    OllamaChatMessage msg = chunk.getMessage();
                    if (msg == null) {
                        return;
                    }
                    String delta = msg.getResponse();
                    if (delta != null && !delta.isEmpty()) {
                        onMessage.accept(new ChatStreamMessage.AssistantDelta(delta));
                    }
                };

        OllamaChatResult result = this.api.chat(request, handler);
        List<OllamaChatMessage> history = result.getChatHistory();
        if (history == null) {
            return List.of();
        }
        List<ChatMessage> out = new ArrayList<>(history.size());
        for (OllamaChatMessage om : history) {
            out.add(fromOllama(om));
        }
        return withToolCallIdsFromAssistant(out);
    }

    /**
     * {@link OllamaChatMessage} has no per-tool id on tool rows; copy from the preceding assistant's
     * {@code tool_calls} list.
     */
    private static List<ChatMessage> withToolCallIdsFromAssistant(List<ChatMessage> messages) {
        List<ChatMessage> linked = new ArrayList<>(messages.size());
        ChatMessage assistantWithTools = null;
        int slot = 0;
        for (ChatMessage m : messages) {
            if (m.role() == ChatRole.ASSISTANT) {
                if (m.hasAssistantToolCalls()) {
                    assistantWithTools = m;
                    slot = 0;
                } else {
                    assistantWithTools = null;
                }
                linked.add(m);
            } else if (m.role() == ChatRole.TOOL) {
                String id = m.toolCallId();
                if ((id == null || id.isBlank())
                        && assistantWithTools != null
                        && slot < assistantWithTools.assistantToolCalls().size()) {
                    id = assistantWithTools.assistantToolCalls().get(slot).id();
                }
                slot++;
                linked.add(new ChatMessage(ChatRole.TOOL, m.content(), List.of(), id));
            } else {
                if (m.role() == ChatRole.USER && !m.content().trim().isEmpty()) {
                    assistantWithTools = null;
                }
                linked.add(m);
            }
        }
        return linked;
    }

    private static Tools.Tool toOllamaTool(
            ChatToolDefinition def, ChatToolExecutor exec, Consumer<ChatStreamMessage> onMessage) {
        Tools.Parameters parameters = new Tools.Parameters();
        parameters.setProperties(new HashMap<>());

        Tools.ToolSpec spec =
                Tools.ToolSpec.builder()
                        .name(def.name())
                        .description(def.description())
                        .parameters(parameters)
                        .build();

        ToolFunction fn =
                args -> {
                    try {
                        String argsJson = JSON.writeValueAsString(args);
                        onMessage.accept(
                                new ChatStreamMessage.ToolUsage(
                                        def.name(),
                                        argsJson,
                                        HttpTargetTools.humanReadableUsage(def.name(), argsJson)));
                        return exec.invoke(def.name(), argsJson);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                };

        return Tools.Tool.builder().toolSpec(spec).type("function").toolFunction(fn).build();
    }

    private static OllamaChatMessage toOllama(ChatMessage m) {
        OllamaChatMessageRole role =
                switch (m.role()) {
                    case SYSTEM -> OllamaChatMessageRole.SYSTEM;
                    case USER -> OllamaChatMessageRole.USER;
                    case ASSISTANT -> OllamaChatMessageRole.ASSISTANT;
                    case TOOL -> OllamaChatMessageRole.TOOL;
                };
        OllamaChatMessage om = new OllamaChatMessage(role, m.content());
        if (m.role() == ChatRole.ASSISTANT && m.hasAssistantToolCalls()) {
            List<OllamaChatToolCalls> calls = new ArrayList<>();
            for (ChatToolCall tc : m.assistantToolCalls()) {
                OllamaChatToolCalls occ = new OllamaChatToolCalls();
                if (tc.id() != null && !tc.id().isEmpty()) {
                    occ.setId(tc.id());
                }
                OllamaToolCallsFunction fn = new OllamaToolCallsFunction();
                fn.setName(tc.name());
                fn.setArguments(parseArgs(tc.argumentsJson()));
                occ.setFunction(fn);
                calls.add(occ);
            }
            om.setToolCalls(calls);
        }
        return om;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseArgs(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return JSON.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static ChatMessage fromOllama(OllamaChatMessage m) {
        ChatRole role = mapOllamaRole(m.getRole());
        String c = m.getResponse() != null ? m.getResponse() : "";
        boolean toolCallsPresent = m.getToolCalls() != null && !m.getToolCalls().isEmpty();
        /*
         * Jackson may deserialize role as a *new* OllamaChatMessageRole("assistant") instance, so
         * r == OllamaChatMessageRole.ASSISTANT is false and we must not rely on reference equality.
         * Non-empty tool_calls only appear on assistant turns per Ollama chat API.
         */
        if (toolCallsPresent) {
            List<ChatToolCall> calls = new ArrayList<>();
            int i = 0;
            for (OllamaChatToolCalls occ : m.getToolCalls()) {
                if (occ.getFunction() == null) {
                    continue;
                }
                String name = occ.getFunction().getName();
                String argsJson;
                try {
                    argsJson = JSON.writeValueAsString(occ.getFunction().getArguments());
                } catch (Exception e) {
                    argsJson = "{}";
                }
                String id = occ.getId();
                if (id == null || id.isBlank()) {
                    id = "ollama-" + (i++);
                } else {
                    i++;
                }
                calls.add(new ChatToolCall(id, name != null ? name : "", argsJson));
            }
            return new ChatMessage(ChatRole.ASSISTANT, c, calls, null);
        }

        return new ChatMessage(role, c);
    }

    /** Map by {@link OllamaChatMessageRole#getRoleName()} — deserialized roles are not always == to static constants. */
    private static ChatRole mapOllamaRole(OllamaChatMessageRole r) {
        if (r == null) {
            return ChatRole.USER;
        }
        String n = r.getRoleName();
        if (n == null || n.isEmpty()) {
            return ChatRole.USER;
        }
        return switch (n.toLowerCase()) {
            case "system" -> ChatRole.SYSTEM;
            case "user" -> ChatRole.USER;
            case "assistant" -> ChatRole.ASSISTANT;
            case "tool" -> ChatRole.TOOL;
            default -> ChatRole.USER;
        };
    }
}
