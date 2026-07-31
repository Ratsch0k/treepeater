package treepeater.api;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultTreeModel;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import burp.api.montoya.http.message.HttpRequestResponse;
import treepeater.Treepeater;
import treepeater.TreepeaterModel;
import treepeater.importing.ImportOptions;
import treepeater.requestResponse.RequestDescriptions;
import treepeater.requestResponse.Status;
import treepeater.settings.StatusRegistry;
import treepeater.tree.FolderTreeNode;
import treepeater.tree.RequestTreeNode;
import treepeater.tree.TreepeaterNode;

/**
 * Headless facade over {@link TreepeaterModel} for callers that are not on the Swing event dispatch
 * thread. The model has no thread safety and assumes EDT callers, so every operation here marshals with
 * {@link SwingUtilities#invokeAndWait} using the same guard as
 * {@link treepeater.TreepeaterUI#contextForAgent}, and returns a JSON string rather than live nodes so
 * results can cross the thread boundary safely.
 */
public final class TreepeaterService {

    /** Depth cap for {@code list_tree} so a large project cannot produce an unbounded document. */
    public static final int MAX_TREE_DEPTH = 32;

    private final TreepeaterModel model;

    public TreepeaterService(TreepeaterModel model) {
        if (model == null) {
            throw new IllegalArgumentException("model required");
        }
        this.model = model;
    }

    public TreepeaterModel model() {
        return this.model;
    }

    /** Tree rooted at {@code rootId} (0 for the whole tree) serialized to {@code maxDepth} levels. */
    public String treeJson(int rootId, int maxDepth, boolean includeRequests) {
        int depth = maxDepth <= 0 ? MAX_TREE_DEPTH : Math.min(maxDepth, MAX_TREE_DEPTH);
        return onEdt(() -> {
            TreepeaterNode start = rootId == 0 ? root() : requireNode(rootId);
            ObjectNode out = Json.obj();
            out.set("tree", nodeJson(start, depth, false, includeRequests));
            out.put("total_nodes", countSubtree(start));
            out.put("max_depth", depth);
            return Json.nodeToString(out);
        });
    }

    public String nodeJson(int id) {
        return onEdt(() -> Json.nodeToString(nodeJson(requireNode(id), 1, true, true)));
    }

    public String statusesJson() {
        return onEdt(() -> {
            ArrayNode list = Json.arr();
            StatusRegistry registry = Treepeater.getStatusRegistry();
            List<Status> all = registry != null ? registry.getAll() : List.of();
            for (Status status : all) {
                ObjectNode row = Json.obj();
                row.put("id", status.getId());
                row.put("name", status.getStatus());
                list.add(row);
            }
            ObjectNode out = Json.obj();
            out.set("statuses", list);
            out.put("default_id", StatusRegistry.getDefault().getId());
            return Json.nodeToString(out);
        });
    }

    public String notesJson(int id) {
        return onEdt(() -> {
            TreepeaterNode node = requireNode(id);
            if (!(node instanceof RequestTreeNode request)) {
                throw new IllegalArgumentException("node " + id + " is a folder and has no notes");
            }
            ObjectNode out = Json.obj();
            out.put("id", id);
            out.put("notes", request.getNotes());
            return Json.nodeToString(out);
        });
    }

    public String createFolder(int parentId, String name) {
        return onEdt(() -> {
            TreepeaterNode parent = parentId == 0 ? root() : requireNode(parentId);
            if (!(parent instanceof FolderTreeNode)) {
                throw new IllegalArgumentException("node " + parentId + " is not a folder");
            }
            FolderTreeNode folder = this.model.createFolder(parent);
            if (folder == null) {
                throw new IllegalStateException("folder could not be created");
            }
            if (name != null && !name.isBlank()) {
                folder.setName(name.trim());
            }
            treeModel().nodeChanged(folder);
            return Json.nodeToString(nodeJson(folder, 0, false, true));
        });
    }

    public String renameNode(int id, String name) {
        if (name == null || name.isBlank()) {
            return Json.error("name required");
        }
        return onEdt(() -> {
            TreepeaterNode node = requireNode(id);
            node.setName(name.trim());
            treeModel().nodeChanged(node);
            return Json.nodeToString(nodeJson(node, 0, false, true));
        });
    }

