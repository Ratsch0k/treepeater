package treepeater.api.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import treepeater.api.ApiPolicy;
import treepeater.api.Json;
import treepeater.api.TreepeaterTool;
import treepeater.api.TreepeaterToolRegistry;

/**
 * JSON-RPC dispatch for the MCP endpoint, covering both the {@code 2025-11-25} handshake revision and the
 * stateless {@code 2026-07-28} revision. Deliberately free of any HTTP type: it maps a request string to a
 * response string so the protocol can be exercised without opening a socket.
 */
public final class McpDispatcher {

    public static final String PROTOCOL_2025 = "2025-11-25";
    public static final String PROTOCOL_2026 = "2026-07-28";

    private static final int PARSE_ERROR = -32700;
    private static final int INVALID_REQUEST = -32600;
    private static final int METHOD_NOT_FOUND = -32601;
    private static final int INVALID_PARAMS = -32602;

    /** {@code HeaderMismatch} from the 2026-07-28 schema, raised when SEP-2243 headers contradict the body. */
    private static final int HEADER_MISMATCH = -32020;

    private static final String SERVER_NAME = "treepeater";
    private static final String SERVER_TITLE = "Treepeater";
    private static final String SERVER_VERSION = "1.0.0";

    private static final String PROTOCOL_META_KEY = "io.modelcontextprotocol/protocolVersion";

    /** SEP-2549 cache hints, letting a stateless client reuse a tool list instead of refetching per call. */
    private static final int TOOLS_CACHE_TTL_MS = 60000;

    /** Wire revision in use: the handshake-based one or the stateless successor. */
    public enum Revision {
        LEGACY,
        STATELESS
    }

    private final TreepeaterToolRegistry registry;

