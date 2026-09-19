package treepeater.api.server;

import treepeater.api.TreepeaterToolRegistry;

/**
 * Adapter for {@code /mcp}: picks the wire revision from the request, hands the body to
 * {@link McpDispatcher}, and maps the outcome onto a status. All protocol behaviour lives in the
 * dispatcher, and no HTTP library type appears here.
 */
public final class McpHandler {

    private final McpDispatcher dispatcher;

    public McpHandler(TreepeaterToolRegistry registry) {
        this.dispatcher = new McpDispatcher(registry);
    }

    public ApiResponse handle(ApiRequest request) {
        if (!request.isMethod("POST")) {
            return ApiResponse.error(405, "method not allowed");
        }
        String body = request.body();
        String mcpMethod = request.header("Mcp-Method");
        String mcpName = request.header("Mcp-Name");
        McpDispatcher.Revision revision = McpDispatcher.detectRevision(body, mcpMethod);

        String response = this.dispatcher.handle(body, revision, mcpMethod, mcpName);
        // Notifications only: accepted, with nothing to report back.
        return response == null ? ApiResponse.status(202) : ApiResponse.ok(response);
    }
}
