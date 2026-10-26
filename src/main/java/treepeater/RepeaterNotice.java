package treepeater;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * Adds a Repeater tab that tells users without Treepeater installed that the project was used with it.
 */
public final class RepeaterNotice {
    public static final String HOST = "treepeater.invalid";
    public static final String PROJECT_URL = "https://github.com/Ratsch0k/treepeater";

    private RepeaterNotice() {}

    public static HttpRequest buildRequest(String version) {
        String body = "This Burp project was created/edited with the Treepeater extension (version " + version + ").\r\n"
                + "Its request trees, statuses, and notes are stored in the project file\r\n"
                + "and can only be viewed with Treepeater installed.\r\n"
                + "\r\n"
                + "Get Treepeater: " + PROJECT_URL + "\r\n"
                + "Notice created: " + LocalDate.now() + "\r\n";
        int length = body.getBytes(StandardCharsets.UTF_8).length;
        String raw = "POST /this-project-uses-treepeater HTTP/1.1\r\n"
                + "Host: " + HOST + "\r\n"
                + "X-Treepeater-Version: " + version + "\r\n"
                + "Content-Type: text/plain\r\n"
                + "Content-Length: " + length + "\r\n"
                + "\r\n"
                + body;
        return HttpRequest.httpRequest(HttpService.httpService(HOST, 443, true), ByteArray.byteArray(raw));
    }

    public static void createNoticeInRepeaterTab(String version) {
        Treepeater.api.repeater().sendToRepeater(buildRequest(version), "Treepeater " + version + " used");
        Treepeater.getPersistence().setUsedTreepeaterVersion(version);
    }

    /**
     * Creates the notice when the project holds requests and no notice exists for the running version.
     */
    public static void ensureNotice(TreepeaterModel model) {
        try {
            String current = TreepeaterVersion.get();
            if (current.equals(Treepeater.getPersistence().getUsedTreepeaterVersion())) {
                return;
            }
            if (model.getTree().toSimpleRepeaterList().isEmpty()) {
                return;
            }
            createNoticeInRepeaterTab(current);
        } catch (Exception e) {
            Treepeater.api.logging().logToOutput("Could not create Repeater notice: " + e.getMessage());
        }
    }
}
