package treepeater.ai.anthropic;

import static org.junit.jupiter.api.Assertions.*;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;

import org.junit.jupiter.api.Test;

import treepeater.ai.ChatToolDefinition;

class AnthropicStreamingChatClientTest {

    /** An empty / blank schema string must not blow up; we still emit a valid {@code type:"object"}. */
    @Test
    void buildInputSchema_handlesBlank() throws Exception {
        Tool.InputSchema schema = AnthropicStreamingChatClient.buildInputSchema("");
        assertEquals(JsonValue.from("object"), schema._type());
        assertTrue(schema.properties().isEmpty());
        assertTrue(schema.required().isEmpty());
    }

    /** Properties, required, and additionalProperties from the JSON schema must be copied onto the Anthropic schema. */
    @Test
    void buildInputSchema_copiesPropertiesAndRequired() throws Exception {
        String json =
                "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\",\"description\":\"who\"},\"count\":{\"type\":\"integer\"}},\"required\":[\"name\"],\"additionalProperties\":false}";

        Tool.InputSchema schema = AnthropicStreamingChatClient.buildInputSchema(json);

        assertEquals(JsonValue.from("object"), schema._type());
        assertTrue(schema.properties().isPresent(), "properties should be populated");
        assertTrue(
                schema.properties().get()._additionalProperties().containsKey("name"),
                "property 'name' should pass through");
        assertTrue(
                schema.properties().get()._additionalProperties().containsKey("count"),
                "property 'count' should pass through");
        assertEquals(
                java.util.List.of("name"),
                schema.required().orElse(java.util.List.of()),
                "required list should pass through");
        assertEquals(
                JsonValue.from(false),
                schema._additionalProperties().get("additionalProperties"),
                "additionalProperties should pass through unchanged");
    }

    /** Only the final tool in the list carries a cache_control breakpoint. */
    @Test
    void anthropicTool_cacheControlOnlyOnLastTool() throws Exception {
        ChatToolDefinition def =
                new ChatToolDefinition(
                        "t",
                        "desc",
                        "{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}");

        Tool notLast = AnthropicStreamingChatClient.anthropicTool(def, false);
        Tool last = AnthropicStreamingChatClient.anthropicTool(def, true);

        assertTrue(notLast.cacheControl().isEmpty(), "non-final tools must not set cache_control");
        assertTrue(last.cacheControl().isPresent(), "final tool must set cache_control");
    }
}
