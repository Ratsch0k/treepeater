package treepeater.api;

import treepeater.ai.ToolActionLevel;

/**
 * One callable operation in the shared registry. The same record backs AI chat tool declarations, the
 * REST endpoints, and MCP {@code tools/list} / {@code tools/call}, so a tool only has to be written once.
 *
 * @param inputSchemaJson JSON Schema 2020-12 object describing the arguments
 * @param actionLevel sensitivity used to gate external callers via {@link ApiPolicy}
 */
public record TreepeaterTool(
        String name,
        String description,
        String inputSchemaJson,
        ToolActionLevel actionLevel,
        Handler handler) {

    public TreepeaterTool {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("tool name required");
        }
        if (description == null) {
            description = "";
        }
        if (inputSchemaJson == null || inputSchemaJson.isBlank()) {
            inputSchemaJson = "{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}";
        }
    }

    /** Runs the tool and returns a JSON result string, or a {@link Json#error(String)} envelope. */
    @FunctionalInterface
    public interface Handler {
        String invoke(String argumentsJson) throws Exception;
    }
}
