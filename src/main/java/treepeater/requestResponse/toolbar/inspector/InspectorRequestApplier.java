package treepeater.requestResponse.toolbar.inspector;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.core.Range;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.editor.HttpRequestEditor;

/** Writes a re-encoded value from the Inspector back into the request editor. */
final class InspectorRequestApplier {

    private InspectorRequestApplier() {}

    record Result(Range newOffsets, String newRaw, String statusMessage, boolean success) {
        static Result failure(String statusMessage) {
            return new Result(null, null, statusMessage, false);
        }
    }

    static Result apply(
            HttpRequestEditor editor, Range offsets, String valueText, InspectorEncoding scheme, boolean encodeMode) {
        String toWrite = encodeMode ? valueText : InspectorEncoding.encode(valueText, scheme);

        ByteArray current = editor.getRequest().toByteArray();
        if (current == null) {
            return Result.failure("Could not read the current request to apply the change.");
        }

        int start = offsets.startIndexInclusive();
        int end = offsets.endIndexExclusive();
        if (start < 0 || end > current.length() || start > end) {
            return Result.failure("Selection is no longer valid \u2013 reselect the text and try again.");
        }

        String full = current.toString();
        String updated = full.substring(0, start) + toWrite + full.substring(end);

        editor.setRequest(HttpRequest.httpRequest(editor.getRequest().httpService(), updated));

        int newEnd = start + toWrite.length();
        return new Result(Range.range(start, newEnd), toWrite, "Applied to request.", true);
    }
}
