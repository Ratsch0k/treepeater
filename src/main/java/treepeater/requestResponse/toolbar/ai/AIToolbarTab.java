package treepeater.requestResponse.toolbar.ai;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.JComponent;
import java.lang.reflect.InvocationTargetException;

import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import treepeater.Treepeater;
import treepeater.ai.AgentChatSession;
import treepeater.ai.AgentChatWorkspace;
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
import treepeater.tree.RequestTreeNode;

public class AIToolbarTab implements AIChatHost {

    private final ToolbarIconButton button;
    private final JPanel content;
    private final JTextArea disabledInfoArea;
    private JTabbedPane chatTabPane;
    private int nextChatTabIndex = 1;

    private final RequestTreeNode node;
    private final Supplier<AgentToolContext> agentToolContextSupplier;
    private boolean blockTabPersist;

    public AIToolbarTab(RequestTreeNode node, Supplier<AgentToolContext> agentToolContextSupplier) {
        this.node = node;
        this.button = new ToolbarIconButton(new WandIcon());
        this.content = new JPanel(new BorderLayout());

        this.agentToolContextSupplier = agentToolContextSupplier;
        this.disabledInfoArea = null;

        this.content.add(this.buildContent(), BorderLayout.CENTER);
    }

    private void saveWorkspaceToNode() {
        if (this.node == null || this.chatTabPane == null) {
            return;
        }
        if (this.blockTabPersist) {
            return;
        }
        int n = this.chatTabPane.getTabCount();
        List<AgentChatSession> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Component c = this.chatTabPane.getComponentAt(i);
            if (c instanceof AIAgentChatPanel p) {
                list.add(p.toSessionSnapshot(this.chatTabPane.getTitleAt(i)));
            }
        }
        this.node.setAgentChatWorkspace(
                new AgentChatWorkspace(
                        list, this.chatTabPane.getSelectedIndex(), this.nextChatTabIndex));
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
    public AgentToolContext agentToolContextForToolPreview() {
        return this.agentToolContextSupplier != null ? this.agentToolContextSupplier.get() : null;
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
        AgentChatWorkspace ws = this.node != null ? this.node.getAgentChatWorkspace() : AgentChatWorkspace.EMPTY;
        this.nextChatTabIndex = Math.max(1, ws.nextChatTabIndex());
        this.blockTabPersist = true;
        try {
            if (ws.sessions().isEmpty()) {
                this.addNewChatTab();
            } else {
                for (AgentChatSession s : ws.sessions()) {
                    AIAgentChatPanel session = new AIAgentChatPanel(this, this::saveWorkspaceToNode);
                    String title = s.title();
                    int index = this.chatTabPane.getTabCount();
                    this.chatTabPane.addTab(title, session);
                    this.chatTabPane.setTabComponentAt(
                            index, new AIAgentChatTabTitle(title, () -> this.closeAgentChat(session)));
                    session.loadFromSession(s);
                }
                int sel = ws.selectedSessionIndex();
                if (sel >= 0 && sel < this.chatTabPane.getTabCount()) {
                    this.chatTabPane.setSelectedIndex(sel);
                }
            }
        } finally {
            this.blockTabPersist = false;
        }
        this.chatTabPane.addChangeListener(
                new ChangeListener() {
                    @Override
                    public void stateChanged(ChangeEvent e) {
                        AIToolbarTab.this.saveWorkspaceToNode();
                    }
                });
        panel.add(this.chatTabPane, BorderLayout.CENTER);
        return panel;
    }

    private void addNewChatTab() {
        if (this.chatTabPane == null) {
            return;
        }
        AIAgentChatPanel session = new AIAgentChatPanel(this, this::saveWorkspaceToNode);
        String title = "Chat " + this.nextChatTabIndex++;
        int index = this.chatTabPane.getTabCount();
        this.chatTabPane.addTab(title, session);
        this.chatTabPane.setTabComponentAt(index, new AIAgentChatTabTitle(title, () -> this.closeAgentChat(session)));
        this.chatTabPane.setSelectedComponent(session);
        this.saveWorkspaceToNode();
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
        } else {
            this.saveWorkspaceToNode();
        }
    }
}
