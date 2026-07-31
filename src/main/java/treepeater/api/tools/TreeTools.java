package treepeater.api.tools;

import java.util.OptionalInt;

import com.fasterxml.jackson.databind.JsonNode;

import treepeater.ai.ToolActionLevel;
import treepeater.api.Json;
import treepeater.api.TreepeaterService;
import treepeater.api.TreepeaterTool;
import treepeater.api.TreepeaterToolRegistry;

/** Browsing and restructuring the Treepeater tree: folders, names, statuses, and notes. */
public final class TreeTools {

    public static final String LIST_TREE = "list_tree";
    public static final String GET_TREE_NODE = "get_tree_node";
    public static final String CREATE_FOLDER = "create_folder";
    public static final String RENAME_NODE = "rename_node";
    public static final String MOVE_NODE = "move_node";
    public static final String DELETE_NODE = "delete_node";
    public static final String SET_NODE_STATUS = "set_node_status";
    public static final String GET_NODE_NOTES = "get_node_notes";
    public static final String SET_NODE_NOTES = "set_node_notes";

    private static final String LIST_TREE_SCHEMA =
            """
            {"type":"object","properties":{\
            "node_id":{"type":"integer","description":"Subtree root; 0 or omitted for the whole tree"},\
            "max_depth":{"type":"integer","description":"Levels to include, 1-32 (default 32)"},\
            "include_requests":{"type":"boolean","description":"Include request leaves (default true)"}},\
            "additionalProperties":false}""";

    private static final String NODE_ID_SCHEMA =
            """
            {"type":"object","properties":{\
            "node_id":{"type":"integer","description":"Tree node id"}},\
            "required":["node_id"],"additionalProperties":false}""";

    private static final String CREATE_FOLDER_SCHEMA =
            """
            {"type":"object","properties":{\
            "parent_id":{"type":"integer","description":"Parent folder id; 0 or omitted for the tree root"},\
            "name":{"type":"string","description":"Folder name (default \\"New Folder\\")"}},\
            "additionalProperties":false}""";

    private static final String RENAME_NODE_SCHEMA =
            """
            {"type":"object","properties":{\
            "node_id":{"type":"integer"},\
            "name":{"type":"string","description":"New display name"}},\
            "required":["node_id","name"],"additionalProperties":false}""";

    private static final String MOVE_NODE_SCHEMA =
            """
            {"type":"object","properties":{\
            "node_id":{"type":"integer","description":"Node to move"},\
            "parent_id":{"type":"integer","description":"Destination folder id; 0 for the tree root"},\
            "index":{"type":"integer","description":"Position among the destination's children; omitted appends"}},\
            "required":["node_id","parent_id"],"additionalProperties":false}""";

    private static final String SET_NODE_STATUS_SCHEMA =
            """
            {"type":"object","properties":{\
            "node_id":{"type":"integer"},\
            "status_id":{"type":"string","description":"Status id from list_statuses"}},\
            "required":["node_id","status_id"],"additionalProperties":false}""";

    private static final String SET_NODE_NOTES_SCHEMA =
            """
            {"type":"object","properties":{\
            "node_id":{"type":"integer","description":"Request node id; folders have no notes"},\
            "notes":{"type":"string","description":"Replacement notes text; empty string clears them"}},\
            "required":["node_id","notes"],"additionalProperties":false}""";

    private TreeTools() {}

