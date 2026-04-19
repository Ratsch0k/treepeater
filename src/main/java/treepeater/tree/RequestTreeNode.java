package treepeater.tree;

import java.util.HashSet;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import treepeater.Treepeater;
import treepeater.requestResponse.RequestHistory;
import treepeater.requestResponse.Status;

public class RequestTreeNode extends TreepeaterNode {
    private HttpRequest request;
    private HttpResponse response;
    private String notes = "";
    private final RequestHistory history;

    public RequestTreeNode(int id, Status status, String name, HttpRequest request, HttpResponse response, RequestHistory history) {
        this(id, status, name, request, response, history, "");
    }

    public RequestTreeNode(int id, Status status, String name, HttpRequest request, HttpResponse response, RequestHistory history, String notes) {
        super(id, status, name);
        this.request = request;
        this.response = response;
        this.notes = notes != null ? notes : "";
        this.history = history;
    }

    public RequestTreeNode(int id, String name, HttpRequest request, HttpResponse response) {
        super(id, null, name);
        this.request = request;
        this.response = response;
        this.history = new RequestHistory();
    }

    public RequestTreeNode(int id, Status status, String name, HttpRequest request, HttpResponse response, HashSet<TreepeaterNodeListener> l, String notes) {
        this(id, status, name, request, response, null, l, notes);
    }

    /**
     * Used when reconstructing a node from drag-and-drop transfer data; preserves {@code history} when non-null.
     */
    public RequestTreeNode(int id, Status status, String name, HttpRequest request, HttpResponse response, RequestHistory history, HashSet<TreepeaterNodeListener> l, String notes) {
        super(id, status, name, l);
        this.request = request;
        this.response = response;
        this.notes = notes != null ? notes : "";
        this.history = history != null ? history : new RequestHistory();
    }

    public RequestTreeNode(RequestTreeNode copy) {
        super(copy.getId(), copy.getStatus(), copy.getName(), copy.getListeners());
        this.request = copy.request;
        this.response = copy.response;
        this.notes = copy.notes != null ? copy.notes : "";
        this.history = copy.history;
    }

    @Override
    public boolean getAllowsChildren() {
        return false;
    }

    public void setRequest(HttpRequest r) {
        this.request = r;
        Treepeater.saveState();
    }

    public void setResponse(HttpResponse r) {
        this.response = r;
        Treepeater.saveState();
    }

    public String getNotes() {
        return this.notes != null ? this.notes : "";
    }

    public void setNotes(String notes) {
        this.notes = notes != null ? notes : "";
        Treepeater.saveState();
    }

    public HttpRequest getRequest() {
        return this.request;
    }

    public HttpResponse getResponse() {
        return this.response;
    }

    public RequestHistory getHistory() {
        return this.history;
    }
}
