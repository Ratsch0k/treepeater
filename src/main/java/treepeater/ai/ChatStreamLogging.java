package treepeater.ai;

import java.util.logging.Level;
import java.util.logging.Logger;

import treepeater.Treepeater;

/**
 * Chat stream tracing: Burp’s Extender <em>Output</em> (via {@code MontoyaApi.logging().logToOutput}) when the
 * extension is loaded, otherwise JUL. Lines are prefixed with {@code [chatStream]} in Burp.
 * <p>
 * Token-level assistant chunks ({@link Level#FINE}) go to JUL by default. To mirror them to Burp as well, set
 * {@code -Dtreepeater.chatStream.verboseStream=true} (very chatty).
 * <p>
 * <strong>Timing</strong>: a {@code SEND API} line is written before building the request and opening the stream.
 * The matching {@code RECV API} line includes {@code wall_ms} (entire turn from just after that log through stream
 * end). {@code build_ms} is in-process request construction; {@code http_open_ms} is mostly blocking in
 * {@code createStreaming} until the response stream is ready (network + server). A large pre-stream time with a large
 * {@code http_open_ms} is not a Burp extension bug—it is time waiting on the API. A large {@code build_ms} alone
 * would point at local work building large payloads.
 */
public final class ChatStreamLogging {
    private static final String BURP_PREFIX = "[chatStream] ";
    private static final String BURP_VERBOSE = "[chatStream][verbose] ";

    private static final String LOGGER_NAME = "treepeater.chatStream";
    private static final Logger JUL = Logger.getLogger(LOGGER_NAME);

    /** JUL name; used when not running in Burp (e.g. unit tests) or in addition to FINE. */
    public static final String LOGGER_NAME_PUBLIC = LOGGER_NAME;

    private ChatStreamLogging() {}

    private static boolean verboseStreamToBurp() {
        return Boolean.getBoolean("treepeater.chatStream.verboseStream");
    }

    private static void line(Level level, String message) {
        boolean inBurp = Treepeater.api != null;
        if (level.intValue() >= Level.INFO.intValue()) {
            if (inBurp) {
                Treepeater.api.logging().logToOutput(BURP_PREFIX + message);
            } else {
                JUL.log(level, message);
            }
            return;
        }
        if (JUL.isLoggable(level)) {
            JUL.log(level, message);
        }
        if (inBurp && verboseStreamToBurp()) {
            Treepeater.api.logging().logToOutput(BURP_VERBOSE + message);
        }
    }

    /** User pressed Send: model choice and how many history messages (including the new user line) are sent. */
    public static void logUserTurnStart(String modelLabel, int historyMessageCount) {
        if (JUL.isLoggable(Level.INFO) || Treepeater.api != null) {
            line(
                    Level.INFO,
                    "SEND user turn | model=" + modelLabel + " | historyMessages=" + historyMessageCount);
        }
    }

    /**
     * A streaming chat request is about to be issued to a provider.
     *
     * @param client short label, e.g. "OpenAI", "Anthropic", "Ollama", "Burp"
     * @param modelId deployment name or model id
     * @param round 0-based index for tool loops; use 0 for a single request with no prior agent rounds
     * @param historySize number of {@link ChatMessage} entries in the request
     * @param tools whether tools are enabled for this request
     */
    public static void logApiRequest(
            String client, String modelId, int round, int historySize, boolean tools) {
        if (JUL.isLoggable(Level.INFO) || Treepeater.api != null) {
            String r = round < 0 ? "—" : String.valueOf(round + 1);
            line(
                    Level.INFO,
                    "SEND API "
                            + client
                            + " | model="
                            + modelId
                            + " | round="
                            + r
                            + " | history="
                            + historySize
                            + (tools ? " | tools=on" : " | tools=off"));
        }
    }

    /**
     * After a streaming call completes. See class javadoc for field meanings. {@code buildMs} + {@code httpOpenMs}
     * is the “pre-stream” phase (before the iterator); {@code streamMs} is the iterator only.
     */
    public static void logApiStreamComplete(
            String client,
            int round,
            long wallMs,
            long buildMs,
            long httpOpenMs,
            long ttfbMs,
            long streamMs,
            int streamEventCount) {
        if (JUL.isLoggable(Level.INFO) || Treepeater.api != null) {
            String r = round < 0 ? "—" : String.valueOf(round + 1);
            long preStream = buildMs + httpOpenMs;
            line(
                    Level.INFO,
                    "RECV API "
                            + client
                            + " | round="
                            + r
                            + " | wall_ms="
                            + wallMs
                            + " | build_ms="
                            + buildMs
                            + " | http_open_ms="
                            + httpOpenMs
                            + " | (pre_stream_ms="
                            + preStream
                            + ")"
                            + " | ttfb_ms="
                            + ttfbMs
                            + " | stream_ms="
                            + streamMs
                            + " | events="
                            + streamEventCount);
        }
    }

    /**
     * Per-round token usage. {@code cachedReadTokens} and {@code cachedWriteTokens} are 0 when the provider
     * does not report them (e.g. Ollama/Burp). A high {@code cachedReadTokens} ratio on rounds &gt; 1
     * indicates prompt caching is working; a persistently 0 value on Anthropic/OpenAI suggests the cache
     * breakpoint is wrong or the stable prefix is varying.
     */
    public static void logApiUsage(
            String client,
            int round,
            long inputTokens,
            long cachedReadTokens,
            long cachedWriteTokens,
            long outputTokens) {
        if (JUL.isLoggable(Level.INFO) || Treepeater.api != null) {
            String r = round < 0 ? "—" : String.valueOf(round + 1);
            line(
                    Level.INFO,
                    "RECV API "
                            + client
                            + " | round="
                            + r
                            + " | usage input_tokens="
                            + inputTokens
                            + " | cached_read="
                            + cachedReadTokens
                            + " | cached_write="
                            + cachedWriteTokens
                            + " | output_tokens="
                            + outputTokens);
        }
    }

    /**
     * Message from the model toward the UI. INFO: tool rows; FINE: each assistant text chunk (see class javadoc for
     * Burp verbosity).
     */
    public static void logEmitTowardUi(ChatStreamMessage m) {
        if (m instanceof ChatStreamMessage.AssistantDelta ad) {
            int n = ad.text().length();
            line(Level.FINE, "RECV toward UI (assistant) | chars=" + n);
        } else if (m instanceof ChatStreamMessage.ThinkingDelta td) {
            int n = td.text().length();
            line(Level.FINE, "RECV toward UI (thinking) | chars=" + n);
        } else if (m instanceof ChatStreamMessage.ToolApprovalRequest req) {
            if (JUL.isLoggable(Level.INFO) || Treepeater.api != null) {
                line(
                        Level.INFO,
                        "RECV toward UI (tool) | name="
                                + req.toolName()
                                + " | id="
                                + req.toolCallId()
                                + (req.requiresApproval() ? " | approval=required" : " | approval=not_required"));
            }
        }
    }

    /**
     * User (or auto-approval) posted a message into the session, e.g. tool approval, usually before the model run
     * continues.
     */
    public static void logSessionPostReply(ChatStreamMessage m) {
        if (m == null) {
            return;
        }
        if (m instanceof ChatStreamMessage.ToolApprovalResponse r) {
            if ("__session_closed__".equals(r.toolCallId())) {
                return;
            }
            if (JUL.isLoggable(Level.INFO) || Treepeater.api != null) {
                line(
                        Level.INFO,
                        "RECV into session (tool approval) | id=" + r.toolCallId() + " | approved=" + r.approved());
            }
        } else {
            line(Level.FINE, "RECV into session | " + m);
        }
    }
}
