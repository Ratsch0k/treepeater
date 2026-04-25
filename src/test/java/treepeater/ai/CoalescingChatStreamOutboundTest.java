package treepeater.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class CoalescingChatStreamOutboundTest {

    @Test
    void interleavedDeltas_flushInStreamOrderOnShutdown() {
        List<ChatStreamMessage> out = new ArrayList<>();
        CoalescingChatStreamOutbound c = new CoalescingChatStreamOutbound(out::add);
        c.accept(new ChatStreamMessage.AssistantDelta("a"));
        c.accept(new ChatStreamMessage.ThinkingDelta("t"));
        c.accept(new ChatStreamMessage.AssistantDelta("b"));
        c.shutdown();

        assertEquals(3, out.size());
        assertInstanceOf(ChatStreamMessage.AssistantDelta.class, out.get(0));
        assertEquals("a", ((ChatStreamMessage.AssistantDelta) out.get(0)).text());
        assertInstanceOf(ChatStreamMessage.ThinkingDelta.class, out.get(1));
        assertEquals("t", ((ChatStreamMessage.ThinkingDelta) out.get(1)).text());
        assertInstanceOf(ChatStreamMessage.AssistantDelta.class, out.get(2));
        assertEquals("b", ((ChatStreamMessage.AssistantDelta) out.get(2)).text());
    }

    @Test
    void nonDelta_flushesBothBuffersFirst() {
        List<ChatStreamMessage> out = new ArrayList<>();
        CoalescingChatStreamOutbound c = new CoalescingChatStreamOutbound(out::add);
        c.accept(new ChatStreamMessage.AssistantDelta("x"));
        c.accept(new ChatStreamMessage.ThinkingDelta("y"));
        c.accept(
                new ChatStreamMessage.ToolApprovalRequest(
                        "id", "tool", "{}", "t", "d", false));

        assertTrue(out.size() >= 3);
        assertInstanceOf(ChatStreamMessage.AssistantDelta.class, out.get(0));
        assertEquals("x", ((ChatStreamMessage.AssistantDelta) out.get(0)).text());
        assertInstanceOf(ChatStreamMessage.ThinkingDelta.class, out.get(1));
        assertEquals("y", ((ChatStreamMessage.ThinkingDelta) out.get(1)).text());
        assertInstanceOf(ChatStreamMessage.ToolApprovalRequest.class, out.get(2));
        c.shutdown();
    }
}
