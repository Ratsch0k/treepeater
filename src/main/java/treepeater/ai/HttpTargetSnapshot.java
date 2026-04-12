package treepeater.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Read-only view of the request editor's HTTP target and request line, for AI tools and prompts.
 */
public record HttpTargetSnapshot(
        String scheme,
        String host,
        int port,
        boolean sniEnabled,
        String method,
        String url,
        String path) {

    private static final ObjectMapper JSON = new ObjectMapper();

    public String toJson() {
        ObjectNode n = JSON.createObjectNode();
        n.put("scheme", this.scheme);
        n.put("host", this.host);
        n.put("port", this.port);
        n.put("sniEnabled", this.sniEnabled);
        n.put("method", this.method);
        n.put("url", this.url);
        n.put("path", this.path);
        try {
            return JSON.writeValueAsString(n);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
