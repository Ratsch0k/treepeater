package treepeater.draggable;
import java.util.HashSet;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import treepeater.requestResponse.Status;
import treepeater.tree.TreepeaterNodeListener;

public class RequestTreeNodeTransferable {
    public boolean isFolder;
    public int id;
    public Status status;
    public String name;
    public HttpRequest request;
    public HttpResponse response;
    public String notes;
    public HashSet<TreepeaterNodeListener> listener;

    public RequestTreeNodeTransferable(boolean isFolder, int id, Status status, String name, HttpRequest request, HttpResponse response, String notes, HashSet<TreepeaterNodeListener> l) {
        this.isFolder = isFolder;
        this.id = id;
        this.status = status;
        this.name = name;
        this.request = request;
        this.response = response;
        this.notes = notes;
        this.listener = l;
    }
}
