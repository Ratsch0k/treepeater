package treepeater.api.tools.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import treepeater.ai.NestedToolInvoker;
import treepeater.api.Json;
import treepeater.api.TreepeaterTool;

/** Adapts a {@link TreepeaterToolSpec} to the wire-level {@link TreepeaterTool} record. */
public final class TreepeaterToolAdapter {

    private static final ObjectMapper JSON = new ObjectMapper();

    private TreepeaterToolAdapter() {}

    public static TreepeaterTool toRecord(TreepeaterToolSpec spec, ToolRuntime runtime) {
        return new TreepeaterTool(
                spec.name(),
                spec.description(),
                spec.inputSchemaJson(),
                spec.actionLevel(),
                argsJson -> spec.invoke(buildInvocation(spec, argsJson, runtime, null)));
    }

    public static ToolInvocation buildInvocation(
            TreepeaterToolSpec spec,
            String argumentsJson,
            ToolRuntime runtime,
            NestedToolInvoker nested) {
        JsonNode args = parseArgs(argumentsJson);
        return new ToolInvocation(args, argumentsJson != null ? argumentsJson : "", runtime, nested);
    }

    static JsonNode parseArgs(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Json.obj();
        }
        try {
            JsonNode node = JSON.readTree(argumentsJson);
            return node != null && node.isObject() ? node : Json.obj();
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
