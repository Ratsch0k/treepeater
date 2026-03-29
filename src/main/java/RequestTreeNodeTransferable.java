import java.util.HashSet;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

public class RequestTreeNodeTransferable {
    public int id;
    public Status status;
    public String name;
    public HttpRequest request;
    public HttpResponse response;
    public HashSet<RequestTreeNodeListener> listener;

    public RequestTreeNodeTransferable(int id, Status status, String name, HttpRequest request, HttpResponse response, HashSet<RequestTreeNodeListener> l) {
        this.id = id;
        this.status = status;
        this.name = name;
        this.request = request;
        this.response = response;
        this.listener = l;
    }
}
