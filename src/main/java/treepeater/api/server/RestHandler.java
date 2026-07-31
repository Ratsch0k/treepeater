package treepeater.api.server;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import treepeater.ai.ToolActionLevel;
import treepeater.api.ApiPolicy;
import treepeater.api.Json;
import treepeater.api.TreepeaterService;
import treepeater.api.TreepeaterTool;
import treepeater.api.TreepeaterToolRegistry;

/**
 * Plain REST surface under {@code /api/v1} for clients that do not speak MCP: server metadata, the tool
 * catalogue, tool invocation, and the read-only tree endpoints.
 *
 * <p>Holds no HTTP library type; the caller supplies an {@link ApiRequest} and receives an
 * {@link ApiResponse}, which keeps this testable without a socket.
 */
public final class RestHandler {

    private static final String CONTEXT = "/api/v1";

    private final TreepeaterToolRegistry registry;
    private final TreepeaterService service;

    public RestHandler(TreepeaterToolRegistry registry, TreepeaterService service) {
        if (registry == null) {
            throw new IllegalArgumentException("registry required");
        }
        if (service == null) {
            throw new IllegalArgumentException("service required");
        }
        this.registry = registry;
        this.service = service;
    }

    public ApiResponse handle(ApiRequest request) {
        String path = relativePath(request.path());
        return switch (path) {
            case "/health" -> requireGet(request, this::health);
            case "/tools" -> requireGet(request, this::tools);
            case "/tree" -> requireGet(request, this::tree);
            case "/statuses" -> requireGet(request, r -> ApiResponse.fromResult(this.service.statusesJson()));
            default -> this.dynamic(request, path);
        };
    }

    private ApiResponse dynamic(ApiRequest request, String path) {
        if (path.startsWith("/tools/")) {
            return request.isMethod("POST")
                    ? this.callTool(request, decode(path.substring("/tools/".length())))
                    : methodNotAllowed();
        }
        if (path.startsWith("/tree/nodes/")) {
            return request.isMethod("GET")
                    ? this.node(decode(path.substring("/tree/nodes/".length())))
                    : methodNotAllowed();
        }
        return ApiResponse.error(404, "not found");
    }

    private ApiResponse health(ApiRequest request) {
        ApiPolicy policy = ApiPolicy.fromSettings();
        ArrayNode protocols = Json.arr();
        protocols.add(McpDispatcher.PROTOCOL_2025);
        protocols.add(McpDispatcher.PROTOCOL_2026);

        ObjectNode out = Json.obj();
        out.put("name", "Treepeater");
        out.put("api_version", "v1");
        out.set("mcp_protocol_versions", protocols);
        out.put("tool_count", this.registry.tools().size());
        out.put("allow_write", policy.allowWrite());
        out.put("allow_execute", policy.allowExecute());
        return ApiResponse.ok(Json.nodeToString(out));
    }

    private ApiResponse tools(ApiRequest request) {
        ArrayNode list = Json.arr();
        for (TreepeaterTool tool : this.registry.tools()) {
            ObjectNode row = Json.obj();
            row.put("name", tool.name());
            row.put("description", tool.description());
            row.set("input_schema", Json.resultAsNode(tool.inputSchemaJson()));
            ToolActionLevel level = tool.actionLevel();
            if (level != null) {
                row.put("action_level", level.name());
            } else {
                row.putNull("action_level");
            }
            list.add(row);
        }
        ObjectNode out = Json.obj();
        out.set("tools", list);
        return ApiResponse.ok(Json.nodeToString(out));
    }

    private ApiResponse callTool(ApiRequest request, String name) {
        if (name.isBlank() || this.registry.find(name) == null) {
            return ApiResponse.error(404, "unknown tool: " + name);
        }
        return ApiResponse.fromResult(
                this.registry.execute(name, request.body(), ApiPolicy.fromSettings()));
    }

    private ApiResponse tree(ApiRequest request) {
        Map<String, String> query = parseQuery(request.rawQuery());
        Integer rootId = optionalInt(query.get("node_id"));
        Integer maxDepth = optionalInt(query.get("max_depth"));
        if (rootId == null || maxDepth == null) {
            return ApiResponse.error(400, "node_id and max_depth must be integers");
        }
        boolean includeRequests = !"false".equalsIgnoreCase(query.get("include_requests"));
        return ApiResponse.fromResult(this.service.treeJson(rootId, maxDepth, includeRequests));
    }

    private ApiResponse node(String rawId) {
        Integer id = optionalInt(rawId);
        if (id == null || rawId.isBlank()) {
            return ApiResponse.error(400, "node id must be an integer");
        }
        return ApiResponse.fromResult(this.service.nodeJson(id));
    }

    private interface Route {
        ApiResponse handle(ApiRequest request);
    }

    private static ApiResponse requireGet(ApiRequest request, Route route) {
        return request.isMethod("GET") ? route.handle(request) : methodNotAllowed();
    }

    private static ApiResponse methodNotAllowed() {
        return ApiResponse.error(405, "method not allowed");
    }

    /** Path below the {@code /api/v1} context. */
    private static String relativePath(String path) {
        return path.startsWith(CONTEXT) ? path.substring(CONTEXT.length()) : path;
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> out = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return out;
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int split = pair.indexOf('=');
            String key = split < 0 ? pair : pair.substring(0, split);
            String value = split < 0 ? "" : pair.substring(split + 1);
            out.put(decode(key), decode(value));
        }
        return out;
    }

    private static String decode(String raw) {
        try {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return raw;
        }
    }

    /** Parsed integer, {@code 0} for an absent value, or {@code null} when present but not numeric. */
    private static Integer optionalInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return Integer.valueOf(0);
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
