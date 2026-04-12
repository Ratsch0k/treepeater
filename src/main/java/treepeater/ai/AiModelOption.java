package treepeater.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * Entry in the AI toolbar model combo: Burp's built-in AI or a specific Ollama model.
 */
public record AiModelOption(String label, Kind kind, String ollamaModel) {
    public enum Kind {
        BURP,
        OLLAMA
    }

    public static final String DEFAULT_OLLAMA_BASE_URL = "http://127.0.0.1:11434";

    @Override
    public String toString() {
        return this.label;
    }

    /**
     * Burp first (default), then Ollama presets for the configured base URL.
     */
    public static List<AiModelOption> defaultChoices() {
        List<AiModelOption> list = new ArrayList<>();
        list.add(new AiModelOption("Burp", Kind.BURP, null));
        String[] ollamaModels = {"qwen3.5", "llama3.2", "mistral", "codellama"};
        for (String model : ollamaModels) {
            list.add(new AiModelOption("Ollama — " + model, Kind.OLLAMA, model));
        }
        return list;
    }
}