    public static void register(TreepeaterToolRegistry registry, TreepeaterService service) {
        registry.add(
                new TreepeaterTool(
                        LIST_TREE,
                        "Treepeater tree as nested JSON. Each node has id, type folder|request, name, "
                                + "status_id, parent_id and path; requests also carry method and url. Use node_id "
                                + "to scope to a subtree and max_depth to limit size.",
                        LIST_TREE_SCHEMA,
                        ToolActionLevel.READ_ONLY,
                        args -> withArgs(args, parsed -> {
                            int nodeId = Json.integer(parsed, "node_id", "nodeId").orElse(0);
                            int maxDepth = Json.integer(parsed, "max_depth", "maxDepth").orElse(0);
                            boolean includeRequests =
                                    Json.bool(parsed, "include_requests", "includeRequests").orElse(Boolean.TRUE);
                            return service.treeJson(nodeId, maxDepth, includeRequests);
                        })));

        registry.add(
                new TreepeaterTool(
                        GET_TREE_NODE,
                        "One tree node in detail: name, status, path, and for requests the method, url, "
                                + "notes and send-history size. Immediate children are included for folders.",
                        NODE_ID_SCHEMA,
                        ToolActionLevel.READ_ONLY,
                        args -> withNodeId(args, service::nodeJson)));

        registry.add(
                new TreepeaterTool(
                        GET_NODE_NOTES,
                        "Notes attached to a request node.",
                        NODE_ID_SCHEMA,
                        ToolActionLevel.READ_ONLY,
                        args -> withNodeId(args, service::notesJson)));

        registry.add(
                new TreepeaterTool(
                        CREATE_FOLDER,
                        "Creates a folder under parent_id (0 for the tree root) and returns its node id, "
                                + "which can then be used as the import destination.",
                        CREATE_FOLDER_SCHEMA,
                        ToolActionLevel.WRITE,
                        args -> withArgs(args, parsed -> {
                            int parentId = Json.integer(parsed, "parent_id", "parentId").orElse(0);
                            return service.createFolder(parentId, Json.text(parsed, "name"));
                        })));

        registry.add(
                new TreepeaterTool(
                        RENAME_NODE,
                        "Renames a folder or request node.",
                        RENAME_NODE_SCHEMA,
                        ToolActionLevel.WRITE,
                        args -> withArgs(args, parsed -> {
                            OptionalInt nodeId = Json.integer(parsed, "node_id", "nodeId");
                            if (nodeId.isEmpty()) {
                                return Json.error("node_id required");
                            }
                            return service.renameNode(nodeId.getAsInt(), Json.text(parsed, "name"));
                        })));

        registry.add(
                new TreepeaterTool(
                        MOVE_NODE,
                        "Reparents a node under another folder. Moving a node into its own subtree is "
                                + "rejected. Open tabs for the node stay open.",
                        MOVE_NODE_SCHEMA,
                        ToolActionLevel.WRITE,
                        args -> withArgs(args, parsed -> {
                            OptionalInt nodeId = Json.integer(parsed, "node_id", "nodeId");
                            OptionalInt parentId = Json.integer(parsed, "parent_id", "parentId");
                            if (nodeId.isEmpty() || parentId.isEmpty()) {
                                return Json.error("node_id and parent_id required");
                            }
                            int index = Json.integer(parsed, "index").orElse(-1);
                            return service.moveNode(nodeId.getAsInt(), parentId.getAsInt(), index);
                        })));

        registry.add(
                new TreepeaterTool(
                        SET_NODE_STATUS,
                        "Sets the status of a folder or request node. Valid ids come from list_statuses.",
                        SET_NODE_STATUS_SCHEMA,
                        ToolActionLevel.WRITE,
                        args -> withArgs(args, parsed -> {
                            OptionalInt nodeId = Json.integer(parsed, "node_id", "nodeId");
                            if (nodeId.isEmpty()) {
                                return Json.error("node_id required");
                            }
                            return service.setNodeStatus(nodeId.getAsInt(), Json.text(parsed, "status_id", "statusId"));
                        })));

        registry.add(
                new TreepeaterTool(
                        SET_NODE_NOTES,
                        "Replaces the notes on a request node.",
                        SET_NODE_NOTES_SCHEMA,
                        ToolActionLevel.WRITE,
                        args -> withArgs(args, parsed -> {
                            OptionalInt nodeId = Json.integer(parsed, "node_id", "nodeId");
                            if (nodeId.isEmpty()) {
                                return Json.error("node_id required");
                            }
                            JsonNode notes = Json.first(parsed, "notes");
                            String text = notes != null && notes.isTextual() ? notes.asText() : "";
                            return service.setNotes(nodeId.getAsInt(), text);
                        })));

        registry.add(
                new TreepeaterTool(
                        DELETE_NODE,
                        "Deletes a node and its whole subtree, closing any open tabs it contains. "
                                + "This cannot be undone.",
                        NODE_ID_SCHEMA,
                        ToolActionLevel.WRITE,
                        args -> withNodeId(args, service::deleteNode)));
    }

