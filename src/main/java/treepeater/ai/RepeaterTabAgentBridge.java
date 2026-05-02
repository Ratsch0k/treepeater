package treepeater.ai;

import java.util.OptionalInt;

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
