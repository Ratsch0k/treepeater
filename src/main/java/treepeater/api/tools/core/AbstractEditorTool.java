package treepeater.api.tools.core;

import java.util.OptionalInt;

import com.fasterxml.jackson.databind.JsonNode;

import treepeater.ai.AgentToolContext;
import treepeater.ai.TreepeaterTabAgentBridge;
import treepeater.api.tools.http.HttpToolLabels;

/** Base for HTTP/editor tools that resolve a repeater tab via {@link TreepeaterTabAgentBridge}. */
public abstract class AbstractEditorTool implements TreepeaterToolSpec {

    protected TreepeaterTabAgentBridge requireBridge(ToolInvocation inv) {
        TreepeaterTabAgentBridge bridge = inv.runtime().bridge();
        if (bridge == null) {
            throw new IllegalStateException("no bridge");
        }
        return bridge;
    }

    protected AgentToolContext requireContext(ToolInvocation inv) {
        TreepeaterTabAgentBridge bridge = requireBridge(inv);
        OptionalInt nodeId = parseRequestNodeId(inv.args());
        AgentToolContext ctx = bridge.contextForAgent(nodeId);
        if (ctx == null) {
            throw new IllegalStateException("no target context");
        }
        return ctx;
    }

    /** Numeric-only, {@code >= 1}; matches the {@code request_node_id} contract declared in each tool's schema. */
    protected static OptionalInt parseRequestNodeId(JsonNode args) {
        if (args == null || !args.isObject()) {
            return OptionalInt.empty();
        }
        JsonNode n = HttpToolLabels.firstArg(args, "request_node_id", "requestNodeId");
        if (n == null || !n.isNumber()) {
            return OptionalInt.empty();
        }
        int v = n.intValue();
        return v < 1 ? OptionalInt.empty() : OptionalInt.of(v);
    }

    protected String capResult(String result) {
        return ToolResults.capResult(result);
    }
}