    public String setNodeStatus(int id, String statusId) {
        if (statusId == null || statusId.isBlank()) {
            return Json.error("status_id required");
        }
        return onEdt(() -> {
            TreepeaterNode node = requireNode(id);
            StatusRegistry registry = Treepeater.getStatusRegistry();
            Status status = registry != null ? registry.getById(statusId.trim()) : null;
            if (status == null) {
                throw new IllegalArgumentException(
                        "unknown status '" + statusId.trim() + "'; call list_statuses for valid ids");
            }
            node.setStatus(status);
            treeModel().nodeChanged(node);
            Treepeater.saveState();
            return Json.nodeToString(nodeJson(node, 0, false, true));
        });
    }

    public String setNotes(int id, String notes) {
        return onEdt(() -> {
            TreepeaterNode node = requireNode(id);
            if (!(node instanceof RequestTreeNode request)) {
                throw new IllegalArgumentException("node " + id + " is a folder and has no notes");
            }
            request.setNotes(notes != null ? notes : "");
            ObjectNode out = Json.obj();
            out.put("id", id);
            out.put("notes", request.getNotes());
            return Json.nodeToString(out);
        });
    }

    /**
     * Reparents a node. Uses the tree model directly rather than
     * {@link TreepeaterModel#removeNodeFromTree} because that also closes open tabs and detaches
     * listeners, which is correct for a delete but would break a move.
     */
    public String moveNode(int id, int newParentId, int index) {
        return onEdt(() -> {
            TreepeaterNode node = requireNode(id);
            if (node == root()) {
                throw new IllegalArgumentException("the root cannot be moved");
            }
            TreepeaterNode parent = newParentId == 0 ? root() : requireNode(newParentId);
            if (!(parent instanceof FolderTreeNode)) {
                throw new IllegalArgumentException("node " + newParentId + " is not a folder");
            }
            if (node == parent || isAncestorOf(node, parent)) {
                throw new IllegalArgumentException("cannot move a node into itself or its own subtree");
            }
            treeModel().removeNodeFromParent(node);
            int target = index < 0 || index > parent.getChildCount() ? parent.getChildCount() : index;
            this.model.insertNodeInto(node, parent, target);
            return Json.nodeToString(nodeJson(node, 0, false, true));
        });
    }

    public String deleteNode(int id) {
        return onEdt(() -> {
            TreepeaterNode node = requireNode(id);
            if (node == root()) {
                throw new IllegalArgumentException("the root cannot be deleted");
            }
            int removed = countSubtree(node);
            this.model.removeNodeFromTree(node);
            ObjectNode out = Json.obj();
            out.put("deleted_id", id);
            out.put("removed_nodes", removed);
            return Json.nodeToString(out);
        });
    }

    /**
     * Imports {@code requestResponse} under {@code folderId} (0 for the tree root) using
     * {@link TreepeaterModel#importRequestManual}, which handles direct and path-aware placement alike.
     */
    public String importRequest(int folderId, HttpRequestResponse requestResponse, ImportOptions options) {
        if (requestResponse == null || options == null) {
            return Json.error("request and options required");
        }
        return onEdt(() -> {
            TreepeaterNode folder = folderId == 0 ? root() : requireNode(folderId);
            if (!(folder instanceof FolderTreeNode destination)) {
                throw new IllegalArgumentException("node " + folderId + " is not a folder");
            }
            RequestTreeNode created = this.model.importRequestManual(destination, requestResponse, options);
            if (created == null) {
                throw new IllegalStateException("import produced no node");
            }
            ObjectNode out = (ObjectNode) nodeJson(created, 0, false, true);
            out.set("folder_path", pathArray(parentOf(created)));
            return Json.nodeToString(out);
        });
    }

