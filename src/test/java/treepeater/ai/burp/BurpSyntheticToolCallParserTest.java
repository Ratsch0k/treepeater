package treepeater.ai.burp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import treepeater.ai.ChatToolCall;

class BurpSyntheticToolCallParserTest {

    private final Set<String> known = Set.of("search_tabs", "noop");

    @Test
    void parse_single_wellFormed_extracts_call_and_strip() {
        String raw =
                "Plan text.\n<<<TREEPEATER_TOOL>>>\n{\"name\":\"noop\",\"arguments\":{}}\n<<<END_TREEPEATER_TOOL>>>";
        BurpToolParseResult r = BurpSyntheticToolCallParser.parse(raw, known, "p-");
        assertEquals("Plan text.", r.visibleText().trim());
        assertEquals(1, r.toolCalls().size());
        ChatToolCall tc = r.toolCalls().get(0);
        assertEquals("noop", tc.name());
        assertEquals("{}", tc.argumentsJson());
        assertEquals("p-0", tc.id());
    }

    @Test
    void parse_multiple_blocks_keeps_order() {
        String raw =
                "<<<TREEPEATER_TOOL>>>\n"
                        + "{\"name\":\"noop\",\"arguments\":{\"k\":1}}\n<<<END_TREEPEATER_TOOL>>>"
                        + "\nmiddle\n"
                        + "<<<TREEPEATER_TOOL>>>\n"
                        + "{\"name\":\"search_tabs\",\"arguments\":{\"query\":\"x\"}}\n<<<END_TREEPEATER_TOOL>>>";
        BurpToolParseResult r = BurpSyntheticToolCallParser.parse(raw, known, "p-");
        assertEquals("middle", r.visibleText().trim());
        assertEquals("noop", r.toolCalls().get(0).name());
        assertEquals("{\"k\":1}", r.toolCalls().get(0).argumentsJson());
        assertEquals("search_tabs", r.toolCalls().get(1).name());
        assertEquals("p-1", r.toolCalls().get(1).id());
    }

    @Test
    void parse_respects_explicit_id_from_json() {
        String raw =
                "<<<TREEPEATER_TOOL>>>\n{\"name\":\"noop\",\"arguments\":{},\"id\":\"custom-9\"}\n<<<END_TREEPEATER_TOOL>>>";
        BurpToolParseResult r = BurpSyntheticToolCallParser.parse(raw, known, "p-");
        assertEquals("", r.visibleText());
        assertEquals("custom-9", r.toolCalls().get(0).id());
    }

    @Test
    void parse_unknown_tool_name_leaves_block_in_visible_text() {
        String raw =
                "<<<TREEPEATER_TOOL>>>\n{\"name\":\"evil\",\"arguments\":{}}\n<<<END_TREEPEATER_TOOL>>>";
        BurpToolParseResult r = BurpSyntheticToolCallParser.parse(raw, known, "p-");
        assertTrue(r.toolCalls().isEmpty());
        assertTrue(r.visibleText().contains("evil"));
        assertTrue(r.visibleText().contains(BurpSyntheticToolCallParser.START_MARKER));
    }

    @Test
    void parse_bad_json_leaves_block_in_visible_text() {
        String raw = "<<<TREEPEATER_TOOL>>>\nNOT JSON\n<<<END_TREEPEATER_TOOL>>>";
        BurpToolParseResult r = BurpSyntheticToolCallParser.parse(raw, known, "p-");
        assertTrue(r.toolCalls().isEmpty());
        assertTrue(r.visibleText().contains("NOT JSON"));
    }

    @Test
    void parse_unclosed_marker_treated_as_plain_text() {
        String raw = "hi <<<TREEPEATER_TOOL>>>\n{\"name\":\"noop\",\"arguments\":{}}";
        BurpToolParseResult r = BurpSyntheticToolCallParser.parse(raw, known, "p-");
        assertTrue(r.toolCalls().isEmpty());
        assertEquals(raw, r.visibleText());
    }

    @Test
    void encode_then_parse_roundtrip() {
        List<ChatToolCall> calls =
                List.of(
                        new ChatToolCall("", "noop", "{}"),
                        new ChatToolCall("fix", "search_tabs", "{\"query\":\"a\"}"));
        String encoded = BurpSyntheticToolCallParser.encodeSyntheticToolCalls("Intro\n", calls);
        BurpToolParseResult r = BurpSyntheticToolCallParser.parse(encoded, known, "p-");
        assertEquals("Intro", r.visibleText().trim());
        assertEquals(2, r.toolCalls().size());
        assertEquals("fix", r.toolCalls().get(1).id());
    }
}
