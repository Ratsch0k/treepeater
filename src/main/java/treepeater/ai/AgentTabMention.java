package treepeater.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import treepeater.tree.RequestTreeNode;
import treepeater.tree.TreepeaterNode;

/**
 * One open repeater tab offered in the chat composer {@code @}-mention popup.
 *
 * @param requestNodeId stable id for {@code request_node_id} on HTTP tools
 * @param pathLabel slash-separated path from the tree root (excluding the synthetic id-0 root)
 */
public record AgentTabMention(int requestNodeId, String pathLabel) {

    /** Synthetic tree root in {@link treepeater.tree.RequestTree}; not a real request tab. */
    private static final int SYNTHETIC_ROOT_ID = 0;

    public static String slashPathForNode(RequestTreeNode node) {
        if (node == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (TreepeaterNode cur = node; cur != null; ) {
            if (cur.getId() == SYNTHETIC_ROOT_ID) {
                break;
            }
            String name = cur.getName();
            parts.add(name != null ? name : "#" + cur.getId());
            TreepeaterNode p = cur.getParent() instanceof TreepeaterNode tn ? tn : null;
            if (p == null) {
                break;
            }
            cur = p;
        }
        Collections.reverse(parts);
        return String.join("/", parts);
    }
}
