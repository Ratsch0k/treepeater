package treepeater.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import burp.api.montoya.http.message.requests.HttpRequest;

import treepeater.settings.StatusRegistry;

class TreeFilterTest {
    private final StatusRegistry registry = new StatusRegistry();

    private RequestTreeNode request(String method, String statusId) {
        HttpRequest req = null;
        if (method != null) {
            req = mock(HttpRequest.class);
            when(req.method()).thenReturn(method);
        }
        RequestTreeNode node = new RequestTreeNode(1, "req", req, null);
        node.setStatus(this.registry.getById(statusId));
        return node;
    }

    private FolderTreeNode folder(String statusId) {
        return new FolderTreeNode(2, this.registry.getById(statusId), "folder");
    }

    @Test
    void emptyFilterIsEmptyAndMatchesEverything() {
        TreeFilter none = TreeFilter.none();

        assertTrue(none.isEmpty());
        assertFalse(none.hasAttributeFilter());
        assertTrue(none.matchesAttributes(this.request("GET", "DEFAULT")));
        assertTrue(none.matchesAttributes(this.folder("DEFAULT")));
    }

    @Test
    void isEmptyTakesQueryAndAttributesIntoAccount() {
        assertFalse(TreeFilter.none().withQuery(TreeTitleMatcher.parse("a")).isEmpty());
        assertFalse(TreeFilter.none().withMethods(List.of("get")).isEmpty());
        assertFalse(TreeFilter.none().withStatusIds(List.of("TODO")).isEmpty());
        assertTrue(TreeFilter.none().withMethods(List.of("get")).withoutAttributeFilters().isEmpty());
    }

    @Test
    void statusMatchesOnIdCaseInsensitively() {
        TreeFilter filter = TreeFilter.none().withStatusIds(List.of("todo"));

        assertTrue(filter.matchesAttributes(this.request("GET", "TODO")));
        assertFalse(filter.matchesAttributes(this.request("GET", "DONE")));
    }

    @Test
    void valuesWithinAGroupAreOred() {
        TreeFilter filter = TreeFilter.none()
                .withStatusIds(List.of("TODO", "DONE"))
                .withMethods(List.of("GET", "POST"));

        assertTrue(filter.matchesAttributes(this.request("GET", "TODO")));
        assertTrue(filter.matchesAttributes(this.request("POST", "DONE")));
    }

    @Test
    void groupsAreAnded() {
        TreeFilter filter = TreeFilter.none()
                .withStatusIds(List.of("TODO"))
                .withMethods(List.of("GET"));

        assertTrue(filter.matchesAttributes(this.request("GET", "TODO")));
        assertFalse(filter.matchesAttributes(this.request("POST", "TODO")));
        assertFalse(filter.matchesAttributes(this.request("GET", "DONE")));
    }

    @Test
    void methodsAreCaseInsensitive() {
        TreeFilter filter = TreeFilter.none().withMethods(List.of("get"));

        assertEquals(Set.of("GET"), filter.methods());
        assertTrue(filter.matchesAttributes(this.request("Get", "DEFAULT")));
    }

    @Test
    void requestWithoutHttpRequestNeverMatchesAMethodFilter() {
        TreeFilter filter = TreeFilter.none().withMethods(List.of("GET"));

        assertFalse(filter.matchesAttributes(this.request(null, "DEFAULT")));
    }

    @Test
    void foldersMatchOnStatusButNeverWhileAMethodFilterIsActive() {
        TreeFilter status = TreeFilter.none().withStatusIds(List.of("TODO"));
        TreeFilter statusAndMethod = status.withMethods(List.of("GET"));

        assertTrue(status.matchesAttributes(this.folder("TODO")));
        assertFalse(status.matchesAttributes(this.folder("DONE")));
        assertFalse(statusAndMethod.matchesAttributes(this.folder("TODO")));
    }

    @Test
    void equalityIsByValue() {
        assertEquals(
                TreeFilter.none().withMethods(List.of("get")).withStatusIds(List.of("TODO")),
                TreeFilter.none().withStatusIds(List.of("todo")).withMethods(List.of("GET")));
    }

    @Test
    void methodOrderPutsStandardMethodsFirst() {
        List<String> sorted = new java.util.ArrayList<>(List.of("PROPFIND", "POST", "DELETE", "GET", "BREW"));
        sorted.sort(TreeFilter.METHOD_ORDER);

        assertEquals(List.of("GET", "POST", "DELETE", "BREW", "PROPFIND"), sorted);
    }
}
