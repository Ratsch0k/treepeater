package treepeater.ai;

import java.util.ArrayList;
import java.util.List;

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

    @Override
    public String toString() {
        return this.label;
    }

    /**
     * Burp first (default), then Anthropic presets, then Ollama presets for the configured base URL.
     */
    public static List<AiModelOption> defaultChoices() {
        List<AiModelOption> list = new ArrayList<>();
        list.add(new AiModelOption("Burp", Kind.BURP, null, null, null));
        list.add(new AiModelOption("Anthropic — Claude Sonnet 4", Kind.ANTHROPIC, null, "claude-sonnet-4-20250514", null));
        list.add(new AiModelOption("Anthropic — Claude Haiku 4.5", Kind.ANTHROPIC, null, "claude-haiku-4-5-20251001", null));
        list.add(new AiModelOption("Foundry — gpt-4o (deployment name)", Kind.OPENAI, null, null, "gpt-4o"));
        list.add(new AiModelOption("Foundry — o4-mini (deployment name)", Kind.OPENAI, null, null, "o4-mini"));
        String[] ollamaModels = {"qwen3.5", "llama3.2", "mistral", "codellama"};
        for (String model : ollamaModels) {
            list.add(new AiModelOption("Ollama — " + model, Kind.OLLAMA, model, null, null));
        }
        return list;
    }
}
