package treepeater.ai.burp;

import java.util.ArrayList;
import java.util.List;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ai.chat.Message;
import burp.api.montoya.ai.chat.PromptResponse;

import treepeater.ai.ChatMessage;
import treepeater.ai.ChatRole;
import treepeater.ai.ChatStreamMessage;
import treepeater.ai.ChatStreamSession;
import treepeater.ai.ChatToolCall;
import treepeater.ai.ChatToolDefinition;
import treepeater.ai.ChatTooling;
import treepeater.ai.StreamingChatClient;

/**
 * {@link StreamingChatClient} backed by Burp Suite's built-in AI (Montoya {@code api.ai()}).
 * <p>
 * Burp's prompt API returns a complete response; there is no token stream, so assistant text is delivered
 * as a single {@link ChatStreamMessage.AssistantDelta}.
 * <p>
 * Tool calling is not available from Montoya; when tools are requested we inject the same tool
 * outputs into a leading system message so the model can use current target data.
 */
public class BurpAiStreamingChatClient implements StreamingChatClient {
    private final MontoyaApi api;

    public BurpAiStreamingChatClient(MontoyaApi api) {
        if (api == null) {
            throw new IllegalArgumentException("MontoyaApi is required");
        }
        this.api = api;
    }

    @Override
    public List<ChatMessage> streamChat(
            List<ChatMessage> messages, ChatTooling tooling, ChatStreamSession session) throws Exception {
        if (!this.api.ai().isEnabled()) {
            throw new IllegalStateException(
                    "Burp AI is not available. Enable AI for this extension under Extensions, "
                            + "or ensure your Burp edition supports AI features.");
        }
        List<ChatMessage> toSend = messages;
        if (tooling != null && tooling.isActive()) {
            toSend = new ArrayList<>(messages.size() + 1);
            toSend.add(new ChatMessage(ChatRole.SYSTEM, buildToolInject(tooling, session)));
            toSend.addAll(messages);
        }
        Message[] burpMessages = new Message[toSend.size()];
        for (int i = 0; i < toSend.size(); i++) {
            burpMessages[i] = toBurpMessage(toSend.get(i));
        }
        PromptResponse response = this.api.ai().prompt().execute(burpMessages);
        String text = response.content();
        if (text != null && !text.isEmpty()) {
            session.emit(new ChatStreamMessage.AssistantDelta(text));
        }
        List<ChatMessage> history = new ArrayList<>(messages.size() + 1);
        history.addAll(messages);
        history.add(new ChatMessage(ChatRole.ASSISTANT, text != null ? text : ""));
        return history;
    }

    private static String buildToolInject(ChatTooling tooling, ChatStreamSession session) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("Treepeater tool context (Burp AI does not expose native tool calls; results are pre-filled):\n");
        for (ChatToolDefinition def : tooling.tools()) {
            sb.append("\n--- ").append(def.name()).append(" ---\n");
            sb.append(tooling.executeWithApproval(new ChatToolCall("", def.name(), "{}"), session));
            sb.append('\n');
        }
        return sb.toString();
    }

    private static Message toBurpMessage(ChatMessage m) {
        return switch (m.role()) {
            case SYSTEM -> Message.systemMessage(m.content());
            case USER -> Message.userMessage(m.content());
            case ASSISTANT -> Message.assistantMessage(m.content());
            case TOOL -> Message.userMessage(m.content());
        };
    }
}