    public McpDispatcher(TreepeaterToolRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("registry required");
        }
        this.registry = registry;
    }

    /**
     * Handles a single JSON-RPC object or a batch array.
     *
     * @param headerMethod value of the {@code Mcp-Method} header, or {@code null}
     * @param headerName value of the {@code Mcp-Name} header, or {@code null}
     * @return the response JSON, or {@code null} when the input contained only notifications
     */
    public String handle(String requestJson, Revision revision, String headerMethod, String headerName) {
        Revision effective = revision != null ? revision : Revision.LEGACY;
        if (requestJson == null || requestJson.isBlank()) {
            return Json.nodeToString(errorResponse(null, PARSE_ERROR, "parse error"));
        }
        JsonNode root;
        try {
            root = Json.stringToNode(requestJson);
        } catch (Exception e) {
            return Json.nodeToString(errorResponse(null, PARSE_ERROR, "parse error"));
        }
        if (root.isArray()) {
            ArrayNode responses = Json.arr();
            for (JsonNode element : root) {
                ObjectNode response = this.dispatch(element, effective, headerMethod, headerName);
                if (response != null) {
                    responses.add(response);
                }
            }
            return responses.isEmpty() ? null : Json.nodeToString(responses);
        }
        if (!root.isObject()) {
            return Json.nodeToString(errorResponse(null, INVALID_REQUEST, "invalid request"));
        }
        ObjectNode response = this.dispatch(root, effective, headerMethod, headerName);
        return response != null ? Json.nodeToString(response) : null;
    }

    /**
     * Picks the revision from the {@code Mcp-Method} header, which only exists in the stateless revision,
     * or from an explicit protocol version in {@code params._meta}.
     */
    public static Revision detectRevision(String requestJson, String headerMethod) {
        if (headerMethod != null && !headerMethod.isBlank()) {
            return Revision.STATELESS;
        }
        try {
            JsonNode root = Json.stringToNode(requestJson);
            JsonNode message = root.isArray() ? root.get(0) : root;
            if (message == null || !message.isObject()) {
                return Revision.LEGACY;
            }
            JsonNode meta = Json.first(message.get("params"), "_meta");
            JsonNode version = meta != null ? meta.get(PROTOCOL_META_KEY) : null;
            if (version != null && PROTOCOL_2026.equals(version.asText())) {
                return Revision.STATELESS;
            }
        } catch (Exception e) {
            return Revision.LEGACY;
        }
        return Revision.LEGACY;
    }

    private ObjectNode dispatch(JsonNode message, Revision revision, String headerMethod, String headerName) {
        if (message == null || !message.isObject()) {
            return errorResponse(null, INVALID_REQUEST, "invalid request");
        }
        JsonNode id = message.get("id");
        // A notification carries no id and, per JSON-RPC, must never be answered - not even on error.
        boolean notification = id == null || id.isNull();
        if (notification) {
            return null;
        }
        String method = Json.text(message, "method");
        if (method == null) {
            return errorResponse(id, INVALID_REQUEST, "method required");
        }
        if (isStringEqual(headerMethod, method)) {
            return errorResponse(id, HEADER_MISMATCH, "header and body disagree");
        }
        JsonNode params = message.get("params");
        return switch (method) {
            case "initialize" -> revision == Revision.STATELESS
                    ? errorResponse(id, METHOD_NOT_FOUND, "method not found: initialize")
                    : resultResponse(id, initializeResult());
            case "server/discover" -> resultResponse(id, discoverResult());
            case "ping" -> resultResponse(id, Json.obj());
            case "tools/list" -> resultResponse(id, this.toolsListResult(revision));
            case "tools/call" -> this.toolsCall(id, params, headerName);
            default -> errorResponse(id, METHOD_NOT_FOUND, "method not found: " + method);
        };
    }

    private static ObjectNode initializeResult() {
        ObjectNode result = Json.obj();
        result.put("protocolVersion", PROTOCOL_2025);
        result.set("capabilities", capabilities());
        result.set("serverInfo", serverInfo());
        return result;
    }

    private static ObjectNode discoverResult() {
        ArrayNode versions = Json.arr();
        versions.add(PROTOCOL_2026);
        versions.add(PROTOCOL_2025);
        ObjectNode result = Json.obj();
        result.set("protocolVersions", versions);
        result.set("capabilities", capabilities());
        result.set("serverInfo", serverInfo());
        return result;
    }

    /** Full catalogue in one page; the tool count is small enough that a cursor would only add failure modes. */
    private ObjectNode toolsListResult(Revision revision) {
        ArrayNode list = Json.arr();
        for (TreepeaterTool tool : this.registry.tools()) {
            ObjectNode row = Json.obj();
            row.put("name", tool.name());
            row.put("description", tool.description());
            row.set("inputSchema", Json.resultAsNode(tool.inputSchemaJson()));
            list.add(row);
        }
        ObjectNode result = Json.obj();
        result.set("tools", list);
        if (revision == Revision.STATELESS) {
            result.put("ttlMs", TOOLS_CACHE_TTL_MS);
            result.put("cacheScope", "session");
        }
        return result;
    }

    /**
     * Runs a tool. A tool that reports failure is still a successful call: the envelope travels back in the
     * content block with {@code isError} set, and only a malformed request becomes a JSON-RPC error.
     */
    private ObjectNode toolsCall(JsonNode id, JsonNode params, String headerName) {
        String name = Json.text(params, "name");
        if (name == null || name.isBlank()) {
            return errorResponse(id, INVALID_PARAMS, "params.name is required");
        }
        if (isStringEqual(headerName, name)) {
            return errorResponse(id, HEADER_MISMATCH, "header and body disagree");
        }
        JsonNode arguments = params != null ? params.get("arguments") : null;
        String argumentsJson = arguments != null && arguments.isObject() ? Json.nodeToString(arguments) : "{}";
        String toolResult = this.registry.execute(name, argumentsJson, ApiPolicy.fromSettings());

        ObjectNode block = Json.obj();
        block.put("type", "text");
        block.put("text", toolResult);
        ArrayNode content = Json.arr();
        content.add(block);

        ObjectNode result = Json.obj();
        result.set("content", content);
        result.put("isError", Json.isError(toolResult));
        return resultResponse(id, result);
    }


    private static boolean isStringEqual(String headerValue, String bodyValue) {
        return headerValue != null && !headerValue.isBlank() && !headerValue.trim().equals(bodyValue);
    }

    private static ObjectNode capabilities() {
        ObjectNode tools = Json.obj();
        tools.put("listChanged", false);
        ObjectNode capabilities = Json.obj();
        capabilities.set("tools", tools);
        return capabilities;
    }

    private static ObjectNode serverInfo() {
        ObjectNode info = Json.obj();
        info.put("name", SERVER_NAME);
        info.put("title", SERVER_TITLE);
        info.put("version", SERVER_VERSION);
        return info;
    }

    private static ObjectNode resultResponse(JsonNode id, ObjectNode result) {
        ObjectNode out = envelope(id);
        out.set("result", result);
        return out;
    }

    private static ObjectNode errorResponse(JsonNode id, int code, String message) {
        ObjectNode error = Json.obj();
        error.put("code", code);
        error.put("message", message);
        ObjectNode out = envelope(id);
        out.set("error", error);
        return out;
    }

    /** Response skeleton echoing the request id with its original JSON type, which may be string or number. */
    private static ObjectNode envelope(JsonNode id) {
        ObjectNode out = Json.obj();
        out.put("jsonrpc", "2.0");
        if (id != null && !id.isNull()) {
            out.set("id", id.deepCopy());
        } else {
            out.putNull("id");
        }
        return out;
    }
}
