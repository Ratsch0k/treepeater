package treepeater.ai.burp;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ai.chat.Message;
import burp.api.montoya.ai.chat.PromptResponse;

import treepeater.ai.ChatMessage;
import treepeater.ai.ChatRole;
import treepeater.ai.StreamingChatClient;

/**
 * {@link StreamingChatClient} backed by Burp Suite's built-in AI (Montoya {@code api.ai()}).
 * <p>
 * Burp's prompt API returns a complete response; there is no token stream, so {@code onTextDelta}
 * receives the full assistant text in one segment.
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
    public List<ChatMessage> streamChat(List<ChatMessage> messages, Consumer<String> onTextDelta) throws Exception {
        if (!this.api.ai().isEnabled()) {
            throw new IllegalStateException(
                    "Burp AI is not available. Enable AI for this extension under Extensions, "
                            + "or ensure your Burp edition supports AI features.");
        }
        Message[] burpMessages = new Message[messages.size()];
        for (int i = 0; i < messages.size(); i++) {
            burpMessages[i] = toBurpMessage(messages.get(i));
        }
        PromptResponse response = this.api.ai().prompt().execute(burpMessages);
        String text = response.content();
        if (text != null && !text.isEmpty()) {
            onTextDelta.accept(text);
        }
        List<ChatMessage> history = new ArrayList<>(messages.size() + 1);
        history.addAll(messages);
        history.add(new ChatMessage(ChatRole.ASSISTANT, text != null ? text : ""));
        return history;
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
