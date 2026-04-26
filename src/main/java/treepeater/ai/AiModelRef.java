package treepeater.ai;

import java.util.List;

/**
 * Serializable identity for {@link AiModelOption} (used for persisting agent chat sessions).
 */
public record AiModelRef(
        String label,
        AiModelOption.Kind kind,
        String ollamaModel,
        String anthropicModel,
        String openAiDeployment) {
    public static AiModelRef fromOption(AiModelOption o) {
        if (o == null) {
            return new AiModelRef("Burp", AiModelOption.Kind.BURP, null, null, null);
        }
        return new AiModelRef(o.label(), o.kind(), o.ollamaModel(), o.anthropicModel(), o.openAiDeployment());
    }

    /**
     * Resolves a combo entry: prefer a built-in default choice, otherwise synthesize a label-backed option (e.g. Ollama
     * model from settings).
     */
    public AiModelOption toModelOption() {
        List<AiModelOption> defaults = AiModelOption.defaultChoices();
        for (AiModelOption o : defaults) {
            if (kind != o.kind()) {
                continue;
            }
            if (kind == AiModelOption.Kind.OLLAMA) {
                if (ollamaModel != null
                        && ollamaModel.equals(o.ollamaModel())
                        && (label == null || label.equals(o.label()))) {
                    return o;
                }
            } else if (kind == AiModelOption.Kind.ANTHROPIC) {
                if (anthropicModel != null && anthropicModel.equals(o.anthropicModel())) {
                    return o;
                }
            } else if (kind == AiModelOption.Kind.OPENAI) {
                if (openAiDeployment != null && openAiDeployment.equals(o.openAiDeployment())) {
                    return o;
                }
            } else if (kind == AiModelOption.Kind.BURP) {
                return o;
            }
        }
        String lab = label != null && !label.isEmpty() ? label : "Model";
        return new AiModelOption(lab, kind, ollamaModel, anthropicModel, openAiDeployment);
    }
}
