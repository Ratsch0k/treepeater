package treepeater.api.tools.http.support;

import burp.api.montoya.http.message.requests.HttpRequest;

/** In-memory request mutation preview for the AI diff UI (no EDT commit). */
public final class RequestPreview {

    private RequestPreview() {}

    public static HttpRequest tryPreviewRequestMutation(
            String toolName, String argumentsJson, HttpRequest current) {
        return HttpTargetSupport.tryPreviewRequestMutation(toolName, argumentsJson, current);
    }
}
