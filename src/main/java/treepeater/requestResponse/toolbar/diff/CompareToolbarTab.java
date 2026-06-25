package treepeater.requestResponse.toolbar.diff;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import treepeater.Treepeater;
import treepeater.TreepeaterModel;
import treepeater.TreepeaterModelListener;
import treepeater.Utilities;
import treepeater.components.FilterableListComboBox;
import treepeater.diff.DiffChangeSummary;
import treepeater.diff.SideBySideDiffPane;
import treepeater.diff.WordDiffer;
import treepeater.icons.CompareIcon;
import treepeater.icons.RotationIcon;
import treepeater.requestResponse.HistoryEntry;
import treepeater.requestResponse.RequestHistory;
import treepeater.requestResponse.toolbar.ToolbarIconButton;
import treepeater.requestResponse.toolbar.ToolbarTabTitle;
import treepeater.tree.RequestTreeNode;

public class CompareToolbarTab {

    private static final int LAYOUT_TOGGLE_ICON_SIZE = 20;

    private final ToolbarIconButton button;
    private final JPanel content;
    private final TreepeaterModel model;

    private final FilterableListComboBox<CompareNodeOption> nodeACombo;
    private final FilterableListComboBox<CompareNodeOption> nodeBCombo;
    private final FilterableListComboBox<HistoryEntry> historyACombo;
    private final FilterableListComboBox<HistoryEntry> historyBCombo;
    private final SideBySideDiffPane requestDiffPane;
    private final SideBySideDiffPane responseDiffPane;
    private final DiffChangeSummary requestChangeSummary;
    private final DiffChangeSummary responseChangeSummary;
    private final JSplitPane requestResponseSplit;
    private final ToolbarIconButton layoutToggleButton;
    private final ActionListener nodeAListener;
    private final ActionListener nodeBListener;
    private final ActionListener historyAListener;
    private final ActionListener historyBListener;
    private boolean horizontalLayout;

    public CompareToolbarTab(TreepeaterModel model) {
        this.model = model;
        this.button = new ToolbarIconButton(new CompareIcon());
        this.content = new JPanel(new BorderLayout());

        this.nodeACombo = createNodeSearchCombo();
        this.nodeBCombo = createNodeSearchCombo();
        this.historyACombo = createHistoryCombo();
        this.historyBCombo = createHistoryCombo();
        this.requestDiffPane = new SideBySideDiffPane("Node A", "Node B");
        this.responseDiffPane = new SideBySideDiffPane("Node A", "Node B");

        DiffSection requestSection = createDiffSection("Request", this.requestDiffPane);
        DiffSection responseSection = createDiffSection("Response", this.responseDiffPane);
        this.requestChangeSummary = requestSection.summary;
        this.responseChangeSummary = responseSection.summary;
        this.requestResponseSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, requestSection.panel, responseSection.panel);
        this.requestResponseSplit.setResizeWeight(0.5);
        this.requestResponseSplit.setDividerLocation(0.5);
        this.requestResponseSplit.setBorder(BorderFactory.createEmptyBorder());

        this.layoutToggleButton = new ToolbarIconButton(new RotationIcon());
        this.layoutToggleButton.applyLocalTheme(LAYOUT_TOGGLE_ICON_SIZE);
        this.layoutToggleButton.setToolTipText("Switch between horizontal and vertical layout");
        this.layoutToggleButton.addActionListener(e -> toggleLayout());

        this.content.add(buildContent(), BorderLayout.CENTER);

        model.addListener(
                new TreepeaterModelListener() {
                    @Override
                    public void onOpenTab(RequestTreeNode node, String tabGroupId) {}

                    @Override
                    public void onNewTab(RequestTreeNode node, String tabGroupId) {}

                    @Override
                    public void onCloseTab(RequestTreeNode node) {}

                    @Override
                    public void onTreeChanged() {
                        CompareToolbarTab.this.refreshNodeList();
                    }
                });

        this.nodeAListener =
                e -> {
                    CompareNodeOption opt = CompareToolbarTab.this.nodeACombo.getSelectedItem();
                    if (opt != null) {
                        RequestTreeNode n =
                                CompareToolbarTab.this.model.findRequestNodeInTreeById(opt.requestNodeId());
                        if (n != null) {
                            bindHistoryCombo(CompareToolbarTab.this.historyACombo, n);
                        } else {
                            clearHistoryCombo(CompareToolbarTab.this.historyACombo);
                        }
                    } else {
                        clearHistoryCombo(CompareToolbarTab.this.historyACombo);
                    }
                    refreshDiffs();
                };
        this.nodeBListener =
                e -> {
                    CompareNodeOption opt = CompareToolbarTab.this.nodeBCombo.getSelectedItem();
                    if (opt != null) {
                        RequestTreeNode n =
                                CompareToolbarTab.this.model.findRequestNodeInTreeById(opt.requestNodeId());
                        if (n != null) {
                            bindHistoryCombo(CompareToolbarTab.this.historyBCombo, n);
                        } else {
                            clearHistoryCombo(CompareToolbarTab.this.historyBCombo);
                        }
                    } else {
                        clearHistoryCombo(CompareToolbarTab.this.historyBCombo);
                    }
                    refreshDiffs();
                };
        this.historyAListener = e -> refreshDiffs();
        this.historyBListener = e -> refreshDiffs();

