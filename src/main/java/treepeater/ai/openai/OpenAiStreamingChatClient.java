package treepeater.ai.openai;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.azure.AzureUrlPathMode;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.core.http.StreamResponse;
import com.openai.models.Reasoning;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.FunctionTool;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseStreamEvent;
import com.openai.models.responses.ToolChoiceOptions;
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
 * {@link StreamingChatClient} backed by the OpenAI Responses API (via the official OpenAI Java SDK,
 * configured for Azure OpenAI / Foundry: API key + resource endpoint + deployment name).
 *
 * <p>The Responses API is used instead of Chat Completions because it is the only OpenAI surface
 * that streams reasoning content (both {@link com.openai.models.responses.ResponseReasoningTextDeltaEvent
 * full reasoning text} and {@link com.openai.models.responses.ResponseReasoningSummaryTextDeltaEvent
 * summaries}) for the GPT-5 reasoning model family. Within a single {@link #streamChat} call,
 * subsequent tool-call rounds chain via {@code previousResponseId} so the model retains its prior
 * reasoning without re-thinking each round; across user turns we rebuild from local history.
 */
public class OpenAiStreamingChatClient implements StreamingChatClient {
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * TTFT diagnostics logger; off by default. Enable in code or via the JDK logging config to
     * measure the impact of client reuse, prompt-cache routing, and per-round first-byte timing
     * (e.g. {@code java.util.logging.Logger.getLogger("treepeater.ai.openai.tt").setLevel(Level.FINE)}).
     */
    private static final Logger TIMING = Logger.getLogger("treepeater.ai.openai.tt");

    /**
     * Process-wide cache of {@link OpenAIClient} instances keyed on the fields that affect HTTP
     * client construction. Reusing the SDK client preserves the OkHttp connection pool and HTTP/2
     * session across user messages, so the TLS handshake to a forward proxy (e.g. Burp on
     * {@code 127.0.0.1:8080}) is paid once per JVM rather than once per user turn.
     */
    private static final ConcurrentHashMap<ClientCacheKey, OpenAIClient> CLIENT_CACHE =
            new ConcurrentHashMap<>();

    /**
     * Lazily-built shared TLS artifacts for the trust-all configuration used when an HTTP proxy is
     * present. All proxied configurations get the same trust-all socket factory, so this is a
     * single global pair rather than per-config.
     */
    private static volatile TrustAllTls SHARED_TRUST_ALL_TLS;

    /** Wire-format type tags expected by the Responses API for assistant tool calls / their outputs. */
    private static final JsonValue TYPE_FUNCTION_CALL = JsonValue.from("function_call");
    private static final JsonValue TYPE_FUNCTION_CALL_OUTPUT = JsonValue.from("function_call_output");
    private static final JsonValue TYPE_FUNCTION = JsonValue.from("function");

    private final OpenAiClientConfig config;

    public OpenAiStreamingChatClient(OpenAiClientConfig config) {
        this.config = config;
    }

    @Override
    public List<ChatMessage> streamChat(
            List<ChatMessage> messages, ChatTooling tooling, ChatStreamSession session) throws Exception {
        OpenAIClient client = sharedClient(this.config);
        String cacheKey = resolvePromptCacheKey(session);
        String instructions = extractSystemInstructions(messages);
        List<ResponseInputItem> initialInput = translateNonSystemHistory(messages);
        List<FunctionTool> tools = translateTools(tooling);

        List<ChatMessage> work = new ArrayList<>(messages);
        String previousResponseId = null;
        List<ResponseInputItem> roundInput = initialInput;

        for (int round = 0; round < StreamingChatClient.MAX_AGENT_TOOL_ROUNDS; round++) {
            if (session.isClosed() || Thread.currentThread().isInterrupted()) {
                return work;
            }
            ResponseCreateParams params =
                    buildParams(instructions, roundInput, tools, cacheKey, previousResponseId);
            RoundResult rr = streamOneRound(client, params, session, round);
            previousResponseId = rr.responseId();

            ChatMessage assistant =
                    new ChatMessage(ChatRole.ASSISTANT, rr.text(), rr.toolCalls(), null);
            work.add(assistant);

            if (session.isClosed() || Thread.currentThread().isInterrupted()) {
                return work;
            }
            if (rr.toolCalls().isEmpty() || tooling == null || !tooling.isActive()) {
                return work;
            }

            List<String> results =
                    ParallelToolExecution.executeRound(rr.toolCalls(), tooling, session);
            roundInput = new ArrayList<>(rr.toolCalls().size());
            for (int i = 0; i < rr.toolCalls().size(); i++) {
                ChatToolCall tc = rr.toolCalls().get(i);
                String result = results.get(i);
                if (session.isClosed() || Thread.currentThread().isInterrupted()) {
                    return work;
                }
                work.add(new ChatMessage(ChatRole.TOOL, result, List.of(), tc.id()));
                roundInput.add(buildFunctionCallOutput(tc.id(), result));
            }
        }
        return work;
    }

    /**
     * Default cache-key fallback when {@link ChatStreamSession#conversationKey()} is empty: per-session
     * identity. Stable per-conversation keys live on the {@link ChatStreamSession} that the UI provides.
     */
    private static String resolvePromptCacheKey(ChatStreamSession session) {
        String stable = session != null ? session.conversationKey() : "";
        if (stable != null && !stable.isBlank()) {
            return "treepeater-" + stable;
        }
        return "treepeater-" + System.identityHashCode(session);
    }

    private static String extractSystemInstructions(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : messages) {
            if (m.role() == ChatRole.SYSTEM && m.content() != null && !m.content().isEmpty()) {
                if (sb.length() > 0) {
                    sb.append("\n\n");
                }
                sb.append(m.content());
            }
        }
        return sb.toString();
    }

    /**
     * Translates conversation history (excluding {@link ChatRole#SYSTEM}, which goes to
     * {@code instructions}) into Responses-API input items. Assistant turns with tool calls expand
     * into an optional assistant-text item plus one {@code function_call} item per call; tool
     * results become {@code function_call_output} items keyed by their {@code call_id}.
     */
    private static List<ResponseInputItem> translateNonSystemHistory(List<ChatMessage> messages) {
        List<ResponseInputItem> out = new ArrayList<>(messages.size());
        for (ChatMessage m : messages) {
            switch (m.role()) {
                case SYSTEM -> {
                    // already extracted into instructions
                }
                case USER ->
                        out.add(
                                ResponseInputItem.ofEasyInputMessage(
                                        EasyInputMessage.builder()
                                                .role(EasyInputMessage.Role.USER)
                                                .content(m.content())
                                                .build()));
                case ASSISTANT -> {
                    if (m.content() != null && !m.content().isEmpty()) {
                        out.add(
                                ResponseInputItem.ofEasyInputMessage(
                                        EasyInputMessage.builder()
                                                .role(EasyInputMessage.Role.ASSISTANT)
                                                .content(m.content())
                                                .build()));
                    }
                    if (m.hasAssistantToolCalls()) {
                        for (ChatToolCall tc : m.assistantToolCalls()) {
                            out.add(buildFunctionCallItem(tc));
                        }
                    }
                }
                case TOOL -> out.add(buildFunctionCallOutput(m.toolCallId(), m.content()));
            }
        }
        return out;
    }

    private static ResponseInputItem buildFunctionCallItem(ChatToolCall tc) {
        String args = tc.argumentsJson() != null && !tc.argumentsJson().isEmpty() ? tc.argumentsJson() : "{}";
        return ResponseInputItem.ofFunctionCall(
                ResponseFunctionToolCall.builder()
                        .type(TYPE_FUNCTION_CALL)
                        .callId(tc.id() != null ? tc.id() : "")
                        .name(tc.name() != null ? tc.name() : "")
                        .arguments(args)
                        .build());
    }

    private static ResponseInputItem buildFunctionCallOutput(String callId, String output) {
        return ResponseInputItem.ofFunctionCallOutput(
                ResponseInputItem.FunctionCallOutput.builder()
                        .type(TYPE_FUNCTION_CALL_OUTPUT)
                        .callId(callId != null ? callId : "")
                        .output(output != null ? output : "")
                        .build());
    }

    private List<FunctionTool> translateTools(ChatTooling tooling) throws JsonProcessingException {
        if (tooling == null || !tooling.isActive()) {
            return List.of();
        }
        List<ChatToolDefinition> defs = tooling.tools();
        List<FunctionTool> out = new ArrayList<>(defs.size());
        for (ChatToolDefinition def : defs) {
            out.add(
                    FunctionTool.builder()
                            .type(TYPE_FUNCTION)
                            .name(def.name())
                            .description(def.description())
                            .strict(false)
                            .parameters(schemaToFunctionToolParameters(def.parametersJsonSchema()))
                            .build());
        }
        return out;
    }

    private ResponseCreateParams buildParams(
            String instructions,
            List<ResponseInputItem> input,
            List<FunctionTool> tools,
            String cacheKey,
            String previousResponseId) {
        ResponseCreateParams.Builder b =
                ResponseCreateParams.builder()
                        .model(this.config.deploymentName())
                        .maxOutputTokens(4096L)
                        .reasoning(
                                Reasoning.builder()
                                        .effort(this.config.reasoningEffort())
                                        .summary(Reasoning.Summary.AUTO)
                                        .build())
                        .parallelToolCalls(true)
                        .store(true);

        if (instructions != null && !instructions.isEmpty()) {
            b.instructions(instructions);
        }
        b.inputOfResponse(input);
        if (cacheKey != null && !cacheKey.isBlank()) {
            b.promptCacheKey(cacheKey);
        }
        if (previousResponseId != null && !previousResponseId.isBlank()) {
            b.previousResponseId(previousResponseId);
        }
        if (!tools.isEmpty()) {
            for (FunctionTool t : tools) {
                b.addTool(t);
            }
            b.toolChoice(ToolChoiceOptions.AUTO);
        }
        return b.build();
    }

    private static RoundResult streamOneRound(
            OpenAIClient client, ResponseCreateParams params, ChatStreamSession session, int round)
            throws Exception {
        boolean fineTiming = TIMING.isLoggable(Level.FINE);
        long roundStartNs = fineTiming ? System.nanoTime() : 0L;
        boolean firstChunkSeen = false;

        StringBuilder textOut = new StringBuilder();
        // Tool calls are referenced by SDK item id during streaming and finalized on outputItemDone.
        // Insertion order matters: ParallelToolExecution executes the calls in this exact order.
        Map<String, ResponsesToolStreamAccumulator> toolByItemId = new LinkedHashMap<>();
        Map<String, ResponsesToolStreamAccumulator> toolByCallId = new HashMap<>();
        String responseId = null;
        boolean errored = false;

        try (StreamResponse<ResponseStreamEvent> stream = client.responses().createStreaming(params)) {
            Iterator<ResponseStreamEvent> it = stream.stream().iterator();
            while (it.hasNext()
                    && !session.isClosed()
                    && !Thread.currentThread().isInterrupted()) {
                ResponseStreamEvent ev = it.next();
                if (fineTiming && !firstChunkSeen) {
                    firstChunkSeen = true;
                    long ms = (System.nanoTime() - roundStartNs) / 1_000_000L;
                    TIMING.fine("round=" + round + " first event after " + ms + "ms");
                }

                if (ev.isOutputTextDelta()) {
                    String d = ev.asOutputTextDelta().delta();
                    if (d != null && !d.isEmpty()) {
                        textOut.append(d);
                        session.emit(new ChatStreamMessage.AssistantDelta(d));
                    }
                    continue;
                }
                if (ev.isReasoningTextDelta()) {
                    String d = ev.asReasoningTextDelta().delta();
                    if (d != null && !d.isEmpty()) {
                        session.emit(new ChatStreamMessage.ThinkingDelta(d));
                    }
                    continue;
                }
                if (ev.isReasoningSummaryTextDelta()) {
                    String d = ev.asReasoningSummaryTextDelta().delta();
                    if (d != null && !d.isEmpty()) {
                        session.emit(new ChatStreamMessage.ThinkingDelta(d));
                    }
                    continue;
                }
                if (ev.isOutputItemAdded()) {
                    handleOutputItemAdded(ev.asOutputItemAdded().item(), toolByItemId, toolByCallId, session);
                    continue;
                }
                if (ev.isFunctionCallArgumentsDelta()) {
                    var d = ev.asFunctionCallArgumentsDelta();
                    ResponsesToolStreamAccumulator acc = toolByItemId.get(d.itemId());
                    if (acc != null && d.delta() != null) {
                        acc.args.append(d.delta());
                    }
                    continue;
                }
                if (ev.isOutputItemDone()) {
                    handleOutputItemDone(ev.asOutputItemDone().item(), toolByItemId, toolByCallId);
                    continue;
                }
                if (ev.isCompleted()) {
                    Response r = ev.asCompleted().response();
                    responseId = r.id();
                    break;
                }
                if (ev.isError() || ev.isFailed() || ev.isIncomplete()) {
                    errored = true;
                    if (TIMING.isLoggable(Level.FINE)) {
                        TIMING.fine("round=" + round + " ended early: " + ev);
                    }
                    break;
                }
            }
        }

        List<ChatToolCall> toolCalls = new ArrayList<>(toolByItemId.size());
        int synthIndex = 0;
        for (ResponsesToolStreamAccumulator acc : toolByItemId.values()) {
            String callId =
                    acc.callId != null && !acc.callId.isBlank()
                            ? acc.callId
                            : ("openai-tool-" + (synthIndex++));
            String name = acc.name != null ? acc.name : "";
            String args = acc.args.length() == 0 ? "{}" : acc.args.toString();
            toolCalls.add(new ChatToolCall(callId, name, args));
        }

        if (fineTiming) {
            long ms = (System.nanoTime() - roundStartNs) / 1_000_000L;
            TIMING.fine(
                    "round="
                            + round
                            + " complete in "
                            + ms
                            + "ms (toolCalls="
                            + toolCalls.size()
                            + ", contentChars="
                            + textOut.length()
                            + (errored ? ", errored" : "")
                            + ")");
        }
        return new RoundResult(textOut.toString(), toolCalls, responseId);
    }

    /**
     * On {@code response.output_item.added} for a function call, register a streaming accumulator
     * and emit an early {@link ChatStreamMessage.ToolCallStarted} so the UI can swap a generic
     * "thinking" indicator for a concrete "calling tool" cue while arguments stream in.
     */
    private static void handleOutputItemAdded(
            ResponseOutputItem item,
            Map<String, ResponsesToolStreamAccumulator> toolByItemId,
            Map<String, ResponsesToolStreamAccumulator> toolByCallId,
            ChatStreamSession session) {
        if (!item.isFunctionCall()) {
            return;
        }
        ResponseFunctionToolCall call = item.asFunctionCall();
        String itemId = call.id().orElse("");
        String callId = call.callId();
        String name = call.name();
        ResponsesToolStreamAccumulator acc = new ResponsesToolStreamAccumulator();
        acc.callId = callId;
        acc.name = name;
        if (!itemId.isEmpty()) {
            toolByItemId.put(itemId, acc);
        }
        if (callId != null && !callId.isBlank()) {
            toolByCallId.put(callId, acc);
        }
        if (!acc.announced && name != null && !name.isEmpty()) {
            acc.announced = true;
            String announcedId =
                    callId != null && !callId.isBlank()
                            ? callId
                            : (itemId.isEmpty() ? "openai-tool" : itemId);
            session.emit(new ChatStreamMessage.ToolCallStarted(announcedId, name));
        }
    }

    /**
     * On {@code response.output_item.done} for a function call, replace the streamed argument
     * accumulator with the canonical, fully-formed strings from the SDK item. This avoids any
     * possibility of a partial-argument edge case if the stream is closed mid-way after a
     * {@code function_call_arguments.done} but before the corresponding {@code output_item.done}.
     */
    private static void handleOutputItemDone(
            ResponseOutputItem item,
            Map<String, ResponsesToolStreamAccumulator> toolByItemId,
            Map<String, ResponsesToolStreamAccumulator> toolByCallId) {
        if (!item.isFunctionCall()) {
            return;
        }
        ResponseFunctionToolCall call = item.asFunctionCall();
        String itemId = call.id().orElse("");
        String callId = call.callId();
        ResponsesToolStreamAccumulator acc =
                !itemId.isEmpty() ? toolByItemId.get(itemId) : null;
        if (acc == null && callId != null && !callId.isBlank()) {
            acc = toolByCallId.get(callId);
        }
        if (acc == null) {
            // We never saw the corresponding output_item.added; create one now so the caller still
            // gets a usable tool call. Insertion order goes by the done event in this fallback.
            acc = new ResponsesToolStreamAccumulator();
            String key = !itemId.isEmpty() ? itemId : (callId != null ? callId : "");
            if (!key.isEmpty()) {
                toolByItemId.put(key, acc);
            }
        }
        acc.callId = callId;
        acc.name = call.name();
        String fullArgs = call.arguments();
        if (fullArgs != null) {
            acc.args.setLength(0);
            acc.args.append(fullArgs);
        }
    }

    /**
     * Memoized JSON-Schema → {@link FunctionTool.Parameters} conversion, keyed on the verbatim
     * schema string. Tool schemas are static strings built once at startup, so a single parse per
     * distinct schema is enough for the lifetime of the JVM. Saves roughly one
     * {@link JSON#readTree} call per tool per round (8 tools per round in the default agent
     * configuration).
     */
    private static final ConcurrentHashMap<String, FunctionTool.Parameters> SCHEMA_PARAMETERS_CACHE =
            new ConcurrentHashMap<>();

    /** Package-private for tests; converts Treepeater JSON Schema strings to OpenAI Responses-API tool parameters. */
    static FunctionTool.Parameters schemaToFunctionToolParameters(String jsonSchema)
            throws JsonProcessingException {
        String key = jsonSchema != null ? jsonSchema : "";
        FunctionTool.Parameters cached = SCHEMA_PARAMETERS_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        FunctionTool.Parameters built = schemaToFunctionToolParametersUncached(jsonSchema);
        FunctionTool.Parameters prev = SCHEMA_PARAMETERS_CACHE.putIfAbsent(key, built);
        return prev != null ? prev : built;
    }

    private static FunctionTool.Parameters schemaToFunctionToolParametersUncached(String jsonSchema)
            throws JsonProcessingException {
        final JsonNode n;
        try {
            n = JSON.readTree(jsonSchema);
        } catch (JsonProcessingException e) {
            return FunctionTool.Parameters.builder().build();
        }
        if (!n.isObject()) {
            return FunctionTool.Parameters.builder().build();
        }
        FunctionTool.Parameters.Builder fb = FunctionTool.Parameters.builder();
        for (var e : n.properties()) {
            fb.putAdditionalProperty(e.getKey(), JsonValue.fromJsonNode(e.getValue()));
        }
        return fb.build();
    }

    /**
     * Returns a cached {@link OpenAIClient} for the given {@code config}. Subsequent calls with an
     * equal {@link ClientCacheKey} return the same instance, preserving the OkHttp connection pool
     * across user messages so the TLS handshake (including any forward HTTP proxy) is amortized.
     * The returned client is intentionally never {@code close()}d on a per-call basis.
     */
    private static OpenAIClient sharedClient(OpenAiClientConfig config) {
        ClientCacheKey key = ClientCacheKey.of(config);
        OpenAIClient cached = CLIENT_CACHE.get(key);
        if (cached != null) {
            if (TIMING.isLoggable(Level.FINE)) {
                TIMING.fine("OpenAIClient cache hit for endpoint=" + key.endpoint());
            }
            return cached;
        }
        long t0 = System.nanoTime();
        OpenAIClient built = buildClient(config);
        OpenAIClient prev = CLIENT_CACHE.putIfAbsent(key, built);
        if (prev != null) {
            return prev;
        }
        if (TIMING.isLoggable(Level.FINE)) {
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            TIMING.fine(
                    "OpenAIClient cache miss for endpoint="
                            + key.endpoint()
                            + " (built in "
                            + ms
                            + "ms; subsequent turns reuse this instance)");
        }
        return built;
    }

    private static OpenAIClient buildClient(OpenAiClientConfig config) {
        // Azure routes the Responses API through the "unified" v1 surface — see the SDK's
        // {@code AzureUrlCategory.categorizeBaseUrl}: a base URL ending in {@code /openai/v1} is
        // categorized as {@code AZURE_UNIFIED} and the SDK skips the legacy
        // {@code /openai/deployments/{deployment}/} prefix. Without this, requests hit
        // {@code /openai/deployments/{deployment}/responses}, which Azure rejects with 404.
        //
        // For {@code /openai/v1/responses}, Microsoft documents the query string as
        // {@code api-version=preview} — not a dated {@code YYYY-MM-DD-preview} string. Using
        // {@link AzureOpenAIServiceVersion#latestPreviewVersion()} sends e.g. {@code 2025-03-01-preview},
        // which many Azure deployments reject with "API version not supported" on this route.
        String base = normalizeUnifiedAzureEndpoint(config.endpoint());
        var builder = OpenAIOkHttpClient.builder()
                .apiKey(config.apiKey())
                .baseUrl(base)
                .azureServiceVersion(AzureOpenAIServiceVersion.fromString("preview"))
                .azureUrlPathMode(AzureUrlPathMode.UNIFIED);
        config.httpProxy()
                .ifPresent(
                        addr -> {
                            builder.proxy(new Proxy(Proxy.Type.HTTP, addr));
                            applyTrustAllTls(builder);
                        });
        return builder.build();
    }

    /**
     * Accepts any server certificate so HTTPS through an intercepting HTTP proxy (e.g. Burp) works when
     * the proxy presents a self-signed replacement cert. The {@link SSLContext} and derived
     * {@link SSLSocketFactory} are built once per JVM and reused across all clients, so we do not pay
     * {@code SSLContext.init} on every {@link #buildClient} call.
     */
    private static void applyTrustAllTls(OpenAIOkHttpClient.Builder builder) {
        TrustAllTls tls = sharedTrustAllTls();
        builder.sslSocketFactory(tls.socketFactory);
        builder.trustManager(tls.trustManager);
        builder.hostnameVerifier((hostname, session) -> true);
    }

    private static TrustAllTls sharedTrustAllTls() {
        TrustAllTls local = SHARED_TRUST_ALL_TLS;
        if (local != null) {
            return local;
        }
        synchronized (OpenAiStreamingChatClient.class) {
            if (SHARED_TRUST_ALL_TLS != null) {
                return SHARED_TRUST_ALL_TLS;
            }
            try {
                X509TrustManager tm =
                        new X509TrustManager() {
                            @Override
                            public X509Certificate[] getAcceptedIssuers() {
                                return new X509Certificate[0];
                            }

                            @Override
                            public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                            @Override
                            public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                        };
                SSLContext ssl = SSLContext.getInstance("TLS");
                ssl.init(null, new TrustManager[] {tm}, new SecureRandom());
                SHARED_TRUST_ALL_TLS = new TrustAllTls(ssl.getSocketFactory(), tm);
                return SHARED_TRUST_ALL_TLS;
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException(
                        "Could not relax TLS verification for HTTP proxy (self-signed MITM certs)", e);
            }
        }
    }

    private static String trimTrailingSlashes(String endpoint) {
        String s = endpoint.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /**
     * Normalizes an Azure OpenAI / Foundry endpoint to the canonical Responses-API base URL used
     * with {@link AzureUrlPathMode#UNIFIED}: {@code {resource}/openai/v1} (without trailing slash).
     * The user typically configures only the resource root in settings; this transparently appends
     * the {@code /openai/v1} suffix while leaving an already-correctly-shaped URL untouched, and
     * collapses an accidental {@code /openai} suffix.
     */
    private static String normalizeUnifiedAzureEndpoint(String endpoint) {
        String s = trimTrailingSlashes(endpoint);
        if (s.endsWith("/openai/v1")) {
            return s;
        }
        if (s.endsWith("/openai")) {
            return s + "/v1";
        }
        return s + "/openai/v1";
    }

    private static final class ResponsesToolStreamAccumulator {
        String callId;
        String name = "";
        final StringBuilder args = new StringBuilder();
        /** {@code true} once a {@link ChatStreamMessage.ToolCallStarted} has been emitted for this call. */
        boolean announced;
    }

    /** Snapshot of one assistant turn produced by the Responses API stream. */
    private record RoundResult(String text, List<ChatToolCall> toolCalls, String responseId) {}

    /** Identity for {@link #CLIENT_CACHE}: anything that affects the underlying HTTP client. */
    private record ClientCacheKey(String endpoint, String apiKey, String proxyHost, int proxyPort) {
        static ClientCacheKey of(OpenAiClientConfig cfg) {
            String endpoint =
                    normalizeUnifiedAzureEndpoint(cfg.endpoint() != null ? cfg.endpoint() : "");
            String apiKey = cfg.apiKey() != null ? cfg.apiKey() : "";
            Optional<InetSocketAddress> proxy = cfg.httpProxy();
            String host = proxy.map(InetSocketAddress::getHostString).orElse("");
            int port = proxy.map(InetSocketAddress::getPort).orElse(-1);
            return new ClientCacheKey(endpoint, apiKey, host, port);
        }
    }

    /** Lazily-built TLS artifacts for {@link #applyTrustAllTls}; immutable after construction. */
    private static final class TrustAllTls {
        final SSLSocketFactory socketFactory;
        final X509TrustManager trustManager;

        TrustAllTls(SSLSocketFactory socketFactory, X509TrustManager trustManager) {
            this.socketFactory = socketFactory;
            this.trustManager = trustManager;
        }
    }
}
