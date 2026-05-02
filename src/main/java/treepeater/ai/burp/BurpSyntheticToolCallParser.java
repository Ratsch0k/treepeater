package treepeater.ai.burp;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import treepeater.ai.ChatToolCall;

/**
 * Parses Burp-AI-specific synthetic tool calls embedded in assistant text. Well-formed blocks are
 * removed from {@link BurpToolParseResult#visibleText()}; invalid or unknown-tool blocks are left
 * verbatim in the visible text.
 */
public final class BurpSyntheticToolCallParser {
    public static final String START_MARKER = "<<<TREEPEATER_TOOL>>>";
    public static final String END_MARKER = "<<<END_TREEPEATER_TOOL>>>";

    private static final ObjectMapper JSON = new ObjectMapper();

    private BurpSyntheticToolCallParser() {}

    /**
     * @param response raw assistant text from Burp
     * @param knownToolNames tool names allowed for this session (from {@code ChatTooling})
     * @param defaultIdPrefix e.g. {@code "burp-tool-0-"} so ids become {@code burp-tool-0-0}, {@code burp-tool-0-1}, …
     */
    public static BurpToolParseResult parse(String response, Set<String> knownToolNames, String defaultIdPrefix) {
        if (response == null || response.isEmpty()) {
            return new BurpToolParseResult("", List.of());
        }
        if (knownToolNames == null || knownToolNames.isEmpty()) {
            return new BurpToolParseResult(response, List.of());
        }
        String prefix = defaultIdPrefix != null ? defaultIdPrefix : "burp-tool-";

        List<ChatToolCall> calls = new ArrayList<>();
        StringBuilder prose = new StringBuilder();
        int cursor = 0;
        final int n = response.length();

        while (cursor < n) {
            int start = response.indexOf(START_MARKER, cursor);
            if (start < 0) {
                prose.append(response, cursor, n);
                break;
            }
            prose.append(response, cursor, start);
            int afterStartMarker = start + START_MARKER.length();
            while (afterStartMarker < n && (response.charAt(afterStartMarker) == '\r' || response.charAt(afterStartMarker) == '\n')) {
                afterStartMarker++;
            }
            int end = response.indexOf(END_MARKER, afterStartMarker);
            if (end < 0) {
                prose.append(response, start, n);
                break;
            }
            String inner = response.substring(afterStartMarker, end).trim();
            int afterEndMarker = end + END_MARKER.length();
            while (afterEndMarker < n && (response.charAt(afterEndMarker) == '\r' || response.charAt(afterEndMarker) == '\n')) {
                afterEndMarker++;
            }

            ParsedBlock parsed = tryParseBlock(inner, knownToolNames);
            if (parsed == null) {
                prose.append(response, start, afterEndMarker);
            } else {
                String id =
                        parsed.id != null && !parsed.id.isBlank()
                                ? parsed.id
                                : prefix + calls.size();
                calls.add(new ChatToolCall(id, parsed.name, parsed.argumentsJson));
            }
            cursor = afterEndMarker;
        }

        return new BurpToolParseResult(prose.toString(), List.copyOf(calls));
    }

    static String encodeSyntheticToolCalls(String prosePrefix, List<ChatToolCall> calls) {
        if (calls == null || calls.isEmpty()) {
            return prosePrefix == null ? "" : prosePrefix;
        }
        StringBuilder sb = new StringBuilder();
        if (prosePrefix != null && !prosePrefix.isEmpty()) {
            sb.append(prosePrefix);
        }
        for (ChatToolCall tc : calls) {
            if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') {
                sb.append('\n');
            }
            sb.append(START_MARKER).append('\n');
            ObjectNode o = JSON.createObjectNode();
            o.put("name", tc.name());
            JsonNode argsNode = readArguments(tc.argumentsJson());
            o.set("arguments", argsNode);
            if (tc.id() != null && !tc.id().isBlank()) {
                o.put("id", tc.id());
            }
            sb.append(o.toString());
            sb.append('\n').append(END_MARKER);
        }
        return sb.toString();
    }

    private static JsonNode readArguments(String argumentsJson) {
        try {
            if (argumentsJson == null || argumentsJson.isBlank()) {
                return JSON.createObjectNode();
            }
            JsonNode n = JSON.readTree(argumentsJson);
            return n.isObject() ? n : JSON.createObjectNode();
        } catch (Exception e) {
            return JSON.createObjectNode();
        }
    }

    private record ParsedBlock(String name, String argumentsJson, String id) {}

    private static ParsedBlock tryParseBlock(String inner, Set<String> knownToolNames) {
        JsonNode root;
        try {
            root = JSON.readTree(inner);
        } catch (Exception e) {
            return null;
        }
        if (root == null || !root.isObject()) {
            return null;
        }
        JsonNode nameNode = root.get("name");
        if (nameNode == null || !nameNode.isTextual()) {
            return null;
        }
        String name = nameNode.asText("");
        if (name.isBlank() || !knownToolNames.contains(name)) {
            return null;
        }
        JsonNode argsNode = root.get("arguments");
        if (argsNode == null || !argsNode.isObject()) {
            return null;
        }
        String argsJson = argsNode.toString();
        String id = null;
        JsonNode idNode = root.get("id");
        if (idNode != null && idNode.isTextual()) {
            id = idNode.asText("");
        }
        return new ParsedBlock(name, argsJson, id);
    }
}