        this.nodeACombo.addActionListener(this.nodeAListener);
        this.nodeBCombo.addActionListener(this.nodeBListener);
        this.historyACombo.addActionListener(this.historyAListener);
        this.historyBCombo.addActionListener(this.historyBListener);

        refreshNodeList();
    }

    public JButton getButton() {
        return this.button;
    }

    public JPanel getContent() {
        return this.content;
    }

    public void applyLocalTheme() {
        this.button.applyLocalTheme();
        this.layoutToggleButton.applyLocalTheme(LAYOUT_TOGGLE_ICON_SIZE);
        this.requestDiffPane.refreshTheme();
        this.responseDiffPane.refreshTheme();
        this.requestChangeSummary.refreshTheme();
        this.responseChangeSummary.refreshTheme();
        refreshDiffs();
    }

    public void refreshNodeList() {
        List<RequestTreeNode> nodes = this.model.allRequestNodesInTree();
        CompareNodeOption prevA = this.nodeACombo.getSelectedItem();
        CompareNodeOption prevB = this.nodeBCombo.getSelectedItem();
        Integer prevHistA = selectedHistoryIndex(this.historyACombo);
        Integer prevHistB = selectedHistoryIndex(this.historyBCombo);
        populateNodeCombo(this.nodeACombo, nodes);
        populateNodeCombo(this.nodeBCombo, nodes);
        if (prevA != null) {
            RequestTreeNode n = this.model.findRequestNodeInTreeById(prevA.requestNodeId());
            if (n != null) {
                runWithoutTriggeringListeners(this.nodeACombo, this.nodeAListener, () -> selectNodeInCombo(this.nodeACombo, n));
                runWithoutTriggeringListeners(
                        this.historyACombo,
                        this.historyAListener,
                        () -> bindHistoryCombo(this.historyACombo, n, prevHistA));
            } else {
                runWithoutTriggeringListeners(this.nodeACombo, this.nodeAListener, () -> this.nodeACombo.setSelectedItem(null));
                runWithoutTriggeringListeners(this.historyACombo, this.historyAListener, () -> clearHistoryCombo(this.historyACombo));
            }
        }
        if (prevB != null) {
            RequestTreeNode n = this.model.findRequestNodeInTreeById(prevB.requestNodeId());
            if (n != null) {
                runWithoutTriggeringListeners(this.nodeBCombo, this.nodeBListener, () -> selectNodeInCombo(this.nodeBCombo, n));
                runWithoutTriggeringListeners(
                        this.historyBCombo,
                        this.historyBListener,
                        () -> bindHistoryCombo(this.historyBCombo, n, prevHistB));
            } else {
                runWithoutTriggeringListeners(this.nodeBCombo, this.nodeBListener, () -> this.nodeBCombo.setSelectedItem(null));
                runWithoutTriggeringListeners(this.historyBCombo, this.historyBListener, () -> clearHistoryCombo(this.historyBCombo));
            }
        }
        refreshDiffs();
    }

    private static FilterableListComboBox<CompareNodeOption> createNodeSearchCombo() {
        FilterableListComboBox<CompareNodeOption> combo = new FilterableListComboBox<>();
        combo.setEditable(true);
        combo.setMaximumRowCount(10);
        combo.setPopupMinWidth(200);
        combo.setFilter((opt, q) -> pathMatches(q, opt.pathLabel()));
        return combo;
    }

    private static FilterableListComboBox<HistoryEntry> createHistoryCombo() {
        FilterableListComboBox<HistoryEntry> combo = new FilterableListComboBox<>();
        combo.setEditable(false);
        combo.setRenderer(new HistoryEntryListCellRenderer());
        combo.setMaximumRowCount(12);
        combo.setPopupMinWidth(280);
        combo.setEnabled(false);
        return combo;
    }

    private static void populateNodeCombo(FilterableListComboBox<CompareNodeOption> combo, List<RequestTreeNode> nodes) {
        DefaultComboBoxModel<CompareNodeOption> model = new DefaultComboBoxModel<>();
        if (nodes != null) {
            for (RequestTreeNode n : nodes) {
                if (n != null) {
                    model.addElement(CompareNodeOption.from(n));
                }
            }
        }
        combo.setModel(model);
    }

    private static void selectNodeInCombo(FilterableListComboBox<CompareNodeOption> combo, RequestTreeNode node) {
        if (node == null) {
            combo.setSelectedItem(null);
            return;
        }
        CompareNodeOption match = null;
        for (int i = 0; i < combo.getModel().getSize(); i++) {
            CompareNodeOption option = combo.getModel().getElementAt(i);
            if (option.requestNodeId() == node.getId()) {
                match = option;
                break;
            }
        }
        if (match == null) {
            match = CompareNodeOption.from(node);
            if (combo.getModel() instanceof DefaultComboBoxModel<CompareNodeOption> defaultModel) {
                defaultModel.addElement(match);
            }
        }
        combo.setSelectedItem(match);
    }

    private static void bindHistoryCombo(FilterableListComboBox<HistoryEntry> combo, RequestTreeNode node) {
        bindHistoryCombo(combo, node, null);
    }

    private static void bindHistoryCombo(
            FilterableListComboBox<HistoryEntry> combo, RequestTreeNode node, Integer preferredIndex) {
        if (node == null) {
            clearHistoryCombo(combo);
            return;
        }
        RequestHistory history = node.getHistory();
        RequestHistory.ensureSeededFromNode(node);
        List<HistoryEntry> entries = new ArrayList<>(history.size());
        for (int i = 0; i < history.size(); i++) {
            entries.add(history.getEntry(i));
        }
        combo.setModel(new DefaultComboBoxModel<>(entries.toArray(HistoryEntry[]::new)));

        HistoryEntry toSelect = null;
        if (preferredIndex != null) {
            for (HistoryEntry entry : entries) {
                if (entry.getIndex() == preferredIndex) {
                    toSelect = entry;
                    break;
                }
            }
        }
        if (toSelect == null) {
            int cur = history.getCurrentIndex();
            if (cur >= 0 && cur < entries.size()) {
                toSelect = entries.get(cur);
            } else if (!entries.isEmpty()) {
                toSelect = entries.get(entries.size() - 1);
            }
        }
        combo.setSelectedItem(toSelect);
        combo.setEnabled(!entries.isEmpty());
    }

    private static void clearHistoryCombo(FilterableListComboBox<HistoryEntry> combo) {
        combo.setModel(new DefaultComboBoxModel<>());
        combo.setSelectedItem(null);
        combo.setEnabled(false);
    }

    private static Integer selectedHistoryIndex(FilterableListComboBox<HistoryEntry> combo) {
        HistoryEntry entry = combo.getSelectedItem();
        return entry != null ? entry.getIndex() : null;
    }

    private static boolean pathMatches(String query, String path) {
        if (query == null || query.isEmpty()) {
            return true;
        }
        String p = path != null ? path : "";
        return p.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }

    private static void runWithoutTriggeringListeners(
            FilterableListComboBox<?> combo, ActionListener listener, Runnable action) {
        combo.removeActionListener(listener);
        action.run();
        combo.addActionListener(listener);
    }

    private JPanel buildContent() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel northStack = new JPanel();
        northStack.setLayout(new BoxLayout(northStack, BoxLayout.PAGE_AXIS));
        northStack.add(new ToolbarTabTitle("Compare", this.layoutToggleButton));
        northStack.add(buildSelectorBar());
        northStack.add(new JSeparator());

        panel.add(northStack, BorderLayout.NORTH);
        panel.add(this.requestResponseSplit, BorderLayout.CENTER);

        if (Treepeater.api != null) {
            Treepeater.api.userInterface().applyThemeToComponent(panel);
        }
        return panel;
    }

    private JPanel buildSelectorBar() {
        JPanel bar = new JPanel(new GridBagLayout());
        bar.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 8));
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 4, 6);

        JLabel labelA = new JLabel("Node A");
        labelA.setFont(labelA.getFont().deriveFont(Font.BOLD));
        bar.add(labelA, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 4, 4);
        this.nodeACombo.setPreferredSize(new Dimension(0, this.nodeACombo.getPreferredSize().height));
        bar.add(this.nodeACombo, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(0, 0, 4, 0);
        this.historyACombo.setPreferredSize(new Dimension(60, this.historyACombo.getPreferredSize().height));
        bar.add(this.historyACombo, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(0, 0, 0, 6);
        JLabel labelB = new JLabel("Node B");
        labelB.setFont(labelB.getFont().deriveFont(Font.BOLD));
        bar.add(labelB, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 0, 4);
        this.nodeBCombo.setPreferredSize(new Dimension(0, this.nodeBCombo.getPreferredSize().height));
        bar.add(this.nodeBCombo, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(0, 0, 0, 0);
        this.historyBCombo.setPreferredSize(new Dimension(60, this.historyBCombo.getPreferredSize().height));
        bar.add(this.historyBCombo, gbc);

        return bar;
    }

    private record DiffSection(JPanel panel, DiffChangeSummary summary) {}

    private static DiffSection createDiffSection(String title, SideBySideDiffPane pane) {
        JPanel section = new JPanel(new BorderLayout(0, 2));
        section.setOpaque(false);
        section.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 2));

        DiffChangeSummary summary = new DiffChangeSummary();

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel heading = new JLabel(title);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, heading.getFont().getSize2D() + 3f));
        heading.setBorder(BorderFactory.createEmptyBorder(0, 8, 2, 8));
        header.add(heading, BorderLayout.WEST);
        header.add(summary, BorderLayout.EAST);
        section.add(header, BorderLayout.NORTH);

        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBorder(BorderFactory.createEmptyBorder(0, 4, 4, 4));
        contentPanel.add(pane, BorderLayout.CENTER);
        section.add(contentPanel, BorderLayout.CENTER);

        return new DiffSection(section, summary);
    }

    private void toggleLayout() {
        this.horizontalLayout = !this.horizontalLayout;
        if (this.horizontalLayout) {
            this.requestResponseSplit.setOrientation(JSplitPane.HORIZONTAL_SPLIT);
        } else {
            this.requestResponseSplit.setOrientation(JSplitPane.VERTICAL_SPLIT);
        }
        this.requestResponseSplit.setDividerLocation(0.5);
        this.requestResponseSplit.revalidate();
        this.requestResponseSplit.repaint();
    }

    private void refreshDiffs() {
        CompareNodeOption a = this.nodeACombo.getSelectedItem();
        CompareNodeOption b = this.nodeBCombo.getSelectedItem();
        if (a == null || b == null) {
            this.requestDiffPane.setDiff(null);
            this.responseDiffPane.setDiff(null);
            this.requestChangeSummary.clear();
            this.responseChangeSummary.clear();
            return;
        }

        RequestTreeNode nodeA = this.model.findRequestNodeInTreeById(a.requestNodeId());
        RequestTreeNode nodeB = this.model.findRequestNodeInTreeById(b.requestNodeId());
        if (nodeA == null || nodeB == null) {
            this.requestDiffPane.setDiff(null);
            this.responseDiffPane.setDiff(null);
            this.requestChangeSummary.clear();
            this.responseChangeSummary.clear();
            return;
        }

        Integer histA = selectedHistoryIndex(this.historyACombo);
        Integer histB = selectedHistoryIndex(this.historyBCombo);
        if (histA == null || histB == null) {
            this.requestDiffPane.setDiff(null);
            this.responseDiffPane.setDiff(null);
            this.requestChangeSummary.clear();
            this.responseChangeSummary.clear();
            return;
        }

        HttpRequest reqA = resolveRequest(nodeA, Optional.of(histA));
        HttpRequest reqB = resolveRequest(nodeB, Optional.of(histB));
        HttpResponse resA = resolveResponse(nodeA, Optional.of(histA));
        HttpResponse resB = resolveResponse(nodeB, Optional.of(histB));

        String reqTextA = Utilities.decodeWireBytesToDisplayString(reqA.toByteArray());
        String reqTextB = Utilities.decodeWireBytesToDisplayString(reqB.toByteArray());
        String resTextA = Utilities.decodeWireBytesToDisplayString(resA.toByteArray());
        String resTextB = Utilities.decodeWireBytesToDisplayString(resB.toByteArray());

        WordDiffer.SideBySideDiff requestDiff = WordDiffer.diff(reqTextA, reqTextB);
        WordDiffer.SideBySideDiff responseDiff = WordDiffer.diff(resTextA, resTextB);
        this.requestDiffPane.setDiff(requestDiff);
        this.responseDiffPane.setDiff(responseDiff);
        this.requestChangeSummary.setCounts(requestDiff.removedChars(), requestDiff.addedChars());
        this.responseChangeSummary.setCounts(responseDiff.removedChars(), responseDiff.addedChars());
    }

    private HttpRequest resolveRequest(RequestTreeNode node, Optional<Integer> historyIndex) {
        if (node == null || historyIndex.isEmpty()) {
            return null;
        }
        int idx = historyIndex.get();
        RequestHistory h = node.getHistory();
        if (idx < 0 || idx >= h.size()) {
            return null;
        }
        return h.getEntry(idx).getRequest();
    }

    private HttpResponse resolveResponse(RequestTreeNode node, Optional<Integer> historyIndex) {
        if (node == null || historyIndex.isEmpty()) {
            return null;
        }
        int idx = historyIndex.get();
        RequestHistory h = node.getHistory();
        if (idx < 0 || idx >= h.size()) {
            return null;
        }
        return h.getEntry(idx).getResponse();
    }
}
