package treepeater.ai;

/**
 * Declares a callable tool: name, human-readable description, and a JSON Schema object for
 * {@code parameters} (per provider conventions).
 */
public record ChatToolDefinition(String name, String description, String parametersJsonSchema) {
    public ChatToolDefinition {
        if (name == null) {
            name = "";
        }
        if (description == null) {
            description = "";
        }
        if (parametersJsonSchema == null) {
            parametersJsonSchema = "{}";
        }
    }
}
