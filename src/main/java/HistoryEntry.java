import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

public class HistoryEntry {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final int index;
    private final LocalDateTime time;
    private final String targetLabel;
    public final HttpRequest request;
    public final HttpResponse response;

    HistoryEntry(int index, LocalDateTime time, String targetLabel, HttpRequest request, HttpResponse response) {
        this.index = index;
        this.time = time;
        this.targetLabel = targetLabel;
        this.request = request;
        this.response = response;
    }

    @Override
    public String toString() {
        String t = (time == null) ? "" : time.format(TIME_FMT);
        String target = (targetLabel == null) ? "" : targetLabel;
        if (target.isEmpty()) {
            return "#" + index + "  " + t;
        }
        return "#" + index + "  " + t + "  " + target;
    }
}