package treepeater;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.swing.tree.TreeNode;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import treepeater.ai.AgentChatWorkspace;
import treepeater.requestResponse.RequestHistory;
import treepeater.importing.ImportOptions;
import treepeater.importing.ImportOptions.DirectNameMode;
import treepeater.importing.ImportOptions.DirectPlacement;
import treepeater.importing.ImportOptions.PathAwarePlacement;
import treepeater.requestResponse.RequestDescriptions;
import treepeater.requestResponse.Status;
import treepeater.pathnormalization.DynamicPathNormalizer;
import treepeater.settings.StatusRegistry;
import treepeater.settings.TreepeaterSettings;
import treepeater.workspace.EditorWorkspace;
import treepeater.workspace.SplitOrientation;
import treepeater.workspace.TabGroupNode;

import treepeater.tree.FolderTreeNode;
import treepeater.tree.RequestTree;
import treepeater.tree.RequestTreeNode;
import treepeater.tree.TreepeaterNode;
import treepeater.tree.TreepeaterNodeListener;

public class TreepeaterModel implements TreepeaterNodeListener {
    private RequestTree tree;
    private EditorWorkspace workspace;
    private RequestTreeNode activeNode;
    private int requestCount;

    private String globalNotes = "";
    private AgentChatWorkspace globalAgentChatWorkspace = AgentChatWorkspace.EMPTY;

    private Set<TreepeaterModelListener> listeners;

    public TreepeaterModel(RequestTree tree, LinkedList<RequestTreeNode> tabs, RequestTreeNode activeNode, int requestCount) {
        this(tree, tabs, activeNode, requestCount, "", AgentChatWorkspace.EMPTY);
    }

    public TreepeaterModel(
            RequestTree tree,
            LinkedList<RequestTreeNode> tabs,
            RequestTreeNode activeNode,
            int requestCount,
            String globalNotes,
            AgentChatWorkspace globalAgentChatWorkspace) {
        this(
                tree,
                EditorWorkspace.fromLegacyTabs(tabs, activeNode),
                activeNode,
                requestCount,
                globalNotes,
                globalAgentChatWorkspace);
    }

    public TreepeaterModel(
            RequestTree tree,
            EditorWorkspace workspace,
            RequestTreeNode activeNode,
            int requestCount,
            String globalNotes,
            AgentChatWorkspace globalAgentChatWorkspace) {
        this.tree = tree;
        this.workspace = workspace != null ? workspace : new EditorWorkspace();
        this.activeNode = activeNode;
        this.requestCount = requestCount;
        this.globalNotes = globalNotes != null ? globalNotes : "";
        this.globalAgentChatWorkspace =
                globalAgentChatWorkspace != null ? globalAgentChatWorkspace : AgentChatWorkspace.EMPTY;
        this.listeners = new HashSet<>();

        this.listenToAllNodes((TreepeaterNode) this.tree.getTreeModel().getRoot());
    }

    public TreepeaterModel() {
        this.tree = new RequestTree();
        this.workspace = new EditorWorkspace();
        this.requestCount = 0;
        this.activeNode = null;
        this.globalNotes = "";
        this.globalAgentChatWorkspace = AgentChatWorkspace.EMPTY;
        this.listeners = new HashSet<>();
    }

    public String getGlobalNotes() {
        return this.globalNotes != null ? this.globalNotes : "";
    }

    public void setGlobalNotes(String notes) {
        String next = notes != null ? notes : "";
        if (next.equals(this.globalNotes)) {
            return;
        }
        this.globalNotes = next;
        Treepeater.saveState();
    }

    public AgentChatWorkspace getGlobalAgentChatWorkspace() {
        return this.globalAgentChatWorkspace != null ? this.globalAgentChatWorkspace : AgentChatWorkspace.EMPTY;
    }

    public void setGlobalAgentChatWorkspace(AgentChatWorkspace workspace) {
        AgentChatWorkspace next = workspace != null ? workspace : AgentChatWorkspace.EMPTY;
        if (next.equals(this.globalAgentChatWorkspace)) {
            return;
        }
        this.globalAgentChatWorkspace = next;
        Treepeater.saveState();
    }

