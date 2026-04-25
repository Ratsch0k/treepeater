package treepeater.requestResponse.toolbar.ai;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;

import javax.swing.JComponent;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Supplier;

import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import treepeater.Treepeater;
import treepeater.ai.AgentMode;
import treepeater.ai.AgentModeToolPolicy;
import treepeater.ai.AgentToolContext;
import treepeater.ai.AiModelOption;
import treepeater.ai.LlmRequestOptions;
import treepeater.ai.ChatToolExecutor;
import treepeater.ai.ChatTooling;
import treepeater.ai.HttpTargetTools;
import treepeater.ai.StreamingChatClient;
import treepeater.ai.anthropic.AnthropicClientConfig;
import treepeater.ai.anthropic.AnthropicStreamingChatClient;
import treepeater.ai.burp.BurpAiStreamingChatClient;
import treepeater.ai.openai.OpenAiClientConfig;
import treepeater.ai.openai.OpenAiStreamingChatClient;
import treepeater.ai.ollama.OllamaClientConfig;
import treepeater.ai.ollama.OllamaStreamingChatClient;
import treepeater.components.StyledButton;
import treepeater.icons.WandIcon;
import treepeater.requestResponse.toolbar.ToolbarIconButton;
import treepeater.requestResponse.toolbar.ToolbarTabTitle;
import treepeater.settings.TreepeaterSettings;

public class AIToolbarTab implements AIChatHost {

    private final ToolbarIconButton button;
    private final JPanel content;
    private final JTextArea disabledInfoArea;
    private JTabbedPane chatTabPane;
    private int nextChatTabIndex = 1;

    private final Supplier<AgentToolContext> agentToolContextSupplier;

    public AIToolbarTab(Supplier<AgentToolContext> agentToolContextSupplier) {
        this.button = new ToolbarIconButton(new WandIcon());
        this.content = new JPanel(new BorderLayout());

        this.agentToolContextSupplier = agentToolContextSupplier;
        this.disabledInfoArea = null;

        this.content.add(this.buildContent(), BorderLayout.CENTER);
    }

