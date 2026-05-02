package treepeater.ai.burp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ai.chat.Message;
import burp.api.montoya.ai.chat.PromptResponse;

import treepeater.ai.ChatMessage;
import treepeater.ai.ChatRole;
import treepeater.ai.ChatStreamMessage;
import treepeater.ai.ChatStreamSession;
import treepeater.ai.ChatToolDefinition;
import treepeater.ai.ChatTooling;
import treepeater.ai.ParallelToolExecution;
import treepeater.ai.StreamingChatClient;

/**
 * {@link StreamingChatClient} backed by Burp Suite's built-in AI (Montoya {@code api.ai()}).
 * <p>
 * Burp's prompt API returns a complete response; there is no token stream, so assistant text is delivered
 * as a single {@link ChatStreamMessage.AssistantDelta}.
 * <p>
 * Montoya does not expose tool calls. When tools are active, a Burp-only leading system message describes
 * synthetic {@link BurpSyntheticToolCallParser} blocks; the assistant reply is parsed and rounds mirror
 * native tool providers via {@link ParallelToolExecution}.
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
        Objects.requireNonNull(messages, "messages");
        if (!this.api.ai().isEnabled()) {
            throw new IllegalStateException(
                    "Burp AI is not available. Enable AI for this extension under Extensions, "
                            + "or ensure your Burp edition supports AI features.");
        }
        if (tooling == null || !tooling.isActive()) {
            return streamOncePlain(messages, session);
        }
        String burpToolInstructions = buildBurpSyntheticToolInstructions(tooling);
        Set<String> knownNames = new HashSet<>();
        for (ChatToolDefinition def : tooling.tools()) {
            knownNames.add(def.name());
        }
        List<ChatMessage> work = new ArrayList<>(messages);
        for (int round = 0; round < StreamingChatClient.MAX_AGENT_TOOL_ROUNDS; round++) {
            if (session.isClosed() || Thread.currentThread().isInterrupted()) {
                return work;
            }
            Message[] burpMessages = toBurpMessages(burpToolInstructions, work);
            PromptResponse response = this.api.ai().prompt().execute(burpMessages);
            String text = response.content() != null ? response.content() : "";
            BurpToolParseResult parsed =
                    BurpSyntheticToolCallParser.parse(text, knownNames, "burp-tool-" + round + "-");
            if (!parsed.visibleText().isEmpty()) {
                session.emit(new ChatStreamMessage.AssistantDelta(parsed.visibleText()));
            }
            if (parsed.toolCalls().isEmpty()) {
                work.add(new ChatMessage(ChatRole.ASSISTANT, parsed.visibleText()));
                return work;
            }
            work.add(new ChatMessage(ChatRole.ASSISTANT, parsed.visibleText(), parsed.toolCalls(), null));
            List<String> results = ParallelToolExecution.executeRound(parsed.toolCalls(), tooling, session);
            for (int i = 0; i < parsed.toolCalls().size(); i++) {
                if (session.isClosed() || Thread.currentThread().isInterrupted()) {
                    return work;
                }
                work.add(
                        new ChatMessage(
                                ChatRole.TOOL,
                                results.get(i),
                                List.of(),
                                parsed.toolCalls().get(i).id()));
            }
        }
        return work;
    }

    private List<ChatMessage> streamOncePlain(List<ChatMessage> messages, ChatStreamSession session) throws Exception {
        Message[] burpMessages = toBurpMessages(null, messages);
        PromptResponse response = this.api.ai().prompt().execute(burpMessages);
        String text = response.content() != null ? response.content() : "";
        if (!text.isEmpty()) {
            session.emit(new ChatStreamMessage.AssistantDelta(text));
        }
        List<ChatMessage> history = new ArrayList<>(messages.size() + 1);
        history.addAll(messages);
        history.add(new ChatMessage(ChatRole.ASSISTANT, text));
        return history;
    }

    private static String buildBurpSyntheticToolInstructions(ChatTooling tooling) {
        StringBuilder sb = new StringBuilder();
        sb.append(
                "Treepeater / Burp AI: tools are not available through Montoya's API, so Treepeater interprets "
                        + "tool calls you write as text using the fenced format below.\n\n");
        sb.append(
                "To call a tool, output one or more blocks exactly in this shape (first and last lines are the "
                        + "markers, with a single JSON object between them):\n\n");
        sb.append(BurpSyntheticToolCallParser.START_MARKER).append('\n');
        sb.append(
                "{\"name\":\"<tool_name>\",\"arguments\":{...},\"id\":\"<optional_stable_id>\"}\n");
        sb.append(BurpSyntheticToolCallParser.END_MARKER).append('\n');
        sb.append('\n');
        sb.append(
                "After any short plan, group all <<<TREEPEATER_TOOL>>> blocks together. The "
                        + "\"arguments\" value must be a JSON object matching that tool's schema (use {} when there "
                        + "are no parameters).\n\n");
        sb.append(
                "If a block is not valid JSON, is missing an object-valued \"arguments\" field, uses a tool name "
                        + "that is not listed below, or is missing the closing marker, it is not executed and is left "
                        + "in your reply so the user can see it.\n\n");
        sb.append("Available tools:\n");
        for (ChatToolDefinition def : tooling.tools()) {
            sb.append("\n## ").append(def.name()).append('\n');
            sb.append(def.description()).append('\n');
            sb.append("Parameters (JSON Schema): ").append(def.parametersJsonSchema()).append('\n');
        }
        return sb.toString();
    }

    /**
     * @param leadingSystem Burp-only tool instructions, or {@code null} to omit
     */
    private static Message[] toBurpMessages(String leadingSystem, List<ChatMessage> work) {
        List<Message> out = new ArrayList<>(work.size() + 1);
        if (leadingSystem != null && !leadingSystem.isEmpty()) {
            out.add(Message.systemMessage(leadingSystem));
        }
        for (ChatMessage m : work) {
            out.add(chatMessageToBurp(m));
        }
        return out.toArray(Message[]::new);
    }

    private static Message chatMessageToBurp(ChatMessage m) {
        return switch (m.role()) {
            case SYSTEM -> Message.systemMessage(m.content());
            case USER -> Message.userMessage(m.content());
            case ASSISTANT -> {
                if (m.hasAssistantToolCalls()) {
                    yield Message.assistantMessage(
                            BurpSyntheticToolCallParser.encodeSyntheticToolCalls(
                                    m.content(), m.assistantToolCalls()));
                }
                yield Message.assistantMessage(m.content());
            }
            case TOOL -> Message.userMessage(formatToolTurnForBurp(m));
        };
    }

    private static String formatToolTurnForBurp(ChatMessage m) {
        String idPart = m.toolCallId() != null && !m.toolCallId().isBlank() ? m.toolCallId() : "?";
        return "Treepeater tool result (tool call id: " + idPart + "):\n" + m.content();
    }
}
