package treepeater.ai.openai;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.azure.AzureUrlPathMode;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.core.http.StreamResponse;
import com.openai.models.ChatModel;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionStreamOptions;
import com.openai.models.chat.completions.ChatCompletionToolChoiceOption;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import treepeater.ai.ChatMessage;
import treepeater.ai.ChatRole;
import treepeater.ai.ChatStreamMessage;
import treepeater.ai.ChatStreamSession;
import treepeater.ai.ChatToolCall;
import treepeater.ai.ChatToolDefinition;
import treepeater.ai.ChatTooling;
import treepeater.ai.ParallelToolExecution;
import treepeater.ai.StreamingChatClient;

/**
 * {@link StreamingChatClient} using the official OpenAI Java SDK, configured for Azure OpenAI / Foundry
 * (API key + resource endpoint + deployment name).
 */
public class OpenAiStreamingChatClient implements StreamingChatClient {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final OpenAiClientConfig config;

    public OpenAiStreamingChatClient(OpenAiClientConfig config) {
        this.config = config;
    }

    @Override
    public List<ChatMessage> streamChat(
            List<ChatMessage> messages, ChatTooling tooling, ChatStreamSession session) throws Exception {
        OpenAIClient client = newClient();
        String cacheKey = "treepeater-" + System.identityHashCode(session);
        try {
            if (tooling == null || !tooling.isActive()) {
                return streamOncePlain(client, messages, session, cacheKey);
            }
            List<ChatMessage> work = new ArrayList<>(messages);
            for (int round = 0; round < StreamingChatClient.MAX_AGENT_TOOL_ROUNDS; round++) {
                if (session.isClosed() || Thread.currentThread().isInterrupted()) {
                    return work;
                }
                RoundResult rr = streamOneAssistantTurn(client, work, session, tooling, round, cacheKey);
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
            OpenAIClient client, List<ChatMessage> messages, ChatStreamSession session, String cacheKey)
            throws Exception {
        ChatCompletionCreateParams params = buildParams(messages, ChatTooling.none(), false, cacheKey);
        StringBuilder assistantAccum = new StringBuilder();
        runTextStream(client, params, assistantAccum, session);
        List<ChatMessage> history = new ArrayList<>(messages.size() + 1);
        history.addAll(messages);
        history.add(new ChatMessage(ChatRole.ASSISTANT, assistantAccum.toString()));
        return history;
    }

    private RoundResult streamOneAssistantTurn(
            OpenAIClient client,
            List<ChatMessage> messages,
            ChatStreamSession session,
            ChatTooling tooling,
            int round,
            String cacheKey)
            throws Exception {
        ChatCompletionCreateParams params = buildParams(messages, tooling, true, cacheKey);
        StringBuilder textOut = new StringBuilder();
        Map<Long, ToolStreamAccumulator> toolAcc = new TreeMap<>();

        try (StreamResponse<ChatCompletionChunk> stream = client.chat().completions().createStreaming(params)) {
            Iterator<ChatCompletionChunk> it = stream.stream().iterator();
            while (it.hasNext()
                    && !session.isClosed()
                    && !Thread.currentThread().isInterrupted()) {
                ChatCompletionChunk chunk = it.next();
                List<ChatCompletionChunk.Choice> choices = chunk.choices();
                if (choices == null || choices.isEmpty()) {
                    continue;
                }
                ChatCompletionChunk.Choice choice = choices.getFirst();
                var delta = choice.delta();
                delta.content()
                        .ifPresent(
                                piece -> {
                                    if (!piece.isEmpty()) {
                                        textOut.append(piece);
                                        session.emit(new ChatStreamMessage.AssistantDelta(piece));
                                    }
                                });
                delta.toolCalls()
                        .ifPresent(
                                tcs -> {
                                    for (ChatCompletionChunk.Choice.Delta.ToolCall tc : tcs) {
                                        long idx = tc.index();
                                        ToolStreamAccumulator acc =
                                                toolAcc.computeIfAbsent(idx, k -> new ToolStreamAccumulator());
                                        tc.id().ifPresent(id -> acc.id = id);
                                        tc.function()
                                                .ifPresent(
                                                        fn -> {
                                                            fn.name().ifPresent(n -> acc.name = n);
                                                            fn.arguments().ifPresent(a -> acc.args.append(a));
                                                        });
                                    }
                                });
            }
        }

        List<ChatToolCall> toolCalls = new ArrayList<>();
        for (Map.Entry<Long, ToolStreamAccumulator> e : toolAcc.entrySet()) {
            ToolStreamAccumulator acc = e.getValue();
            String id =
                    acc.id != null && !acc.id.isBlank()
                            ? acc.id
                            : ("openai-tool-" + e.getKey());
            String name = acc.name != null ? acc.name : "";
            String args = acc.args.toString();
            toolCalls.add(new ChatToolCall(id, name, args));
        }

        ChatMessage assistant =
                new ChatMessage(ChatRole.ASSISTANT, textOut.toString(), toolCalls, null);
        return new RoundResult(assistant, !toolCalls.isEmpty());
    }

    private void runTextStream(
            OpenAIClient client,
            ChatCompletionCreateParams params,
            StringBuilder assistantAccum,
            ChatStreamSession session)
            throws Exception {
        try (StreamResponse<ChatCompletionChunk> stream = client.chat().completions().createStreaming(params)) {
            Iterator<ChatCompletionChunk> it = stream.stream().iterator();
            while (it.hasNext()
                    && !session.isClosed()
                    && !Thread.currentThread().isInterrupted()) {
                ChatCompletionChunk chunk = it.next();
                List<ChatCompletionChunk.Choice> choices = chunk.choices();
                if (choices == null || choices.isEmpty()) {
                    continue;
                }
                choices.getFirst()
                        .delta()
                        .content()
                        .ifPresent(
                                piece -> {
                                    if (!piece.isEmpty()) {
                                        assistantAccum.append(piece);
                                        session.emit(new ChatStreamMessage.AssistantDelta(piece));
                                    }
                                });
            }
        }
    }

    private ChatCompletionCreateParams buildParams(
            List<ChatMessage> messages, ChatTooling tooling, boolean includeTools, String cacheKey)
            throws JsonProcessingException {
        // Order matters for auto prompt caching: emit tools → system → messages. The Azure/OpenAI
        // prefix cache keys on a stable prefix; mutating system or tools mid-session invalidates
        // subsequent hits. promptCacheKey keeps a session's prefix colocated on the same backend.
        ChatCompletionCreateParams.Builder b =
                ChatCompletionCreateParams.builder()
                        .model(ChatModel.of(this.config.deploymentName()))
                        .maxCompletionTokens(4096L)
                        .reasoningEffort(this.config.reasoningEffort().toSdk())
                        .streamOptions(
                                ChatCompletionStreamOptions.builder().includeUsage(true).build());

        if (cacheKey != null && !cacheKey.isBlank()) {
            b.promptCacheKey(cacheKey);
        }

        if (includeTools && tooling != null && tooling.isActive()) {
            b.toolChoice(ChatCompletionToolChoiceOption.Auto.AUTO);
            for (ChatToolDefinition def : tooling.tools()) {
                b.addFunctionTool(
                        FunctionDefinition.builder()
                                .name(def.name())
                                .description(def.description())
                                .parameters(schemaToParameters(def.parametersJsonSchema()))
                                .build());
            }
        }

        appendConversation(b, messages);
        return b.build();
    }

    private static FunctionParameters schemaToParameters(String jsonSchema) throws JsonProcessingException {
        final JsonNode n;
        try {
            n = JSON.readTree(jsonSchema);
        } catch (JsonProcessingException e) {
            return FunctionParameters.builder().build();
        }
        if (!n.isObject()) {
            return FunctionParameters.builder().build();
        }
        FunctionParameters.Builder fb = FunctionParameters.builder();
        for (var e : n.properties()) {
            fb.putAdditionalProperty(e.getKey(), JsonValue.fromJsonNode(e.getValue()));
        }
        return fb.build();
    }

    private void appendConversation(ChatCompletionCreateParams.Builder b, List<ChatMessage> messages)
            throws JsonProcessingException {
        for (ChatMessage m : messages) {
            switch (m.role()) {
                case SYSTEM -> b.addSystemMessage(m.content());
                case USER -> b.addUserMessage(m.content());
                case ASSISTANT -> {
                    if (m.hasAssistantToolCalls()) {
                        ChatCompletionAssistantMessageParam.Builder ab =
                                ChatCompletionAssistantMessageParam.builder();
                        if (!m.content().isBlank()) {
                            ab.content(m.content());
                        }
                        for (ChatToolCall tc : m.assistantToolCalls()) {
                            String args = tc.argumentsJson() != null ? tc.argumentsJson() : "{}";
                            ab.addToolCall(
                                    ChatCompletionMessageToolCall.ofFunction(
                                            ChatCompletionMessageFunctionToolCall.builder()
                                                    .id(tc.id() != null ? tc.id() : "")
                                                    .type(JsonValue.from("function"))
                                                    .function(
                                                            ChatCompletionMessageFunctionToolCall.Function.builder()
                                                                    .name(tc.name() != null ? tc.name() : "")
                                                                    .arguments(args)
                                                                    .build())
                                                    .build()));
                        }
                        b.addMessage(ab.build());
                    } else {
                        b.addAssistantMessage(m.content());
                    }
                }
                case TOOL ->
                        b.addMessage(
                                ChatCompletionToolMessageParam.builder()
                                        .toolCallId(m.toolCallId() != null ? m.toolCallId() : "")
                                        .content(m.content())
                                        .build());
            }
        }
    }

    private OpenAIClient newClient() {
        String base = normalizeEndpoint(this.config.endpoint());
        return OpenAIOkHttpClient.builder()
                .apiKey(this.config.apiKey())
                .baseUrl(base)
                .azureServiceVersion(AzureOpenAIServiceVersion.latestStableVersion())
                .azureUrlPathMode(AzureUrlPathMode.AUTO)
                .build();
    }

    private static String normalizeEndpoint(String endpoint) {
        String s = endpoint.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static final class ToolStreamAccumulator {
        String id;
        String name = "";
        final StringBuilder args = new StringBuilder();
    }

    private record RoundResult(ChatMessage assistant, boolean hadToolCalls) {}
}
