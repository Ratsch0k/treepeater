package treepeater.requestResponse;

import java.awt.event.ActionEvent;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.SwingWorker;

import burp.api.montoya.http.RequestOptions;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import treepeater.Treepeater;
import treepeater.components.CustomButton;

/**
 * Send / cancel actions and background HTTP worker for {@link RequestResponsePanel}.
 */
final class RequestResponseSendCoordinator {
    private final RequestPanelHttpTarget target;
    private final CustomButton sendButton;
    private final JButton cancelButton;
    private final AtomicReference<SwingWorker<HttpResponse, Void>> activeWorker;
    private final SendCompletionHandler onComplete;
    private final Supplier<HttpRequest> prepareRequestForSend;

    @FunctionalInterface
    interface SendCompletionHandler {
        void onSendComplete(HttpRequest snapshot, HttpResponse response, LocalDateTime time, String targetLabel);
    }

    RequestResponseSendCoordinator(
            RequestPanelHttpTarget target,
            CustomButton sendButton,
            JButton cancelButton,
            Supplier<HttpRequest> prepareRequestForSend,
            SendCompletionHandler onComplete) {
        this.target = target;
        this.sendButton = sendButton;
        this.cancelButton = cancelButton;
        this.prepareRequestForSend = prepareRequestForSend;
        this.onComplete = onComplete;
        this.activeWorker = new AtomicReference<>();
    }

    void registerActions() {
        this.sendButton.setAction(new AbstractAction("Send") {
            @Override
            public void actionPerformed(ActionEvent e) {
                RequestResponseSendCoordinator.this.sendButton.setEnabled(false);
                RequestResponseSendCoordinator.this.cancelButton.setEnabled(true);

                HttpRequest requestToSend = RequestResponseSendCoordinator.this.prepareRequestForSend.get();

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
