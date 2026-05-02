package treepeater.ai.burp;

import java.util.List;
import java.util.Optional;

import treepeater.Treepeater;
import treepeater.ai.StreamingChatClient;
import treepeater.ai.model.LlmModelDefinition;
import treepeater.ai.model.LlmModelOptionValues;
import treepeater.ai.model.LlmProvider;

/**
 * Built-in Burp Suite AI ({@code Treepeater.api.ai()}). Single catalog entry, no configurable
 * options — selecting it just routes to {@link BurpAiStreamingChatClient}.
 */
public final class BurpProvider implements LlmProvider {
    public static final String ID = "burp";

    private final LlmModelDefinition model;

    public BurpProvider() {
        this.model = new LlmModelDefinition(
                this, "", "Burp", List.of(), null, LlmModelOptionValues.EMPTY);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Burp";
    }

    @Override
    public List<LlmModelDefinition> models() {
        return List.of(this.model);
    }

    @Override
    public Optional<UnavailableReason> unavailableReason(LlmModelDefinition model) {
        if (Treepeater.api == null || !Treepeater.api.ai().isEnabled()) {
            return Optional.of(new UnavailableReason(
                    "Burp AI unavailable",
                    "Enable Burp's AI for this extension under Extensions (Use AI), "
                            + "or choose an Ollama model."));
        }
        return Optional.empty();
    }

    @Override
    public StreamingChatClient createClient(LlmModelDefinition model, LlmModelOptionValues values) {
        if (Treepeater.api == null) {
            throw new IllegalStateException("Burp API not available");
        }
        return new BurpAiStreamingChatClient(Treepeater.api);
    }
}
