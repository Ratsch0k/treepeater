package treepeater.api.tools.core;

import java.util.OptionalInt;
import java.util.function.IntFunction;

import com.fasterxml.jackson.databind.JsonNode;

import treepeater.api.Json;

/** Shared argument parsing helpers for tool handlers. */
public final class ToolArgs {

    private ToolArgs() {}

    /** Parses arguments once and reports a consistent error for malformed JSON. */
    public static String withArgs(String argumentsJson, ArgsHandler handler) {
        JsonNode parsed = Json.readArgs(argumentsJson);
        if (parsed == null) {
            return Json.error("arguments must be a JSON object");
        }
        return handler.handle(parsed);
    }

    public static String withNodeId(String argumentsJson, IntFunction<String> handler) {
        return withArgs(argumentsJson, parsed -> {
            OptionalInt nodeId = Json.integer(parsed, "node_id", "nodeId", "id");
            if (nodeId.isEmpty()) {
                return Json.error("node_id required");
            }
            return handler.apply(nodeId.getAsInt());
        });
    }

    @FunctionalInterface
    public interface ArgsHandler {
        String handle(JsonNode args);
    }
}