    private ObjectNode nodeJson(
            TreepeaterNode node, int remainingDepth, boolean detail, boolean includeRequests) {
        ObjectNode out = Json.obj();
        out.put("id", node.getId());
        out.put("type", node instanceof FolderTreeNode ? "folder" : "request");
        out.put("name", node.getName() != null ? node.getName() : "#" + node.getId());
        Status status = node.getStatus();
        if (status != null) {
            out.put("status_id", status.getId());
            out.put("status_name", status.getStatus());
        }
        TreepeaterNode parent = parentOf(node);
        if (parent != null) {
            out.put("parent_id", parent.getId());
        }
        out.put("path", String.join("/", pathParts(node)));

        if (node instanceof RequestTreeNode request) {
            out.put("method", RequestDescriptions.method(request.getRequest()));
            out.put("url", RequestDescriptions.url(request.getRequest()));
            out.put("has_response", request.getResponse() != null);
            if (detail) {
                out.put("notes", request.getNotes());
                out.put("history_size", request.getHistory() != null ? request.getHistory().size() : 0);
            }
        } else {
            out.put("child_count", node.getChildCount());
            if (remainingDepth > 0) {
                ArrayNode children = Json.arr();
                for (int i = 0; i < node.getChildCount(); i++) {
                    TreepeaterNode child = (TreepeaterNode) node.getChildAt(i);
                    if (!includeRequests && child instanceof RequestTreeNode) {
                        continue;
                    }
                    children.add(nodeJson(child, remainingDepth - 1, false, includeRequests));
                }
                out.set("children", children);
            }
        }
        return out;
    }

    private ArrayNode pathArray(TreepeaterNode node) {
        ArrayNode out = Json.arr();
        if (node != null) {
            for (String part : pathParts(node)) {
                out.add(part);
            }
        }
        return out;
    }

    /** Names from the hidden root down to {@code node}, excluding the root itself. */
    private static List<String> pathParts(TreepeaterNode node) {
        List<String> parts = new ArrayList<>();
        TreepeaterNode current = node;
        while (current != null && current.getParent() != null) {
            String name = current.getName();
            parts.add(0, name != null ? name : "#" + current.getId());
            current = parentOf(current);
        }
        return parts;
    }

    private DefaultTreeModel treeModel() {
        return this.model.getTree().getTreeModel();
    }

    private FolderTreeNode root() {
        return (FolderTreeNode) treeModel().getRoot();
    }

    private static TreepeaterNode parentOf(TreepeaterNode node) {
        return node.getParent() instanceof TreepeaterNode parent ? parent : null;
    }

    private static boolean isAncestorOf(TreepeaterNode candidate, TreepeaterNode node) {
        TreepeaterNode current = parentOf(node);
        while (current != null) {
            if (current == candidate) {
                return true;
            }
            current = parentOf(current);
        }
        return false;
    }

    private static int countSubtree(TreepeaterNode node) {
        int total = 1;
        for (int i = 0; i < node.getChildCount(); i++) {
            total += countSubtree((TreepeaterNode) node.getChildAt(i));
        }
        return total;
    }

    /** Any node by id, including folders (the model only searches request leaves). */
    private TreepeaterNode requireNode(int id) {
        TreepeaterNode found = findNode(root(), id);
        if (found == null) {
            throw new IllegalArgumentException("no node with id " + id);
        }
        return found;
    }

    private static TreepeaterNode findNode(TreepeaterNode from, int id) {
        if (from.getId() == id) {
            return from;
        }
        for (int i = 0; i < from.getChildCount(); i++) {
            TreepeaterNode found = findNode((TreepeaterNode) from.getChildAt(i), id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Runs {@code work} on the EDT and converts any failure into an error result. Mirrors the guard in
     * {@link treepeater.TreepeaterUI#contextForAgent}: calling {@code invokeAndWait} from the EDT throws,
     * so run inline when already there.
     */
    private String onEdt(Supplier<String> work) {
        if (SwingUtilities.isEventDispatchThread()) {
            return callSafely(work);
        }
        final String[] holder = new String[1];
        try {
            SwingUtilities.invokeAndWait(() -> holder[0] = callSafely(work));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Json.error("interrupted");
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            return Json.error(
                    cause != null && cause.getMessage() != null ? cause.getMessage() : "operation failed");
        }
        return holder[0] != null ? holder[0] : Json.error("no result");
    }

    private static String callSafely(Supplier<String> work) {
        try {
            return work.get();
        } catch (Exception e) {
            String message = e.getMessage();
            return Json.error(message != null && !message.isBlank() ? message : e.getClass().getSimpleName());
        }
    }
}
