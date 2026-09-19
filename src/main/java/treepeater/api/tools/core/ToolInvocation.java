package treepeater.api.tools.core;

import com.fasterxml.jackson.databind.JsonNode;

import treepeater.ai.NestedToolInvoker;

/** Parsed arguments, runtime dependencies, and optional nested invoker for batch tools. */
public record ToolInvocation(
        JsonNode args,
        String argumentsJson,
        ToolRuntime runtime,
        NestedToolInvoker nestedInvoker) {

    public ToolInvocation {
        if (argumentsJson == null) {
            argumentsJson = "";
        }
    }

    public ToolInvocation(JsonNode args, String argumentsJson, ToolRuntime runtime) {
        this(args, argumentsJson, runtime, null);
    }
}