    /** @return label for a tree tool, or {@code null} when {@code toolName} is not handled here */
    public static HumanToolUsage humanToolUsage(String toolName, String argumentsJson) {
        JsonNode args = Json.readArgs(argumentsJson);
        if (args == null) {
            args = Json.obj();
        }
        return switch (toolName) {
            case LIST_TREE -> {
                int nodeId = Json.integer(args, "node_id", "nodeId").orElse(0);
                int maxDepth = Json.integer(args, "max_depth", "maxDepth").orElse(0);
                StringBuilder det = new StringBuilder();
                if (nodeId > 0) {
                    det.append("subtree from node ").append(nodeId);
                }
                if (maxDepth > 0) {
                    if (!det.isEmpty()) {
                        det.append(" · ");
                    }
                    det.append("max depth ").append(maxDepth);
                }
                yield new HumanToolUsage("List tree", det.toString());
            }
            case GET_TREE_NODE -> new HumanToolUsage("Get tree node" + nodeIdSuffix(args), "");
            case GET_NODE_NOTES -> new HumanToolUsage("Get node notes" + nodeIdSuffix(args), "");
            case CREATE_FOLDER -> {
                int parentId = Json.integer(args, "parent_id", "parentId").orElse(0);
                String name = Json.text(args, "name");
                StringBuilder det = new StringBuilder();
                if (name != null && !name.isBlank()) {
                    det.append(ToolHumanUsage.quotedSnippet(name, 80));
                }
                if (parentId > 0) {
                    if (!det.isEmpty()) {
                        det.append(" · ");
                    }
                    det.append("parent id ").append(parentId);
                }
                yield new HumanToolUsage("Create folder", det.toString());
            }
            case RENAME_NODE -> {
                String name = Json.text(args, "name");
                String det = name != null && !name.isBlank() ? ToolHumanUsage.quotedSnippet(name, 80) : "";
                yield new HumanToolUsage("Rename node" + nodeIdSuffix(args), det);
            }
            case MOVE_NODE -> {
                OptionalInt nodeId = Json.integer(args, "node_id", "nodeId", "id");
                OptionalInt parentId = Json.integer(args, "parent_id", "parentId");
                OptionalInt index = Json.integer(args, "index");
                StringBuilder det = new StringBuilder();
                if (parentId.isPresent()) {
                    det.append("parent id ").append(parentId.getAsInt());
                }
                if (index.isPresent() && index.getAsInt() >= 0) {
                    if (!det.isEmpty()) {
                        det.append(" · ");
                    }
                    det.append("index ").append(index.getAsInt());
                }
                String title =
                        nodeId.isPresent()
                                ? "Move node · id " + nodeId.getAsInt()
                                : "Move node";
                yield new HumanToolUsage(title, det.toString());
            }
            case SET_NODE_STATUS -> {
                String statusId = Json.text(args, "status_id", "statusId");
                String det =
                        statusId != null && !statusId.isBlank()
                                ? ToolHumanUsage.quotedSnippet(statusId, 80)
                                : "";
                yield new HumanToolUsage("Set node status" + nodeIdSuffix(args), det);
            }
            case SET_NODE_NOTES -> new HumanToolUsage("Set node notes" + nodeIdSuffix(args), "");
            case DELETE_NODE ->
                    new HumanToolUsage(
                            "Delete node" + nodeIdSuffix(args),
                            "Deletes the node and its whole subtree; cannot be undone");
            default -> null;
        };
    }

    private static String nodeIdSuffix(JsonNode args) {
        OptionalInt nodeId = Json.integer(args, "node_id", "nodeId", "id");
        return nodeId.isPresent() ? " · id " + nodeId.getAsInt() : "";
    }

    /** Parses arguments once and reports a consistent error for malformed JSON. */
    static String withArgs(String argumentsJson, ArgsHandler handler) {
        JsonNode parsed = Json.readArgs(argumentsJson);
        if (parsed == null) {
            return Json.error("arguments must be a JSON object");
        }
        return handler.handle(parsed);
    }

    private static String withNodeId(String argumentsJson, java.util.function.IntFunction<String> handler) {
        return withArgs(argumentsJson, parsed -> {
            OptionalInt nodeId = Json.integer(parsed, "node_id", "nodeId", "id");
            if (nodeId.isEmpty()) {
                return Json.error("node_id required");
            }
            return handler.apply(nodeId.getAsInt());
        });
    }

    @FunctionalInterface
    interface ArgsHandler {
        String handle(JsonNode args);
    }
}