    private void listenToAllNodes(TreepeaterNode node) {
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TreepeaterNode child = (TreepeaterNode) node.getChildAt(i);
            child.addListener(this);
            this.listenToAllNodes(child);
        }
        node.addListener(this);
    }

    public void addTab(RequestTreeNode node) {
        this.activeNode = node;

        if (this.workspace.isOpen(node)) {
            TabGroupNode group = this.workspace.findGroupContaining(node);
            String groupId = group != null ? group.id() : this.workspace.focusedTabGroupId();
            this.workspace.setFocusedTabGroupId(groupId);
            if (group != null) {
                group.setSelectedIndex(group.indexOf(node));
            }
            this.listeners.forEach(l -> l.onOpenTab(node, groupId));
        } else {
            this.workspace.addTabToFocused(node);
            String groupId = this.workspace.focusedTabGroupId();
            this.listeners.forEach(l -> l.onNewTab(node, groupId));
        }

        Treepeater.saveState();
    }

    public void removeTab(RequestTreeNode node) {
        if (node == null) {
            return;
        }

        if (node.equals(this.activeNode)) {
            TabGroupNode group = this.workspace.findGroupContaining(node);
            if (group != null) {
                int idx = group.indexOf(node);
                if (idx >= 0 && idx < group.tabs().size() - 1) {
                    this.activeNode = group.tabs().get(idx + 1);
                } else if (idx > 0) {
                    this.activeNode = group.tabs().get(idx - 1);
                } else {
                    this.activeNode = pickFallbackActiveNode(node);
                }
            } else {
                this.activeNode = pickFallbackActiveNode(node);
            }
        }

        boolean layoutChanged = this.workspace.removeTab(node);
        this.listeners.forEach(l -> l.onCloseTab(node));
        if (layoutChanged) {
            this.listeners.forEach(TreepeaterModelListener::onWorkspaceLayoutChanged);
        }
        Treepeater.saveState();
    }

    private RequestTreeNode pickFallbackActiveNode(RequestTreeNode excluding) {
        for (RequestTreeNode tab : this.workspace.allOpenTabs()) {
            if (!tab.equals(excluding)) {
                return tab;
            }
        }
        return null;
    }

    public void moveTab(RequestTreeNode node, String fromGroupId, String toGroupId, int dropIndex) {
        this.workspace.moveTab(node, fromGroupId, toGroupId, dropIndex);
        this.activeNode = node;
        this.listeners.forEach(l -> l.onTabMoved(node, fromGroupId, toGroupId, dropIndex));
        Treepeater.saveState();
    }

    public void splitTabGroup(String groupId, SplitOrientation orientation) {
        EditorWorkspace.SplitResult result = this.workspace.splitGroup(groupId, orientation);
        if (result == null) {
            return;
        }
        this.listeners.forEach(TreepeaterModelListener::onWorkspaceLayoutChanged);
        this.listeners.forEach(l -> l.onFocusedGroupChanged(this.workspace.focusedTabGroupId()));
        Treepeater.saveState();
    }

    public void setActiveNode(RequestTreeNode node) {
        if (node == null) {
            this.activeNode = null;
            return;
        }
        this.activeNode = node;
        TabGroupNode group = this.workspace.findGroupContaining(node);
        if (group != null) {
            group.setSelectedIndex(group.indexOf(node));
            this.workspace.setFocusedTabGroupId(group.id());
        }
        Treepeater.saveState();
    }

    public void setFocusedTabGroupId(String tabGroupId) {
        this.workspace.setFocusedTabGroupId(tabGroupId);
        this.listeners.forEach(l -> l.onFocusedGroupChanged(tabGroupId));
    }

    public EditorWorkspace getWorkspace() {
        return this.workspace;
    }

    public void removeNodeFromTree(TreepeaterNode node) {
        if (node == null) {
            return;
        }
        Object rootObj = this.tree.getTreeModel().getRoot();
        if (!(rootObj instanceof TreepeaterNode root)) {
            return;
        }
        if (node == root || node.getParent() == null) {
            return;
        }
        List<TreepeaterNode> subtree = new ArrayList<>();
        collectSubtreeNodes(node, subtree);
        Set<TreepeaterNode> removed = new HashSet<>(subtree);
        for (RequestTreeNode tab : new ArrayList<>(this.workspace.allOpenTabs())) {
            if (removed.contains(tab)) {
                this.removeTab(tab);
            }
        }
        for (TreepeaterNode n : subtree) {
            n.removeListener(this);
        }
        this.tree.getTreeModel().removeNodeFromParent(node);
        Treepeater.saveState();
        notifyTreeChanged();
    }

    private static void collectSubtreeNodes(TreepeaterNode n, List<TreepeaterNode> out) {
        out.add(n);
        for (int i = 0; i < n.getChildCount(); i++) {
            collectSubtreeNodes((TreepeaterNode) n.getChildAt(i), out);
        }
    }

    /** @return the created leaf, or {@code null} when {@code requestResponse} carries no request */
    public RequestTreeNode insertNode(HttpRequestResponse requestResponse) {
        if (requestResponse == null) {
            return null;
        }
        HttpRequest request = requestResponse.request();
        if (request == null) {
            return null;
        }
        this.requestCount += 1;

        DirectNameMode nameMode = TreepeaterSettings.getInstance().getDirectImportNameMode();
        String leafName = nameMode == DirectNameMode.ID
                ? String.valueOf(this.requestCount)
                : resolveDirectLeafName(request, nameMode, "");

        RequestTreeNode node = new RequestTreeNode(
                this.requestCount, leafName, request, requestResponse.response());

        node.addListener(this);

        this.tree.insertRootNode(node);
        Treepeater.saveState();
        notifyTreeChanged();
        return node;
    }

    public void insertNodeInto(TreepeaterNode child, TreepeaterNode parent, int index) {
        if (child != null) {
            child.addListener(this);
            listenToAllNodes(child);
        }
        this.tree.insertNodeInto(child, parent, index);
        Treepeater.saveState();
        notifyTreeChanged();
    }

    public FolderTreeNode createFolder(TreepeaterNode parent) {
        this.requestCount += 1;
        FolderTreeNode folder = new FolderTreeNode(this.requestCount, StatusRegistry.getDefault(), "New Folder");
        folder.addListener(this);

        TreepeaterNode target = parent;
        if (target == null) {
            target = (TreepeaterNode) this.tree.getTreeModel().getRoot();
        }
        if (!(target instanceof FolderTreeNode)) {
            target = (TreepeaterNode) target.getParent();
            if (target == null) {
                target = (TreepeaterNode) this.tree.getTreeModel().getRoot();
            }
        }
        this.tree.insertNodeInto(folder, target, target.getChildCount());
        Treepeater.saveState();
        return folder;
    }

    /**
     * Imports a single request into the tree, sorting it into the folder chain that best matches its
     * URL path. Missing folders are created; the leaf placement follows the user's
     * {@link TreepeaterSettings#getImportLeafMode() import leaf mode} setting.
     *
     * <p>In {@code DIRECT} mode the request becomes a leaf named after the last path segment, placed
     * next to any folder created for deeper paths. In {@code METHOD_FOLDER} mode the full path becomes
     * folders and the leaf is placed under a per-method folder (e.g. {@code [GET]}) using the
     * configured base leaf name.
     *
     * <p>When {@link TreepeaterSettings#isImportNormalizeDynamicSegmentsEnabled() dynamic segment
     * normalization} is enabled, path segments are rewritten into placeholders (e.g. {@code :id},
     * {@code :uuid}) before folder resolution.
     *
     * <p>Options are taken from {@link ImportOptions#fromSettings()} rather than the manual import
     * dialog.
     */
    public RequestTreeNode importRequestPathAware(HttpRequestResponse requestResponse) {
        if (requestResponse == null) {
            return null;
        }
        FolderTreeNode root = (FolderTreeNode) this.tree.getTreeModel().getRoot();
        return importRequestPathAware(root, requestResponse, ImportOptions.fromSettings());
    }

    /**
     * Imports a request under {@code destinationFolder} using options from the manual import dialog.
     *
     * @return the created leaf, or {@code null} when the arguments are incomplete
     */
    public RequestTreeNode importRequestManual(
            FolderTreeNode destinationFolder,
            HttpRequestResponse requestResponse,
            ImportOptions options) {
        if (destinationFolder == null || requestResponse == null || options == null) {
            return null;
        }
        if (options.placement() instanceof PathAwarePlacement) {
            return importRequestPathAware(destinationFolder, requestResponse, options);
        }

        HttpRequest request = requestResponse.request();
        if (request == null) {
            return null;
        }
        DirectPlacement direct = options.directPlacement();
        String leafName = direct.nameMode() == DirectNameMode.ID
                ? String.valueOf(this.requestCount + 1)
                : resolveDirectLeafName(request, direct.nameMode(), direct.manualName());
        return insertRequestLeaf(
                destinationFolder,
                leafName,
                request,
                requestResponse.response(),
                options.resolveStatus());
    }

    private RequestTreeNode importRequestPathAware(
            FolderTreeNode anchor,
            HttpRequestResponse requestResponse,
            ImportOptions options) {
        HttpRequest request = requestResponse.request();
        if (request == null) {
            return null;
        }
        HttpResponse response = requestResponse.response();
        PathAwarePlacement pathAware = options.pathAwarePlacement();

        List<String> segments = pathSegments(request);
        if (pathAware.normalizeDynamicSegmentsEnabled()) {
            segments = DynamicPathNormalizer.normalize(segments);
        }
        LenientFolderGrouping lenientGrouping = lenientFolderGroupingFromPlacement(pathAware);
        Status status = options.resolveStatus();

        if (pathAware.isMethodFolderMode()) {
            FolderTreeNode parent = resolveFolderChain(anchor, segments, lenientGrouping);
            String method = RequestDescriptions.method(request);
            FolderTreeNode methodFolder = findOrCreateChildFolder(parent, "[" + method + "]");
            String baseName = pathAware.baseLeafName();
            String leafName = (baseName != null && !baseName.isBlank()) ? baseName.trim() : "base";
            return insertRequestLeaf(methodFolder, leafName, request, response, status);
        }
        List<String> folderSegments =
                segments.isEmpty() ? segments : segments.subList(0, segments.size() - 1);
        FolderTreeNode parent = resolveFolderChain(anchor, folderSegments, lenientGrouping);
        String leafName = segments.isEmpty() ? "/" : segments.get(segments.size() - 1);
        return insertRequestLeaf(parent, leafName, request, response, status);
    }

    private static String resolveDirectLeafName(HttpRequest request, DirectNameMode mode, String manualName) {
        if (mode == DirectNameMode.ID) {
            throw new IllegalStateException("ID naming uses requestCount");
        }
        return switch (mode) {
            case URL -> RequestDescriptions.url(request);
            case PATH -> RequestDescriptions.path(request);
            case MANUAL -> {
                String name = manualName;
                yield (name != null && !name.isBlank()) ? name.trim() : "?";
            }
            case ID -> throw new IllegalStateException("ID naming uses requestCount");
        };
    }

    /**
     * Finds an existing child folder of {@code parent} whose name equals {@code name}, or creates one.
     */
    public FolderTreeNode findOrCreateChildFolder(FolderTreeNode parent, String name) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            TreepeaterNode child = (TreepeaterNode) parent.getChildAt(i);
            if (child instanceof FolderTreeNode folder && folder.getName().equals(name)) {
                return folder;
            }
        }
        this.requestCount += 1;
        FolderTreeNode folder = new FolderTreeNode(this.requestCount, StatusRegistry.getDefault(), name);
        this.insertNodeInto(folder, parent, parent.getChildCount());
        return folder;
    }

    private RequestTreeNode insertRequestLeaf(
            FolderTreeNode parent, String name, HttpRequest request, HttpResponse response, Status status) {
        this.requestCount += 1;
        RequestTreeNode node = new RequestTreeNode(
                this.requestCount, status, name, request, response, new RequestHistory());
        this.insertNodeInto(node, parent, parent.getChildCount());
        return node;
    }

    /**
     * Resolves the folder chain for {@code segments} under {@code root}, reusing the best matching
     * existing folder and creating any missing folders. Returns the deepest resolved folder.
     *
     * <p>Rather than descending greedily (which can commit to a locally-good branch and miss a better
     * match elsewhere), this enumerates every existing folder as a candidate slash-path, discards the
     * ones that do not match the target path, and then picks the best remaining candidate. A candidate
     * matches in one of two ways:
     * <ul>
     *   <li><b>Prefix match</b>: the folder's path is a prefix of the target path. It consumes as many
     *       target segments as the folder is deep. The deepest such folder is the best place to attach.
     *   <li><b>Lenient folder grouping</b>: when enabled in settings, a folder whose path
     *       contains the target after skipping up to the configured number of leading organizational
     *       segments (e.g. {@code ServiceA/users} for {@code /users/1}), provided the suffix covers
     *       at least the configured minimum overlap with the target path.
     * </ul>
     * When nothing matches, the chain is created directly under {@code root}.
     */
    private FolderTreeNode resolveFolderChain(
            FolderTreeNode root, List<String> segments, LenientFolderGrouping lenientGrouping) {
        if (segments.isEmpty()) {
            return root;
        }

        List<FolderCandidate> candidates = new ArrayList<>();
        collectFolderCandidates(root, new ArrayList<>(), candidates);

        FolderMatch best = null;
        for (FolderCandidate candidate : candidates) {
            FolderMatch match = matchCandidate(candidate, segments, lenientGrouping);
            if (match != null && (best == null || match.betterThan(best))) {
                best = match;
            }
        }

        FolderTreeNode parent = best != null ? best.folder() : root;
        int consumed = best != null ? best.consumed() : 0;
        for (int i = consumed; i < segments.size(); i++) {
            parent = findOrCreateChildFolder(parent, segments.get(i));
        }
        return parent;
    }

    /** An existing folder together with its slash-path from the root and DFS pre-order index. */
    private record FolderCandidate(FolderTreeNode folder, List<String> path, int order) {}

    /**
     * Configuration for {@linkplain #matchCandidate lenient folder grouping}: whether it is
     * active and how strictly folder paths may be aligned to the import target.
     */
    private record LenientFolderGrouping(boolean enabled, int maxSkip, double matchThreshold) {
        static LenientFolderGrouping disabled() {
            return new LenientFolderGrouping(false, 0, 1.0);
        }
    }

    private static LenientFolderGrouping lenientFolderGroupingFromPlacement(PathAwarePlacement pathAware) {
        if (!pathAware.lenientGroupingEnabled()) {
            return LenientFolderGrouping.disabled();
        }
        return new LenientFolderGrouping(
                true,
                pathAware.lenientGroupingMaxSkip(),
                pathAware.lenientGroupingMatchThresholdPercent() / 100.0);
    }

    /**
     * A matched candidate: how many target segments it consumes, how many leading grouping segments
     * were skipped, and its DFS order (used only as a deterministic tie-breaker).
     */
    private record FolderMatch(FolderTreeNode folder, int consumed, int skip, int order) {
        boolean betterThan(FolderMatch other) {
            if (this.consumed != other.consumed) {
                return this.consumed > other.consumed;
            }
            if (this.skip != other.skip) {
                return this.skip < other.skip;
            }
            return this.order < other.order;
        }
    }

    /** Depth-first collects every folder under {@code node} with its full slash-path from the root. */
    private void collectFolderCandidates(FolderTreeNode node, List<String> prefix, List<FolderCandidate> out) {
        for (int i = 0; i < node.getChildCount(); i++) {
            TreepeaterNode child = (TreepeaterNode) node.getChildAt(i);
            if (child instanceof FolderTreeNode folder) {
                List<String> path = new ArrayList<>(prefix);
                for (String segment : folderNameSegments(folder.getName())) {
                    path.add(segment);
                }
                out.add(new FolderCandidate(folder, path, out.size()));
                collectFolderCandidates(folder, path, out);
            }
        }
    }

    /** Matches a candidate against the target, or returns {@code null} if it does not match. */
    private static FolderMatch matchCandidate(
            FolderCandidate candidate, List<String> target, LenientFolderGrouping lenientGrouping) {
        Treepeater.api.logging().logToOutput("Matching candidate: " + candidate.path() + " against target: " + target.toString());
        List<String> path = candidate.path();
        if (path.isEmpty()) {
            return null;
        }
        // Prefix match: the folder path is a prefix of the target.
        if (isPrefix(path, target)) {
            return new FolderMatch(candidate.folder(), path.size(), 0, candidate.order());
        }

        if (!lenientGrouping.enabled()) {
            return null;
        }

        int maxSkip = lenientGrouping.maxSkip();
        double matchThreshold = lenientGrouping.matchThreshold();
        int mustMatch = (int) Math.ceil(target.size() * matchThreshold);
        if (path.size() >= mustMatch && path.size() - maxSkip <= target.size() && path.size() > maxSkip) {
            Treepeater.api.logging().logToOutput("Starting lenient folder grouping");
            // Lenient folder grouping: the folder path contains a prefix of the target after
            // skipping up to maxSkip leading organizational segments. The matched suffix must cover
            // at least matchThreshold of the target path.
    
            // Determine at which point the target path begins in the candidate path.
            int lenientMatchIndex = -1;
    
            for (int i = 1; i <= maxSkip; i++) {
                List<String> pathSubList = path.subList(i, path.size());
                if (pathSubList.isEmpty()) {
                    continue;
                }
                if (isPrefix(pathSubList, target)) {
                    Treepeater.api.logging().logToOutput("Found lenient-folder match at skip index: " + i);
                    Treepeater.api.logging().logToOutput("Path sublist: " + pathSubList.toString() + " is prefix of target: " + target.toString());
                    lenientMatchIndex = i;
                    break;
                }
            }
    
            if (lenientMatchIndex != -1) {
                Treepeater.api.logging().logToOutput("Identify if lenient-folder match covers at least "+ mustMatch + " segments of the target");
                List<String> lenientMatchList = path.subList(lenientMatchIndex, path.size());
                List<String> mustMatchList = target.subList(0, mustMatch);
    
                if (isPrefix(mustMatchList, lenientMatchList)) {
                    Treepeater.api.logging().logToOutput("Candidate matches lenient folder grouping threshold");
                    return new FolderMatch(candidate.folder(), path.size() - lenientMatchIndex, lenientMatchIndex, candidate.order());
                }

                Treepeater.api.logging().logToOutput("Candidate does not match lenient folder grouping threshold");
            }
        }


        return null;
    }

    private static String[] folderNameSegments(String name) {
        if (name == null) {
            return new String[0];
        }
        List<String> segs = new ArrayList<>();
        for (String part : name.split("/")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                segs.add(trimmed);
            }
        }
        return segs.toArray(new String[0]);
    }

    /** True if {@code candidate} is a (non-strict) prefix of {@code full}. */
    private static boolean isPrefix(List<String> candidate, List<String> full) {
        if (candidate.size() > full.size()) {
            return false;
        }
        for (int i = 0; i < candidate.size(); i++) {
            if (!candidate.get(i).equals(full.get(i))) {
                return false;
            }
        }
        return true;
    }


    private static List<String> pathSegments(HttpRequest request) {
        String path = null;
        try {
            path = request.pathWithoutQuery();
        } catch (RuntimeException ignored) {
            // fall through
        }
        if (path == null || path.isBlank()) {
            try {
                path = request.path();
            } catch (RuntimeException ignored) {
                path = null;
            }
        }
        List<String> segments = new ArrayList<>();
        if (path == null) {
            return segments;
        }
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        for (String part : path.split("/")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                segments.add(trimmed);
            }
        }
        return segments;
    }

    /**
     * Where to insert a copied sibling under the source node's parent.
     */
    public enum SiblingCopyPlacement {
        /** Immediately after the source node (default). */
        AFTER_SOURCE,
        /** First child of the parent. */
        PARENT_TOP,
        /** Last child of the parent. */
        PARENT_BOTTOM
    }

    /**
     * Inserts a new node immediately after {@code source} under the same parent, using the given
     * request/response (typically the editor snapshot). Returns the new node, or {@code null} if
     * {@code source} has no parent (e.g. implicit root).
     */
    public RequestTreeNode copyAsSiblingUnderSameParent(RequestTreeNode source, HttpRequest request, HttpResponse response) {
        return copyAsSiblingUnderSameParent(source, request, response, null, SiblingCopyPlacement.AFTER_SOURCE);
    }

    /**
     * Same as {@link #copyAsSiblingUnderSameParent(RequestTreeNode, HttpRequest, HttpResponse)} but uses
     * {@code nameOrNull} when non-blank; otherwise appends {@code " (copy)"} to the source name.
     */
    public RequestTreeNode copyAsSiblingUnderSameParent(
            RequestTreeNode source, HttpRequest request, HttpResponse response, String nameOrNull) {
        return copyAsSiblingUnderSameParent(source, request, response, nameOrNull, SiblingCopyPlacement.AFTER_SOURCE);
    }

    /**
     * Same as {@link #copyAsSiblingUnderSameParent(RequestTreeNode, HttpRequest, HttpResponse, String)} with
     * control over sibling insertion position under the parent.
     */
    public RequestTreeNode copyAsSiblingUnderSameParent(
            RequestTreeNode source,
            HttpRequest request,
            HttpResponse response,
            String nameOrNull,
            SiblingCopyPlacement placement) {
        TreeNode parentRaw = source.getParent();
        if (!(parentRaw instanceof TreepeaterNode parent)) {
            return null;
        }

        this.requestCount += 1;

        String copyName;
        if (nameOrNull != null && !nameOrNull.trim().isEmpty()) {
            copyName = nameOrNull.trim();
        } else {
            String baseName = source.getName();
            copyName = baseName.endsWith(" (copy)") ? baseName : baseName + " (copy)";
        }

        RequestHistory history = new RequestHistory();
        history.addEntry(request.httpService().host(), request, response);

        RequestTreeNode copy =
                new RequestTreeNode(
                        this.requestCount,
                        source.getStatus(),
                        copyName,
                        request,
                        response,
                        history);
        copy.addListener(this);

        SiblingCopyPlacement where =
                placement != null ? placement : SiblingCopyPlacement.AFTER_SOURCE;
        int insertIndex =
                switch (where) {
                    case AFTER_SOURCE -> parent.getIndex(source) + 1;
                    case PARENT_TOP -> 0;
                    case PARENT_BOTTOM -> parent.getChildCount();
                };
        this.tree.insertNodeInto(copy, parent, insertIndex);
        Treepeater.saveState();
        return copy;
    }

    public RequestTree getTree() {
        return this.tree;
    }

    public int getRequestCount() {
        return requestCount;
    }

    public void addListener(TreepeaterModelListener l) {
        this.listeners.add(l);
    }

    public void removeListener(TreepeaterModelListener l) {
        this.listeners.remove(l);
    }

    private void notifyTreeChanged() {
        this.listeners.forEach(TreepeaterModelListener::onTreeChanged);
    }

    @Override
    public void onSelect(TreepeaterNode node) {
        if (node instanceof RequestTreeNode requestNode) {
            this.addTab(requestNode);
        }
    }

    @Override
    public void onNameChange(String newName) {
        notifyTreeChanged();
    }

    public RequestTreeNode getActiveNode() {
        return this.activeNode;
    }

    public List<RequestTreeNode> getTabs() {
        return this.workspace.allOpenTabs();
    }

    public List<RequestTreeNode> getAllOpenTabs() {
        return this.workspace.allOpenTabs();
    }

    /**
     * The {@link #getTabs()} list is keyed by object identity. After drag-and-drop, the same logical tab
     * can still be in that list while the tree model holds a <em>new</em> {@link RequestTreeNode} with
     * the same id, and the list entry is a detached (removed) node. This finds the in-tree node for a
     * request id, or null if it does not exist.
     */
    public RequestTreeNode findRequestNodeInTreeById(int id) {
        Object root = this.tree.getTreeModel().getRoot();
        if (!(root instanceof TreepeaterNode r)) {
            return null;
        }
        return findRequestNodeInTreeById(r, id);
    }

    /** In-tree node for {@code id}, or {@code fallback} when the tab list holds a detached node. */
    public RequestTreeNode resolveRequestNode(int id, RequestTreeNode fallback) {
        RequestTreeNode found = findRequestNodeInTreeById(id);
        return found != null ? found : fallback;
    }

    /** All request leaf nodes in the tree, in depth-first order. */
    public List<RequestTreeNode> allRequestNodesInTree() {
        List<RequestTreeNode> out = new ArrayList<>();
        Object root = this.tree.getTreeModel().getRoot();
        if (root instanceof TreepeaterNode r) {
            collectRequestNodesInTree(r, out);
        }
        return out;
    }

    private static void collectRequestNodesInTree(TreepeaterNode node, List<RequestTreeNode> out) {
        if (node instanceof RequestTreeNode rn) {
            out.add(rn);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectRequestNodesInTree((TreepeaterNode) node.getChildAt(i), out);
        }
    }

    private static RequestTreeNode findRequestNodeInTreeById(TreepeaterNode node, int id) {
        if (node instanceof RequestTreeNode rn && rn.getId() == id) {
            return rn;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            TreepeaterNode c = (TreepeaterNode) node.getChildAt(i);
            RequestTreeNode f = findRequestNodeInTreeById(c, id);
            if (f != null) {
                return f;
            }
        }
        return null;
    }

    @Override
    public void onDelete(TreepeaterNode node) {
        this.removeNodeFromTree(node);
    }
}
