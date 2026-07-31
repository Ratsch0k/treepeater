package treepeater.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import treepeater.ai.ChatToolDefinition;
import treepeater.ai.ChatToolInvokeContext;
import treepeater.ai.RepeaterTabAgentBridge;
import treepeater.api.tools.HttpTargetTools;
import treepeater.api.tools.ImportTools;
import treepeater.api.tools.StatusTools;
import treepeater.api.tools.TreeTools;

/**
 * Single source of truth for callable Treepeater operations, consumed by the AI chat panel, the REST API,
 * and the MCP endpoint. All tool groups live under {@code treepeater.api.tools}.
 */
public final class TreepeaterToolRegistry {

    private final Map<String, TreepeaterTool> tools = new LinkedHashMap<>();
    private final RepeaterTabAgentBridge bridge;

    private TreepeaterToolRegistry(RepeaterTabAgentBridge bridge) {
        this.bridge = bridge;
    }

    /**
     * @param bridge resolves the UI-selected or a specific repeater tab for the built-in editor tools;
     *     when {@code null} those tools are omitted and only the headless tool groups are exposed
     */
    public static TreepeaterToolRegistry create(TreepeaterService service, RepeaterTabAgentBridge bridge) {
        TreepeaterToolRegistry registry = new TreepeaterToolRegistry(bridge);
        if (bridge != null) {
            HttpTargetTools.register(registry, bridge);
        }
        TreeTools.register(registry, service);
        StatusTools.register(registry, service);
        ImportTools.register(registry, service);
        return registry;
    }

    /** Registry with only editor tools; used before the full registry is initialized. */
    public static TreepeaterToolRegistry createEditorOnly(RepeaterTabAgentBridge bridge) {
        TreepeaterToolRegistry registry = new TreepeaterToolRegistry(bridge);
        HttpTargetTools.register(registry, bridge);
        return registry;
    }

    /** Adds a tool, rejecting duplicate names so a typo cannot silently shadow an existing tool. */
    public void add(TreepeaterTool tool) {
        if (tool == null) {
            return;
        }
        if (this.tools.putIfAbsent(tool.name(), tool) != null) {
            throw new IllegalStateException("duplicate tool name: " + tool.name());
        }
    }

    public List<TreepeaterTool> tools() {
        return List.copyOf(this.tools.values());
    }

    public TreepeaterTool find(String name) {
        return name != null ? this.tools.get(name) : null;
    }

    /** Declarations for the AI chat clients. */
    public List<ChatToolDefinition> chatToolDefinitions() {
        List<ChatToolDefinition> out = new ArrayList<>(this.tools.size());
        for (TreepeaterTool tool : this.tools.values()) {
            out.add(new ChatToolDefinition(tool.name(), tool.description(), tool.inputSchemaJson()));
        }
        return out;
    }

    /**
     * Chat dispatch. Built-in tools go through {@link HttpTargetTools#execute(ChatToolInvokeContext,
     * RepeaterTabAgentBridge)} so {@link HttpTargetTools#BATCH_HTTP_TARGET_TOOLS} can still run its
     * children through the per-tool approval flow.
     */
    public String executeForChat(ChatToolInvokeContext context) {
        String name = context.toolName();
        if (this.bridge != null && HttpTargetTools.toolActionLevel(name) != null) {
            return HttpTargetTools.execute(context, this.bridge);
        }
        return invoke(name, context.argumentsJson());
    }

    /** External dispatch with policy gating; used by both the REST and MCP fronts. */
    public String execute(String name, String argumentsJson, ApiPolicy policy) {
        TreepeaterTool tool = find(name);
        if (tool == null) {
            return Json.error("unknown tool: " + name);
        }
        ApiPolicy effective = policy != null ? policy : ApiPolicy.readOnly();
        if (!effective.allows(name, tool.actionLevel())) {
            return ApiPolicy.deniedResult(name);
        }
        return invoke(name, argumentsJson);
    }

    private String invoke(String name, String argumentsJson) {
        TreepeaterTool tool = find(name);
        if (tool == null) {
            return Json.error("unknown tool: " + name);
        }
        try {
            String result = tool.handler().invoke(argumentsJson);
            return result != null ? result : Json.error("tool returned no result");
        } catch (Exception e) {
            String message = e.getMessage();
            return Json.error(message != null && !message.isBlank() ? message : e.getClass().getSimpleName());
        }
    }
}
