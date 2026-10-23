package treepeater.tree;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import burp.api.montoya.http.message.requests.HttpRequest;

import treepeater.requestResponse.Status;

/**
 * Immutable tree filter: the title search query plus optional status and request method
 * restrictions. Values within one group combine with OR, the groups (and the title query)
 * combine with AND; an empty group places no restriction.
 *
 * @param query     parsed title search query
 * @param statusIds selected status ids, normalized with {@link #normalizeStatusId}
 * @param methods   selected request methods, normalized with {@link #normalizeMethod}
 */
public record TreeFilter(TreeTitleMatcher.Query query, Set<String> statusIds, Set<String> methods) {
    private static final List<String> STANDARD_METHODS = List.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE", "CONNECT");

    /** Orders standard HTTP methods first (in conventional order), then the rest alphabetically. */
    public static final Comparator<String> METHOD_ORDER = Comparator
            .<String>comparingInt(m -> {
                int idx = STANDARD_METHODS.indexOf(m);
                return idx >= 0 ? idx : Integer.MAX_VALUE;
            })
            .thenComparing(Comparator.naturalOrder());

    public TreeFilter {
        query = query != null ? query : TreeTitleMatcher.parse("");
        statusIds = normalizeAll(statusIds, true);
        methods = normalizeAll(methods, false);
    }

    public static TreeFilter none() {
        return new TreeFilter(TreeTitleMatcher.parse(""), Set.of(), Set.of());
    }

    public static String normalizeStatusId(String id) {
        return id == null ? null : id.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizeMethod(String method) {
        return method == null ? null : method.trim().toUpperCase(Locale.ROOT);
    }

    private static Set<String> normalizeAll(Collection<String> values, boolean status) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (String value : values) {
            String normalized = status ? normalizeStatusId(value) : normalizeMethod(value);
            if (normalized != null && !normalized.isEmpty()) {
                out.add(normalized);
            }
        }
        return Set.copyOf(out);
    }

    /** @return {@code true} when neither a title query nor an attribute filter is set. */
    public boolean isEmpty() {
        return this.query.isEmpty() && !this.hasAttributeFilter();
    }

    public boolean hasAttributeFilter() {
        return !this.statusIds.isEmpty() || !this.methods.isEmpty();
    }

    public TreeFilter withQuery(TreeTitleMatcher.Query next) {
        return new TreeFilter(next, this.statusIds, this.methods);
    }

    public TreeFilter withStatusIds(Collection<String> next) {
        return new TreeFilter(this.query, Set.copyOf(next), this.methods);
    }

    public TreeFilter withMethods(Collection<String> next) {
        return new TreeFilter(this.query, this.statusIds, Set.copyOf(next));
    }

    public TreeFilter withoutAttributeFilters() {
        return new TreeFilter(this.query, Set.of(), Set.of());
    }

    /**
     * Whether {@code node} satisfies the status and method restrictions (the title query is
     * not considered). Folders have no request method, so they never match while a method
     * restriction is active.
     */
    public boolean matchesAttributes(TreepeaterNode node) {
        if (!this.statusIds.isEmpty()) {
            Status status = node.getStatus();
            String id = status != null ? normalizeStatusId(status.getId()) : null;
            if (id == null || !this.statusIds.contains(id)) {
                return false;
            }
        }
        if (!this.methods.isEmpty()) {
            if (!(node instanceof RequestTreeNode requestNode)) {
                return false;
            }
            HttpRequest request = requestNode.getRequest();
            String method = request != null ? normalizeMethod(request.method()) : null;
            return method != null && this.methods.contains(method);
        }
        return true;
    }
}
