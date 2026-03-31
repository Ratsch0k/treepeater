package treepeater.draggable;
import burp.api.montoya.http.message.requests.HttpRequest;

public class RequestTreeNodeSimple {
    public HttpRequest request;
    public String name;

    public RequestTreeNodeSimple(HttpRequest request, String name) {
        this.request = request;
        this.name = name;
    }
}
