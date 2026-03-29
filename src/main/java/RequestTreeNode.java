import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import javax.swing.tree.DefaultMutableTreeNode;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

public class RequestTreeNode extends DefaultMutableTreeNode {
    private final int id;
    private Status status;
    private String name;
    private HttpRequest request;
    private HttpResponse response;
    private HashSet<RequestTreeNodeListener> listener;
    private final RequestHistory history;

    public RequestTreeNode(int id, String name, HttpRequest request, HttpResponse response) {
        super(name);

        this.id = id;
        this.name = name;
        this.status = Status.TODO;
        this.request = request;
        this.response = response;
        this.listener = new HashSet<>();
        this.history = new RequestHistory();
    }

    public RequestTreeNode(int id, Status status, String name, HttpRequest request, HttpResponse response, HashSet<RequestTreeNodeListener> l) {
        super(name);
        this.id = id;
        this.status = status;
        this.name = name;
        this.request = request;
        this.response = response;
        this.listener = l;
        this.history = new RequestHistory();
    }

    public RequestTreeNode(RequestTreeNode copy) {
        super(copy.getName());

        this.id = copy.id;
        this.name = copy.getName();
        this.status = copy.getStatus();
        this.request = copy.request;
        this.response = copy.response;
        this.listener = copy.listener;
        this.history = copy.history;
    }

    public Status getStatus() {
        return this.status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
        this.listener.forEach(l -> l.onNameChange(name));
    }

    public void addListener(RequestTreeNodeListener l) {
        this.listener.add(l);
    }

    public void removeListener(RequestTreeNodeListener l) {
        this.listener.remove(l);
    }

    public void select() {
        this.listener.forEach(l -> l.onSelect(this));
    }

    public HashSet<RequestTreeNodeListener> getListeners() {
        return this.listener;
    }

    public void setRequest(HttpRequest r) {
        this.request = r;    
    }

    public void setResponse(HttpResponse r) {
        this.response = r;
    }

    public HttpRequest getRequest() {
        return this.request;
    }

    public HttpResponse getResponse() {
        return this.response;
    }

    public int getId() {
        return this.id;
    }

    public RequestHistory getHistory() {
        return this.history;
    }

}
