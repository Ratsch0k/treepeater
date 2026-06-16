package treepeater.ai;

/**
 * One open repeater tab offered in the chat composer {@code @}-mention popup.
 *
 * @param requestNodeId stable id for {@code request_node_id} on HTTP tools
 * @param pathLabel slash-separated path from the tree root (excluding the synthetic id-0 root)
 */
public record AgentTabMention(int requestNodeId, String pathLabel) {

}
