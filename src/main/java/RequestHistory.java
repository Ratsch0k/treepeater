import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

public class RequestHistory {
    private final List<HistoryEntry> history;
    private int currentIndex;

    public RequestHistory() {
        this.history = new ArrayList<>();
        this.currentIndex = -1;
    }

    public void addEntry(String targetLabel, HttpRequest request, HttpResponse response) {
        int index = this.history.size() + 1;
        HistoryEntry entry = new HistoryEntry(index, LocalDateTime.now(), targetLabel, request, response);
        this.history.add(entry);
        this.currentIndex = index;
    }

    public HistoryEntry getEntry(int index) {
        return this.history.get(index);
    }

    public int size() {
        return this.history.size();
    }

    public boolean isEmpty() {
        return this.history.isEmpty();
    }

    public List<HistoryEntry> entries() {
        return Collections.unmodifiableList(this.history);
    }

    public int getCurrentIndex() {
        return this.currentIndex;
    }

    public void setCurrentIndex(int index) {
        this.currentIndex = index;
    }

    public void navigateBack() {
        if (this.currentIndex > 0) {
            this.currentIndex--;
        }
    }

    public void navigateForward() {
        if (this.currentIndex < this.history.size() - 1) {
            this.currentIndex++;
        }
    }

    public HistoryEntry getCurrentEntry() {
        return this.history.get(this.currentIndex);
    }
}
