package treepeater.ai;

import java.util.OptionalInt;

import treepeater.TreepeaterModel.SiblingCopyPlacement;

/**
 * Resolves {@link AgentToolContext} for the UI-selected tab or a specific open repeater tab, and runs
 * paginated tab discovery for {@link HttpTargetTools#SEARCH_TABS}.
 */
public interface RepeaterTabAgentBridge {

    /**
     * @param requestNodeId empty for the UI-selected repeater tab; otherwise {@link treepeater.tree.RequestTreeNode#getId()}
     * @return context or {@code null} if the tab is missing or cannot be built
     */
    AgentToolContext contextForAgent(OptionalInt requestNodeId);

    /**
     * Paginated, optionally filtered list of open repeater tabs (JSON). Implementations must run Swing work on the EDT.
     *
     * @param queryOrNull when non-null and non-blank, filter tabs; when null or blank, all open tabs
     */
    String searchTabs(int offset, int pageSize, String queryOrNull);

    /**
     * Duplicates a request tree node as a sibling with the given name. Implementations must run Swing work on the EDT.
     *
     * @return JSON with {@code request_node_id} and {@code name}, or {@code {"error":...}}
     */
    default String copyTreepeaterNode(int sourceRequestNodeId, String name, SiblingCopyPlacement placement) {
        return "{\"error\":\"copy_treepeater_node requires a full RepeaterTabAgentBridge\"}";
    }

    /**
     * UI-selected repeater tab id for tool card titles; {@link Integer#MIN_VALUE} if unknown. Default: unknown.
     */
    default int uiSelectedRequestNodeIdForToolCard() {
        return Integer.MIN_VALUE;
    }

    /**
     * Bridge that always returns the same context and does not support {@link HttpTargetTools#SEARCH_TABS}.
     */
    static RepeaterTabAgentBridge singleTab(AgentToolContext ctx) {
        return new RepeaterTabAgentBridge() {
            @Override
            public AgentToolContext contextForAgent(OptionalInt requestNodeId) {
                return ctx;
            }

            @Override
            public String searchTabs(int offset, int pageSize, String queryOrNull) {
                return "{\"error\":\"search_tabs requires a full RepeaterTabAgentBridge\"}";
            }
        };
    }
}
