package treepeater.api.tools.http;

/** Reusable JSON Schema fragments shared across HTTP/editor tool declarations. */
final class HttpToolSchemas {

    static final String REQUEST_NODE_ID_PROPERTY =
            "\"request_node_id\":{\"type\":\"integer\",\"minimum\":1,\"description\":\"Tab id from search_tabs; omit=UI tab.\"}";

    /** Schema for tools that only take the optional {@code request_node_id}. */
    static final String OPTIONAL_TAB_PARAMS =
            "{\"type\":\"object\",\"properties\":{" + REQUEST_NODE_ID_PROPERTY + "},\"additionalProperties\":false}";

    private HttpToolSchemas() {}
}
