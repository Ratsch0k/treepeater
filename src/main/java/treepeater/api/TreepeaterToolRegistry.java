package treepeater.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import treepeater.ai.ChatToolDefinition;
import treepeater.ai.ChatToolInvokeContext;
import treepeater.ai.NestedToolInvoker;
import treepeater.ai.TreepeaterTabAgentBridge;
import treepeater.ai.ToolActionLevel;
import treepeater.api.tools.core.ToolInvocation;
import treepeater.api.tools.core.ToolLabelContext;
import treepeater.api.tools.core.ToolRuntime;
import treepeater.api.tools.core.TreepeaterToolAdapter;
import treepeater.api.tools.core.TreepeaterToolSpec;
import treepeater.api.tools.http.HttpTargetToolModule;
import treepeater.api.tools.importing.ImportToolModule;
import treepeater.api.tools.status.StatusToolModule;
import treepeater.api.tools.tree.TreeToolModule;
import treepeater.api.tools.HumanToolUsage;

/**
 * Single source of truth for callable Treepeater operations, consumed by the AI chat panel, the REST API,
 * and the MCP endpoint.
 */
public final class TreepeaterToolRegistry {

    private final Map<String, TreepeaterTool> tools = new LinkedHashMap<>();
    private final Map<String, TreepeaterToolSpec> specs = new LinkedHashMap<>();
    private final ToolRuntime runtime;
    private final TreepeaterTabAgentBridge bridge;

    private TreepeaterToolRegistry(TreepeaterTabAgentBridge bridge, TreepeaterService service) {
        this.bridge = bridge;
        this.runtime = new ToolRuntime(service, bridge, this);
    }

    /**
     * @param bridge resolves the UI-selected or a specific repeater tab for the built-in editor tools;
     *     when {@code null} those tools are omitted and only the headless tool groups are exposed
     */
    public static TreepeaterToolRegistry create(TreepeaterService service, TreepeaterTabAgentBridge bridge) {
        TreepeaterToolRegistry registry = new TreepeaterToolRegistry(bridge, service);
        if (bridge != null) {
            new HttpTargetToolModule().register(registry, registry.runtime);
        }
        new TreeToolModule().register(registry, registry.runtime);
        new StatusToolModule().register(registry, registry.runtime);
        new ImportToolModule().register(registry, registry.runtime);
        return registry;
    }

    /** Registry with only editor tools; used before the full registry is initialized. */
    public static TreepeaterToolRegistry createEditorOnly(TreepeaterTabAgentBridge bridge) {
        TreepeaterToolRegistry registry = new TreepeaterToolRegistry(bridge, null);
        new HttpTargetToolModule().register(registry, registry.runtime);
        return registry;
    }

    /** Adds a tool spec, rejecting duplicate names. */
    public void addSpec(TreepeaterToolSpec spec, ToolRuntime runtime) {
        if (spec == null) {
            return;
        }
        if (this.specs.putIfAbsent(spec.name(), spec) != null) {
            throw new IllegalStateException("duplicate tool name: " + spec.name());
        }
        add(TreepeaterToolAdapter.toRecord(spec, runtime));
    }

    /** Adds a wire-level tool record derived from a spec. */
    private void add(TreepeaterTool tool) {
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

    public TreepeaterToolSpec findSpec(String name) {
        return name != null ? this.specs.get(name) : null;
    }

    public ToolActionLevel actionLevelFor(String name) {
        TreepeaterToolSpec spec = findSpec(name);
        return spec != null ? spec.actionLevel() : null;
    }

    /** Declarations for the AI chat clients. */
    public List<ChatToolDefinition> chatToolDefinitions() {
        List<ChatToolDefinition> out = new ArrayList<>(this.tools.size());
        for (TreepeaterTool tool : this.tools.values()) {
            out.add(new ChatToolDefinition(tool.name(), tool.description(), tool.inputSchemaJson()));
        }
        return out;
    }

    /** Chat dispatch with nested-tool support for batch operations. */
    public String executeForChat(ChatToolInvokeContext context) {
        return invokeSpec(context.toolName(), context.argumentsJson(), context.invokeChildWithApproval());
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
        return invokeSpec(name, argumentsJson, null);
    }

    /** Human-readable label for chat approval cards. */
    public HumanToolUsage humanLabelFor(String toolName, String argumentsJson, ToolLabelContext labelCtx) {
        TreepeaterToolSpec spec = findSpec(toolName);
        if (spec == null) {
            return new HumanToolUsage("Working…", "");
        }
        ToolInvocation inv =
                TreepeaterToolAdapter.buildInvocation(spec, argumentsJson, this.runtime, null);
        return spec.humanLabel(inv, labelCtx);
    }

    private String invokeSpec(String name, String argumentsJson, NestedToolInvoker nested) {
        TreepeaterToolSpec spec = findSpec(name);
        if (spec == null) {
            return Json.error("unknown tool: " + name);
        }
        try {
            ToolInvocation inv =
                    TreepeaterToolAdapter.buildInvocation(spec, argumentsJson, this.runtime, nested);
            if (inv.args() == null) {
                return Json.error("arguments must be a JSON object");
            }
            String result = spec.invoke(inv);
            return result != null ? result : Json.error("tool returned no result");
        } catch (Exception e) {
            String message = e.getMessage();
            return Json.error(message != null && !message.isBlank() ? message : e.getClass().getSimpleName());
        }
    }
}
