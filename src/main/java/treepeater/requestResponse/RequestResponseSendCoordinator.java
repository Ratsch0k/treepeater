package treepeater.requestResponse;

import java.awt.event.ActionEvent;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.SwingWorker;

import burp.api.montoya.http.RequestOptions;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.editor.HttpRequestEditor;

import treepeater.Treepeater;
import treepeater.components.CustomButton;
import treepeater.tree.RequestTreeNode;

/**
 * Send / cancel actions and background HTTP worker for {@link RequestResponsePanel}.
 */
final class RequestResponseSendCoordinator {
    private final HttpRequestEditor requestEditor;
    private final RequestTreeNode node;
    private final RequestPanelHttpTarget target;
    private final CustomButton sendButton;
    private final JButton cancelButton;
    private final AtomicReference<SwingWorker<HttpResponse, Void>> activeWorker;
    private final SendCompletionHandler onComplete;

    @FunctionalInterface
    interface SendCompletionHandler {
        void onSendComplete(HttpRequest snapshot, HttpResponse response, LocalDateTime time, String targetLabel);
    }

    RequestResponseSendCoordinator(
            HttpRequestEditor requestEditor,
            RequestTreeNode node,
            RequestPanelHttpTarget target,
            CustomButton sendButton,
            JButton cancelButton,
            SendCompletionHandler onComplete) {
        this.requestEditor = requestEditor;
        this.node = node;
        this.target = target;
        this.sendButton = sendButton;
        this.cancelButton = cancelButton;
        this.onComplete = onComplete;
        this.activeWorker = new AtomicReference<>();
    }

    void registerActions() {
        this.sendButton.setAction(new AbstractAction("Send") {
            @Override
            public void actionPerformed(ActionEvent e) {
                RequestResponseSendCoordinator.this.sendButton.setEnabled(false);
                RequestResponseSendCoordinator.this.cancelButton.setEnabled(true);

                HttpRequest requestToSend = RequestResponseSendCoordinator.this.requestEditor.getRequest();
                requestToSend = RequestResponseSendCoordinator.this.target.applyToRequest(requestToSend);
                RequestResponseSendCoordinator.this.requestEditor.setRequest(requestToSend);
                RequestResponseSendCoordinator.this.node.setRequest(requestToSend);

                final HttpRequest historyRequestSnapshot = requestToSend;
                final String historyTargetLabel = RequestResponseSendCoordinator.this.target.statusLineLabel();
                final LocalDateTime historyTime = LocalDateTime.now();

                SwingWorker<HttpResponse, Void> worker = new SwingWorker<>() {

                    @Override
                    protected HttpResponse doInBackground() throws Exception {
                        RequestOptions options = RequestOptions.requestOptions();
                        if (RequestResponseSendCoordinator.this.target.isSniEnabled()
                                && RequestResponseSendCoordinator.this.target.isHttps()) {
                            String host = RequestResponseSendCoordinator.this.target.getHost();
                            if (host != null && !host.isBlank()) {
                                options = options.withServerNameIndicator(host.trim());
                            }
                        }
                        HttpRequestResponse response = Treepeater.api.http().sendRequest(historyRequestSnapshot, options);
                        return response.response();
                    }

                    @Override
                    protected void done() {
                        try {
                            if (isCancelled()) {
                                return;
                            }
                            HttpResponse r = get();
                            RequestResponseSendCoordinator.this.onComplete.onSendComplete(
                                    historyRequestSnapshot, r, historyTime, historyTargetLabel);
                        } catch (Exception ex) {
                            Treepeater.api.logging().logToError(ex);
                        } finally {
                            RequestResponseSendCoordinator.this.sendButton.setEnabled(true);
                            RequestResponseSendCoordinator.this.cancelButton.setEnabled(false);
                            if (Objects.equals(RequestResponseSendCoordinator.this.activeWorker.get(), this)) {
                                RequestResponseSendCoordinator.this.activeWorker.set(null);
                            }
                        }
                    }
                };

                RequestResponseSendCoordinator.this.activeWorker.set(worker);
                worker.execute();
            }
        });

        this.cancelButton.setAction(new AbstractAction("Cancel") {
            @Override
            public void actionPerformed(ActionEvent e) {
                SwingWorker<HttpResponse, Void> w = RequestResponseSendCoordinator.this.activeWorker.get();
                if (w != null) {
                    w.cancel(true);
                }
                RequestResponseSendCoordinator.this.activeWorker.set(null);
                RequestResponseSendCoordinator.this.sendButton.setEnabled(true);
                RequestResponseSendCoordinator.this.cancelButton.setEnabled(false);
            }
        });
        this.cancelButton.setEnabled(false);
    }
}