    @Override
    public StreamingChatClient clientForSelectedModel(JComboBox<AiModelOption> modelCombo, LlmRequestOptions options) {
        LlmRequestOptions o = options != null ? options : LlmRequestOptions.DEFAULTS;
        AiModelOption opt = (AiModelOption) modelCombo.getSelectedItem();
        if (opt == null || Treepeater.api == null) {
            throw new IllegalStateException("No model or API");
        }
        TreepeaterSettings settings = TreepeaterSettings.getInstance();
        if (opt.kind() == AiModelOption.Kind.BURP) {
            return new BurpAiStreamingChatClient(Treepeater.api);
        }
        if (opt.kind() == AiModelOption.Kind.ANTHROPIC) {
            String apiKey = settings.getLlmAnthropicApiKey();
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException("Anthropic API key not configured");
            }
            String model = opt.anthropicModel();
            if (model == null || model.isBlank()) {
                throw new IllegalStateException("No Anthropic model id");
            }
            return new AnthropicStreamingChatClient(
                    new AnthropicClientConfig(
                            apiKey, model, o.anthropicExtendedThinking(), o.anthropicOutputEffort()));
        }
        if (opt.kind() == AiModelOption.Kind.OPENAI) {
            String endpoint = settings.getLlmAzureOpenAiEndpoint();
            if (endpoint == null || endpoint.isBlank()) {
                throw new IllegalStateException("Azure OpenAI endpoint not configured");
            }
            String apiKey = settings.getLlmAzureOpenAiApiKey();
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException("Azure OpenAI API key not configured");
            }
            String deployment = opt.openAiDeployment();
            if (deployment == null || deployment.isBlank()) {
                throw new IllegalStateException("No deployment name for Azure OpenAI");
            }
            return new OpenAiStreamingChatClient(
                    new OpenAiClientConfig(endpoint, apiKey, deployment, o.openAiReasoningEffort()));
        }
        String baseUrl = settings.getLlmOllamaBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Ollama base URL not configured");
        }
        String model = opt.ollamaModel();
        if (model == null || model.isBlank()) {
            throw new IllegalStateException("No Ollama model id");
        }
        return new OllamaStreamingChatClient(new OllamaClientConfig(baseUrl, model));
    }

    /** Built-in HTTP target tools; approval depends on {@link AgentMode}. */
    @Override
    public ChatTooling chatTooling(AgentMode mode) {
        if (this.agentToolContextSupplier == null) {
            return ChatTooling.none();
        }
        AgentMode m = mode != null ? mode : AgentMode.ASK;
        ChatToolExecutor exec =
                (name, argsJson) -> HttpTargetTools.execute(name, argsJson, this.agentToolContextSupplier.get());
        return new ChatTooling(
                HttpTargetTools.definitions(),
                exec,
                () -> {
                    AgentToolContext c = this.agentToolContextSupplier.get();
                    return c != null ? c.currentHistoryIndex() : Integer.MIN_VALUE;
                },
                new AgentModeToolPolicy(m));
    }

    @Override
    public void runOnEdtAndWait(Runnable r) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(r);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof Exception ex) {
                throw ex;
            }
            if (c instanceof Error err) {
                throw err;
            }
            throw new RuntimeException(c);
        }
    }

    @Override
    public Component dialogParent() {
        return this.content;
    }

    @Override
    public void logError(Throwable t) {
        if (Treepeater.api != null) {
            Treepeater.api.logging().logToError(t);
        }
    }

    private static void applyDisabledInfoAreaTheme(JTextArea area) {
        Color fg = UIManager.getColor("Label.foreground");
        if (fg != null) {
            area.setForeground(fg);
        }
    }

    public JButton getButton() {
        return this.button;
    }

    public JPanel getContent() {
        return this.content;
    }

    public void applyLocalTheme() {
        this.button.applyLocalTheme();
        if (this.disabledInfoArea != null) {
            applyDisabledInfoAreaTheme(this.disabledInfoArea);
        }
        if (this.chatTabPane != null) {
            for (int i = 0; i < this.chatTabPane.getTabCount(); i++) {
                Component tabTitle = this.chatTabPane.getTabComponentAt(i);
                if (tabTitle instanceof JComponent j) {
                    j.updateUI();
                }
                Component c = this.chatTabPane.getComponentAt(i);
                if (c instanceof AIAgentChatPanel s) {
                    s.refreshTranscriptThemes();
                    s.applyInputPanelTheme();
                    SwingUtilities.invokeLater(s::adjustInputAreaHeight);
                }
            }
        }
    }

    private JPanel buildContent() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel northStack = new JPanel();
        northStack.setLayout(new BoxLayout(northStack, BoxLayout.PAGE_AXIS));
        northStack.setOpaque(false);
        StyledButton newChatButton = new StyledButton("New chat");
        newChatButton.setStyle(StyledButton.Style.DEFAULT);
        newChatButton.addActionListener(e -> addNewChatTab());
        northStack.add(new ToolbarTabTitle("AI", newChatButton));

        panel.add(northStack, BorderLayout.NORTH);

        this.chatTabPane = new JTabbedPane();
        this.chatTabPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        addNewChatTab();
        panel.add(this.chatTabPane, BorderLayout.CENTER);
        return panel;
    }

    private void addNewChatTab() {
        if (this.chatTabPane == null) {
            return;
        }
        AIAgentChatPanel session = new AIAgentChatPanel(this);
        String title = "Chat " + this.nextChatTabIndex++;
        int index = this.chatTabPane.getTabCount();
        this.chatTabPane.addTab(title, session);
        this.chatTabPane.setTabComponentAt(index, new AIAgentChatTabTitle(title, () -> this.closeAgentChat(session)));
        this.chatTabPane.setSelectedComponent(session);
    }

    private void closeAgentChat(AIAgentChatPanel panel) {
        if (this.chatTabPane == null || panel == null) {
            return;
        }
        if (this.chatTabPane.indexOfComponent(panel) < 0) {
            return;
        }
        panel.cancelInFlightChat();
        this.chatTabPane.remove(panel);
        if (this.chatTabPane.getTabCount() == 0) {
            addNewChatTab();
        }
    }
}
