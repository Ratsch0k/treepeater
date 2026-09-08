package treepeater.api.tools.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import treepeater.ai.ChatToolInvokeContext;
import treepeater.ai.NestedToolInvoker;
import treepeater.ai.ToolActionLevel;
import treepeater.api.TreepeaterToolRegistry;
import treepeater.api.tools.HumanToolUsage;
import treepeater.api.tools.core.AbstractEditorTool;
import treepeater.api.tools.core.ToolInvocation;
import treepeater.api.tools.core.ToolLabelContext;
import treepeater.api.tools.core.ToolResults;

public final class BatchHttpTargetToolsTool extends AbstractEditorTool {

    public static final String NAME = "batch_http_target_tools";

    public static final int MAX_TOOLS = 24;

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String SCHEMA =
            "{\"type\":\"object\",\"required\":[\"tools\"],\"properties\":{\"tools\":{\"type\":\"array\",\"minItems\":1,\"maxItems\":"
                    + MAX_TOOLS
                    + ",\"items\":{\"type\":\"object\",\"required\":[\"tool_name\"],\"properties\":{\"tool_name\":{\"type\":\"string\",\"minLength\":1},\"arguments\":{\"type\":\"object\"}},\"additionalProperties\":false}}},\"additionalProperties\":false}";

    /** Stable id for nested tool approval cards when a step runs inside a batch. */
    public static String syntheticChildToolCallId(String parentToolCallId, int batchSlot) {
        String base =
                parentToolCallId != null && !parentToolCallId.isBlank() ? parentToolCallId.trim() : "tool";
        return base + ":batch:" + batchSlot;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Runs several built-in tools in fixed order in a single call; each tools[] entry is "
                + "tool_name plus an arguments object ({} if none); returns per-step results. "
                + "Strongly prefer this whenever your plan needs more than one tool on the same turn—"
                + "especially ordered flows such as changing the request, send_current_http_request, "
                + "then read_http_message with side \"response\". Steps run one after another; write/send "
                + "still need approval.";
    }

    @Override
    public String inputSchemaJson() {
        return SCHEMA;
    }

    @Override
    public ToolActionLevel actionLevel() {
        return ToolActionLevel.READ_ONLY;
    }

    @Override
    public String invoke(ToolInvocation inv) throws Exception {
        JsonNode args = inv.args();
        JsonNode toolsNode = args.get("tools");
        if (toolsNode == null || !toolsNode.isArray()) {
            return ToolResults.errorJson("tools array required");
        }
        int n = toolsNode.size();
        if (n == 0 || n > MAX_TOOLS) {
            return ToolResults.errorJson("tools must have 1.." + MAX_TOOLS + " entries");
        }
        NestedToolInvoker nested = inv.nestedInvoker();
        TreepeaterToolRegistry registry = inv.runtime().registry();

        ArrayNode out = JSON.createArrayNode();
        for (int i = 0; i < n; i++) {
            JsonNode item = toolsNode.get(i);
            ObjectNode row = JSON.createObjectNode();
            row.put("index", i);
            if (item == null || !item.isObject()) {
                row.put("error", "step must be an object");
                out.add(row);
                continue;
            }
            JsonNode nameNode = argFirst(item, "tool_name", "toolName", "name");
            String innerName = nameNode != null && nameNode.isTextual() ? nameNode.asText().trim() : "";
            row.put("tool_name", innerName);
            if (innerName.isEmpty()) {
                row.put("error", "tool_name required");
                out.add(row);
                continue;
            }
            JsonNode argObj = argFirst(item, "arguments", "tool_arguments", "toolArguments");
            if (argObj == null || argObj.isNull()) {
                argObj = JSON.createObjectNode();
            } else if (!argObj.isObject()) {
                row.put("error", "arguments must be a JSON object");
                out.add(row);
                continue;
            }
            String innerArgs = JSON.writeValueAsString(argObj);
            String innerResult;
            try {
                innerResult =
                        nested != null
                                ? nested.invoke(innerName, innerArgs)
                                : registry.executeForChat(new ChatToolInvokeContext(innerName, innerArgs));
            } catch (Exception e) {
                row.put("error", e.getMessage() != null ? e.getMessage() : "tool error");
                out.add(row);
                continue;
            }
            row.set("result", parseToolResultJson(innerResult));
            out.add(row);
        }
        ObjectNode wrap = JSON.createObjectNode();
        wrap.set("results", out);
        return capResult(JSON.writeValueAsString(wrap));
    }

    @Override
    public HumanToolUsage humanLabel(ToolInvocation inv, ToolLabelContext labelCtx) {
        JsonNode toolsNode = HttpToolLabels.firstArg(inv.args(), "tools");
        int steps = toolsNode != null && toolsNode.isArray() ? toolsNode.size() : 0;
        return new HumanToolUsage("Run batched tools · " + steps + " step(s)", "");
    }

    private static JsonNode parseToolResultJson(String raw) {
        if (raw == null) {
            return JSON.nullNode();
        }
        try {
            return JSON.readTree(raw);
        } catch (Exception e) {
            ObjectNode o = JSON.createObjectNode();
            o.put("raw_text", raw);
            return o;
        }
    }

    private static JsonNode argFirst(JsonNode args, String... keys) {
        if (args == null || !args.isObject()) {
            return null;
        }
        for (String k : keys) {
            if (args.has(k) && !args.get(k).isNull()) {
                return args.get(k);
            }
        }
        return null;
    }
}
