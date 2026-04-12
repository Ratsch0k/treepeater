package treepeater.ai.ollama;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.models.chat.OllamaChatMessage;
import io.github.ollama4j.models.chat.OllamaChatMessageRole;
import io.github.ollama4j.models.chat.OllamaChatRequest;
import io.github.ollama4j.models.chat.OllamaChatRequestBuilder;
import io.github.ollama4j.models.chat.OllamaChatResult;
import io.github.ollama4j.models.generate.OllamaTokenHandler;

import treepeater.ai.ChatMessage;
import treepeater.ai.ChatRole;
import treepeater.ai.StreamingChatClient;

/**
 * {@link StreamingChatClient} backed by a local or remote Ollama server (ollama4j).
 */
public class OllamaStreamingChatClient implements StreamingChatClient {
    private final OllamaClientConfig config;
    private final OllamaAPI api;

    public OllamaStreamingChatClient(OllamaClientConfig config) {
        this.config = config;
        this.api = new OllamaAPI(config.baseUrl());
    }

    @Override
    public List<ChatMessage> streamChat(List<ChatMessage> messages, Consumer<String> onTextDelta) throws Exception {
        List<OllamaChatMessage> ollamaMessages = new ArrayList<>(messages.size());
        for (ChatMessage m : messages) {
            ollamaMessages.add(toOllama(m));
        }

        OllamaChatRequest request =
                OllamaChatRequestBuilder.getInstance(this.config.model())
                        .withMessages(ollamaMessages)
                        .withStreaming()
                        .build();

        OllamaTokenHandler handler = chunk -> {
            OllamaChatMessage msg = chunk.getMessage();
            if (msg == null) {
                return;
            }
            String delta = msg.getContent();
            if (delta != null && !delta.isEmpty()) {
                onTextDelta.accept(delta);
            }
        };

        OllamaChatResult result = this.api.chatStreaming(request, handler);
        List<OllamaChatMessage> history = result.getChatHistory();
        if (history == null) {
            return List.of();
        }
        List<ChatMessage> out = new ArrayList<>(history.size());
        for (OllamaChatMessage om : history) {
            out.add(fromOllama(om));
        }
        return out;
    }

    private static OllamaChatMessage toOllama(ChatMessage m) {
        OllamaChatMessageRole role =
                switch (m.role()) {
                    case SYSTEM -> OllamaChatMessageRole.SYSTEM;
                    case USER -> OllamaChatMessageRole.USER;
                    case ASSISTANT -> OllamaChatMessageRole.ASSISTANT;
                    case TOOL -> OllamaChatMessageRole.TOOL;
                };
        return new OllamaChatMessage(role, m.content());
    }

    private static ChatMessage fromOllama(OllamaChatMessage m) {
        OllamaChatMessageRole r = m.getRole();
        ChatRole role;
        if (r == OllamaChatMessageRole.SYSTEM) {
            role = ChatRole.SYSTEM;
        } else if (r == OllamaChatMessageRole.USER) {
            role = ChatRole.USER;
        } else if (r == OllamaChatMessageRole.ASSISTANT) {
            role = ChatRole.ASSISTANT;
        } else if (r == OllamaChatMessageRole.TOOL) {
            role = ChatRole.TOOL;
        } else {
            role = ChatRole.USER;
        }
        String c = m.getContent() != null ? m.getContent() : "";
        return new ChatMessage(role, c);
    }
}
