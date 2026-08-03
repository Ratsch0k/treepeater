package treepeater.api.tools.core;

import java.util.OptionalInt;

import com.fasterxml.jackson.databind.JsonNode;

import treepeater.ai.AgentToolContext;
import treepeater.ai.TreepeaterTabAgentBridge;
import treepeater.api.Json;

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

    protected static OptionalInt parseRequestNodeId(JsonNode args) {
        if (args == null) {
            return OptionalInt.empty();
        }
        return Json.integer(args, "request_node_id", "requestNodeId");
    }

    protected String capResult(String result) {
        return ToolResults.capResult(result);
    }
}
