package treepeater.ai;

import java.util.ArrayList;
import java.util.List;

import treepeater.settings.TreepeaterSettings;


/**
 * Entry in the AI toolbar model combo: Burp's built-in AI, Anthropic, Azure OpenAI / Foundry, or a specific Ollama model.
 */
public record AiModelOption(String label, Kind kind, String ollamaModel, String anthropicModel, String openAiDeployment) {
    public enum Kind {
        BURP,
        ANTHROPIC,
        OPENAI,
        OLLAMA
    }

    public static final String DEFAULT_OLLAMA_BASE_URL = "http://127.0.0.1:11434";

    public static final String[] FALLBACK_OLLAMA_MODELS = {"qwen3.5", "llama3.2", "mistral", "codellama"};

    @Override
    public String toString() {
        return this.label;
    }

    /**
     * Burp first (default), then Anthropic presets, then Ollama models from user settings.
     */
    public static List<AiModelOption> defaultChoices() {
        List<AiModelOption> list = new ArrayList<>();
        list.add(new AiModelOption("Burp", Kind.BURP, null, null, null));
        list.add(new AiModelOption("Claude Opus 4.7", Kind.ANTHROPIC, null, com.anthropic.models.messages.Model.CLAUDE_OPUS_4_7.asString(), null));
        list.add(new AiModelOption("Claude Sonnet 4.6", Kind.ANTHROPIC, null, com.anthropic.models.messages.Model.CLAUDE_SONNET_4_6.asString(), null));
        list.add(new AiModelOption("Claude Haiku 4.5", Kind.ANTHROPIC, null, com.anthropic.models.messages.Model.CLAUDE_HAIKU_4_5.asString(), null));
        list.add(new AiModelOption("GPT-5.4", Kind.OPENAI, null, null, com.openai.models.ChatModel.GPT_5_4.asString()));
        list.add(new AiModelOption("GPT-5.4 mini", Kind.OPENAI, null, null, com.openai.models.ChatModel.GPT_5_4_MINI.asString()));
        list.add(new AiModelOption("GPT-5.3", Kind.OPENAI, null, null, com.openai.models.ChatModel.GPT_5_3_CHAT_LATEST.asString()));
        for (String model : getConfiguredOllamaModels()) {
            list.add(new AiModelOption(model + " (Ollama)", Kind.OLLAMA, model, null, null));
        }
        return list;
    }

    private static List<String> getConfiguredOllamaModels() {
        try {
            List<String> configured = TreepeaterSettings.getInstance().getOllamaModels();
            if (configured != null) {
                return configured;
            }
        } catch (IllegalStateException ignored) {
        }
        return List.of(FALLBACK_OLLAMA_MODELS);
    }
}
