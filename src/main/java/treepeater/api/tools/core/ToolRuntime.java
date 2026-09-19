package treepeater.api.tools.core;

import treepeater.ai.TreepeaterTabAgentBridge;
import treepeater.api.TreepeaterService;
import treepeater.api.TreepeaterToolRegistry;

/** Dependencies injected once at registry build time. */
public record ToolRuntime(
        TreepeaterService service,
        TreepeaterTabAgentBridge bridge,
        TreepeaterToolRegistry registry) {}
