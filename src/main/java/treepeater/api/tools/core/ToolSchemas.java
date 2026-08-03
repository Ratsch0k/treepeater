package treepeater.api.tools.core;

/** Reusable JSON Schema fragments shared across tool declarations. */
public final class ToolSchemas {

    public static final String EMPTY_OBJECT =
            "{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}";

    public static final String NODE_ID =
            """
            {"type":"object","properties":{\
            "node_id":{"type":"integer","description":"Tree node id"}},\
            "required":["node_id"],"additionalProperties":false}""";

    private ToolSchemas() {}
}
