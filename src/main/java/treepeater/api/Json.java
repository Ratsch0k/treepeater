package treepeater.api;

import java.util.Optional;
import java.util.OptionalInt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * JSON helpers shared by the external API: result building, lenient argument coercion, and the
 * {@code {"error":...}} envelope that tools return so both
 * tool families report failures the same way.
 */
public final class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() {}

    public static ObjectNode obj() {
        return MAPPER.createObjectNode();
    }

    public static ArrayNode arr() {
        return MAPPER.createArrayNode();
    }

    public static String nodeToString(JsonNode node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            return "{\"error\":\"could not serialize result\"}";
        }
    }

    public static JsonNode stringToNode(String raw) throws Exception {
        if (raw == null || raw.isBlank()) {
            return obj();
        }
        JsonNode node = MAPPER.readTree(raw);
        return node != null ? node : obj();
    }

    /**
     * Arguments object for a tool call, or {@code null} when {@code raw} is not parseable. Blank input
     * yields an empty object so tools without required arguments can be called with no body.
     */
    public static JsonNode readArgs(String raw) {
        try {
            JsonNode node = stringToNode(raw);
            return node.isObject() ? node : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static String error(String message) {
        ObjectNode out = obj();
        out.put("error", message != null && !message.isBlank() ? message : "error");
        return nodeToString(out);
    }

    /** Whether a tool result is an error envelope, used to set the MCP {@code isError} flag. */
    public static boolean isError(String result) {
        if (result == null) {
            return true;
        }
        try {
            JsonNode node = MAPPER.readTree(result);
            return node != null && node.isObject() && node.has("error");
        } catch (Exception e) {
            return false;
        }
    }

    /** Parses a tool result for embedding in a larger document, falling back to {@code raw_text}. */
    public static JsonNode resultAsNode(String raw) {
        if (raw == null) {
            return MAPPER.nullNode();
        }
        try {
            return MAPPER.readTree(raw);
        } catch (Exception e) {
            ObjectNode out = obj();
            out.put("raw_text", raw);
            return out;
        }
    }

    /** First present, non-null value among {@code keys}, accepting snake_case and camelCase aliases. */
    public static JsonNode first(JsonNode args, String... keys) {
        if (args == null) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = args.get(key);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    /** Trimmed text for the first matching key, or {@code null} when absent or blank. */
    public static String text(JsonNode args, String... keys) {
        JsonNode node = first(args, keys);
        if (node == null) {
            return null;
        }
        String raw = node.isTextual() ? node.asText() : node.toString();
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static OptionalInt integer(JsonNode args, String... keys) {
        JsonNode node = first(args, keys);
        if (node == null) {
            return OptionalInt.empty();
        }
        if (node.isIntegralNumber()) {
            return OptionalInt.of(node.asInt());
        }
        if (node.isTextual()) {
            try {
                return OptionalInt.of(Integer.parseInt(node.asText().trim()));
            } catch (NumberFormatException e) {
                return OptionalInt.empty();
            }
        }
        return OptionalInt.empty();
    }

    public static Optional<Boolean> bool(JsonNode args, String... keys) {
        JsonNode node = first(args, keys);
        if (node == null) {
            return Optional.empty();
        }
        if (node.isBoolean()) {
            return Optional.of(node.booleanValue());
        }
        if (node.isTextual()) {
            String raw = node.asText().trim();
            if ("true".equalsIgnoreCase(raw)) {
                return Optional.of(Boolean.TRUE);
            }
            if ("false".equalsIgnoreCase(raw)) {
                return Optional.of(Boolean.FALSE);
            }
        }
        return Optional.empty();
    }
}
