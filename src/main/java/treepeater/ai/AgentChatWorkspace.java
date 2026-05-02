package treepeater.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * All agent sub-tabs and UI indices for a single {@link treepeater.tree.RequestTreeNode}.
 */
public record AgentChatWorkspace(
        List<AgentChatSession> sessions,
        int selectedSessionIndex,
        int nextChatTabIndex) {
    public static final AgentChatWorkspace EMPTY = new AgentChatWorkspace(List.of(), 0, 1);

    public AgentChatWorkspace {
        if (sessions == null) {
            sessions = List.of();
        } else {
            sessions = List.copyOf(sessions);
        }
        if (nextChatTabIndex < 1) {
            nextChatTabIndex = 1;
        }
        if (selectedSessionIndex < 0) {
            selectedSessionIndex = 0;
        }
        if (!sessions.isEmpty() && selectedSessionIndex >= sessions.size()) {
            selectedSessionIndex = sessions.size() - 1;
        }
    }

    public AgentChatWorkspace copy() {
        List<AgentChatSession> copyList = new ArrayList<>(this.sessions.size());
        for (AgentChatSession s : this.sessions) {
            copyList.add(s.copy());
        }
        return new AgentChatWorkspace(copyList, this.selectedSessionIndex, this.nextChatTabIndex);
    }
}
