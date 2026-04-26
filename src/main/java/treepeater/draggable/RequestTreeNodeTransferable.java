package treepeater.draggable;
import java.util.HashSet;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import treepeater.ai.AgentChatWorkspace;
import treepeater.requestResponse.Status;
import treepeater.tree.RequestTreeNodeListener;

public class RequestTreeNodeTransferable {
    public int id;
    public Status status;
    public String name;
    public HttpRequest request;
    public HttpResponse response;
    public String notes;
    public HashSet<RequestTreeNodeListener> listener;
    public AgentChatWorkspace agentChatWorkspace;

    public RequestTreeNodeTransferable(
            int id,
            Status status,
            String name,
            HttpRequest request,
            HttpResponse response,
            String notes,
            HashSet<RequestTreeNodeListener> l,
            AgentChatWorkspace agentChatWorkspace) {
        this.id = id;
        this.status = status;
        this.name = name;
        this.request = request;
        this.response = response;
        this.notes = notes;
        this.listener = l;
        this.agentChatWorkspace = agentChatWorkspace;
    }
}
