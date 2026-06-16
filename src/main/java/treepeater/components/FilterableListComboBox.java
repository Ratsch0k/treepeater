package treepeater.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

import javax.swing.BorderFactory;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import treepeater.Utilities;
import treepeater.requestResponse.RequestResponsePanelUi;

/**
 * Combo-box-like control with a text field, drop button, and popup list. Mirrors core {@link
 * javax.swing.JComboBox} APIs ({@link ComboBoxModel}, {@link ListCellRenderer}, {@link
 * ActionListener}, {@code setEditable}).
 */
public class FilterableListComboBox<T> extends JPanel {

    private static final int POPUP_ROW_HEIGHT = 22;
    private static final int DEFAULT_MAXIMUM_ROW_COUNT = 10;
    private static final int DEFAULT_POPUP_MIN_WIDTH = 200;

    private final JTextField editorField;
    private final JButton dropButton;
    private final JPopupMenu popup;
    private final JScrollPane popupScroll;
    private final JList<T> popupList;
    private final DefaultListModel<T> visibleModel = new DefaultListModel<>();
    private final List<ActionListener> actionListeners = new ArrayList<>();

    private ComboBoxModel<T> dataModel = new DefaultComboBoxModel<>();
    private ListCellRenderer<? super T> renderer = new DefaultListCellRenderer();
    private BiPredicate<T, String> filter = (item, query) -> true;

    private T selectedItem;
    private boolean editable;
    private boolean suppressFieldEvents;
    private int maximumRowCount = DEFAULT_MAXIMUM_ROW_COUNT;
    private int popupMinWidth = DEFAULT_POPUP_MIN_WIDTH;

    private final ListDataListener modelListener =
            new ListDataListener() {
                @Override
                public void intervalAdded(ListDataEvent e) {
                    onModelChanged();
                }

                @Override
                public void intervalRemoved(ListDataEvent e) {
                    onModelChanged();
                }

                @Override
                public void contentsChanged(ListDataEvent e) {
                    onModelChanged();
                }
            };

    public FilterableListComboBox() {
        this(new DefaultComboBoxModel<>());
    }

    @SafeVarargs
    public FilterableListComboBox(T... items) {
        this(new DefaultComboBoxModel<>(items));
    }

    public FilterableListComboBox(ComboBoxModel<T> model) {
        super(new BorderLayout(0, 0));

        this.editorField = new JTextField();
        this.editorField.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 4));
        this.dropButton = new JButton("\u25be");
        this.dropButton.setMargin(new Insets(0, 4, 0, 4));
        RequestResponsePanelUi.styleAsFlatButton(this.dropButton);
        RequestResponsePanelUi.installHoverBackground(this.dropButton);

        this.popupList = new JList<>(this.visibleModel);
        this.popupList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        this.popupList.setFocusable(false);
        this.popupList.setFixedCellHeight(POPUP_ROW_HEIGHT);
        this.popupList.setCellRenderer(
                (list, value, index, isSelected, cellHasFocus) ->
                        FilterableListComboBox.this.renderer.getListCellRendererComponent(
                                list, value, index, isSelected, cellHasFocus));

        JScrollPane scroll = new JScrollPane(this.popupList);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setFocusable(false);
        scroll.getVerticalScrollBar().setFocusable(false);
        scroll.getHorizontalScrollBar().setFocusable(false);
        this.popupScroll = scroll;

        this.popup = new JPopupMenu();
        this.popup.setFocusable(false);
        this.popup.add(this.popupScroll);
        this.popup.addPopupMenuListener(
                new PopupMenuListener() {
                    @Override
                    public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                        FilterableListComboBox.this.applyPopupTheme();
                    }

                    @Override
                    public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                        if (FilterableListComboBox.this.editable
                                && !FilterableListComboBox.this.editorField.isFocusOwner()) {
                            restoreCommittedEditorText();
                        }
                    }

                    @Override
                    public void popupMenuCanceled(PopupMenuEvent e) {
                        if (FilterableListComboBox.this.editable) {
                            restoreCommittedEditorText();
                        }
                    }
                });

        JPanel dropPanel = new JPanel(new BorderLayout());
        dropPanel.setOpaque(false);
        dropPanel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 1));
        dropPanel.add(this.dropButton, BorderLayout.CENTER);

        add(this.editorField, BorderLayout.CENTER);
        add(dropPanel, BorderLayout.EAST);

        this.dropButton.addActionListener(
                e -> {
                    if (FilterableListComboBox.this.editable) {
                        openPopupForQuery(FilterableListComboBox.this.editorField.getText());
                    } else {
                        toggleReadOnlyPopup();
                    }
                });

        this.editorField.addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        if (!FilterableListComboBox.this.editable
                                && FilterableListComboBox.this.isEnabled()) {
                            toggleReadOnlyPopup();
                        }
                    }
                });

        this.editorField
                .getDocument()
                .addDocumentListener(
                        new DocumentListener() {
                            @Override
                            public void insertUpdate(DocumentEvent e) {
                                onEditorChanged();
                            }

                            @Override
                            public void removeUpdate(DocumentEvent e) {
                                onEditorChanged();
                            }

                            @Override
                            public void changedUpdate(DocumentEvent e) {
                                onEditorChanged();
                            }
                        });

        this.editorField.addKeyListener(
                new KeyAdapter() {
                    @Override
                    public void keyPressed(KeyEvent e) {
                        if (!FilterableListComboBox.this.editable) {
                            return;
                        }
                        if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                            if (!FilterableListComboBox.this.popup.isVisible()) {
                                openPopupForQuery(FilterableListComboBox.this.editorField.getText());
                            }
                            moveListSelection(1);
                            e.consume();
                        } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                            moveListSelection(-1);
                            e.consume();
                        } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                            selectHighlightedRow();
                            e.consume();
                        } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                            hidePopup();
                            restoreCommittedEditorText();
                            e.consume();
                        }
                    }
                });

        this.editorField.addFocusListener(
                new FocusAdapter() {
                    @Override
                    public void focusGained(FocusEvent e) {
                        if (!FilterableListComboBox.this.editable
                                || FilterableListComboBox.this.selectedItem == null) {
                            return;
                        }
                        String committed = labelFor(FilterableListComboBox.this.selectedItem, -1);
                        if (FilterableListComboBox.this.editorField.getText().equals(committed)) {
                            FilterableListComboBox.this.editorField.selectAll();
                        }
                    }
                });

        this.popupList.addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        if (!SwingUtilities.isLeftMouseButton(e)) {
                            return;
                        }
                        int i = FilterableListComboBox.this.popupList.locationToIndex(e.getPoint());
                        if (i < 0) {
                            return;
                        }
                        FilterableListComboBox.this.popupList.setSelectedIndex(i);
                        selectHighlightedRow();
                    }
                });

        setModel(model);
        setEditable(false);
        applyComboChrome();
        applyPopupTheme();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        applyComboChrome();
        applyPopupTheme();
        if (this.dropButton != null) {
            RequestResponsePanelUi.restyleFlatToolbarButton(this.dropButton);
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        this.editorField.setEnabled(enabled);
        this.dropButton.setEnabled(enabled);
        applyComboChrome();
    }

    public ComboBoxModel<T> getModel() {
        return this.dataModel;
    }

    public void setModel(ComboBoxModel<T> model) {
        if (model == null) {
            model = new DefaultComboBoxModel<>();
        }
        if (this.dataModel != null) {
            this.dataModel.removeListDataListener(this.modelListener);
        }
        this.dataModel = model;
        this.dataModel.addListDataListener(this.modelListener);
        onModelChanged();
    }

    public T getSelectedItem() {
        return this.selectedItem;
    }

    public void setSelectedItem(T item) {
        T previous = this.selectedItem;
        this.selectedItem = item;
        updateEditorFromSelection();
        hidePopup();
        if (!Objects.equals(previous, item)) {
            fireActionEvent();
        }
    }

    public void addActionListener(ActionListener listener) {
        this.actionListeners.add(listener);
    }

    public void removeActionListener(ActionListener listener) {
        this.actionListeners.remove(listener);
    }

    public void setRenderer(ListCellRenderer<? super T> renderer) {
        this.renderer = renderer != null ? renderer : new DefaultListCellRenderer();
        this.popupList.setCellRenderer(
                (list, value, index, isSelected, cellHasFocus) ->
                        FilterableListComboBox.this.renderer.getListCellRendererComponent(
                                list, value, index, isSelected, cellHasFocus));
        updateEditorFromSelection();
    }

    public ListCellRenderer<? super T> getRenderer() {
        return this.renderer;
    }

    public boolean isEditable() {
        return this.editable;
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
        this.editorField.setEditable(editable);
    }

    public void setFilter(BiPredicate<T, String> filter) {
        this.filter = filter != null ? filter : (item, query) -> true;
        if (this.popup.isVisible() || this.editable) {
            applyFilter(this.editorField.getText());
        }
    }

    public int getMaximumRowCount() {
        return this.maximumRowCount;
    }

    public void setMaximumRowCount(int count) {
        this.maximumRowCount = Math.max(1, count);
        if (this.popup.isVisible()) {
            resizePopup();
        }
    }

    public int getPopupMinWidth() {
        return this.popupMinWidth;
    }

    public void setPopupMinWidth(int width) {
        this.popupMinWidth = Math.max(1, width);
        if (this.popup.isVisible()) {
            resizePopup();
        }
    }

    private void onModelChanged() {
        if (this.selectedItem != null && !modelContains(this.selectedItem)) {
            this.selectedItem = null;
            updateEditorFromSelection();
        }
        if (this.popup.isVisible() || (this.editable && this.editorField.isFocusOwner())) {
            applyFilter(this.editorField.getText());
        }
    }

    private boolean modelContains(T item) {
        for (int i = 0; i < this.dataModel.getSize(); i++) {
            if (Objects.equals(this.dataModel.getElementAt(i), item)) {
                return true;
            }
        }
        return false;
    }

    private void onEditorChanged() {
        if (this.suppressFieldEvents || !this.editable) {
            return;
        }
        String text = this.editorField.getText();
        if (this.selectedItem != null && !text.equals(labelFor(this.selectedItem, -1))) {
            this.selectedItem = null;
        }
        applyFilter(text);
        if (this.editorField.isFocusOwner()) {
            showPopup();
        }
    }

    private void openPopupForQuery(String query) {
        applyFilter(query);
        showPopup();
        this.editorField.requestFocusInWindow();
    }

    private void applyFilter(String queryRaw) {
        String query = queryRaw != null ? queryRaw.trim() : "";
        this.visibleModel.clear();
        for (int i = 0; i < this.dataModel.getSize(); i++) {
            T item = this.dataModel.getElementAt(i);
            if (!this.editable || this.filter.test(item, query)) {
                this.visibleModel.addElement(item);
            }
        }
        if (!this.visibleModel.isEmpty()) {
            if (this.selectedItem != null) {
                this.popupList.setSelectedValue(this.selectedItem, true);
            }
            if (this.popupList.getSelectedIndex() < 0) {
                this.popupList.setSelectedIndex(0);
            }
        }
        resizePopup();
    }

    private void toggleReadOnlyPopup() {
        if (!isEnabled() || this.dataModel.getSize() == 0) {
            return;
        }
        if (this.popup.isVisible()) {
            hidePopup();
            return;
        }
        applyFilter("");
        if (this.selectedItem != null) {
            this.popupList.setSelectedValue(this.selectedItem, true);
        } else if (!this.visibleModel.isEmpty()) {
            this.popupList.setSelectedIndex(0);
        }
        resizePopup();
        this.popup.show(this, 0, getHeight());
    }

    private void showPopup() {
        if (this.visibleModel.isEmpty()) {
            hidePopup();
            return;
        }
        if (!this.popup.isVisible()) {
            resizePopup();
            Component anchor = this.editable ? this.editorField : this;
            int y = this.editable ? this.editorField.getHeight() : getHeight();
            this.popup.show(anchor, 0, y);
        } else {
            resizePopup();
        }
    }

    private void hidePopup() {
        if (this.popup.isVisible()) {
            this.popup.setVisible(false);
        }
    }

    private void resizePopup() {
        int rows = Math.min(this.maximumRowCount, Math.max(1, this.visibleModel.getSize()));
        int height = rows * POPUP_ROW_HEIGHT + 4;
        int width;
        if (this.editable) {
            width = Math.max(this.popupMinWidth, this.editorField.getWidth() + this.dropButton.getWidth());
        } else {
            width = Math.max(this.popupMinWidth, getWidth());
        }
        if (this.popupList.getParent() instanceof JScrollPane scroll) {
            scroll.setPreferredSize(new Dimension(width, height));
        }
        this.popup.pack();
    }

    private void moveListSelection(int delta) {
        if (this.visibleModel.isEmpty()) {
            return;
        }
        int i = this.popupList.getSelectedIndex();
        if (i < 0) {
            i = 0;
        } else {
            i = Math.max(0, Math.min(this.visibleModel.getSize() - 1, i + delta));
        }
        this.popupList.setSelectedIndex(i);
        this.popupList.ensureIndexIsVisible(i);
    }

    private void selectHighlightedRow() {
        T item = this.popupList.getSelectedValue();
        if (item == null && !this.visibleModel.isEmpty()) {
            item = this.visibleModel.getElementAt(0);
        }
        if (item != null) {
            setSelectedItem(item);
        }
        hidePopup();
    }

    private void updateEditorFromSelection() {
        setFieldText(this.selectedItem != null ? labelFor(this.selectedItem, -1) : "");
    }

    private void restoreCommittedEditorText() {
        updateEditorFromSelection();
    }

    private void setFieldText(String text) {
        this.suppressFieldEvents = true;
        this.editorField.setText(text != null ? text : "");
        this.suppressFieldEvents = false;
    }

    private String labelFor(T value, int index) {
        if (value == null) {
            return "";
        }
        Component rendered =
                this.renderer.getListCellRendererComponent(this.popupList, value, index, false, false);
        if (rendered instanceof javax.swing.JLabel label) {
            String text = label.getText();
            return text != null ? text : "";
        }
        return value.toString();
    }

    private void applyComboChrome() {
        if (this.editorField == null) {
            return;
        }
        Utilities.applyComboBoxShellStyle(this, this.editorField, isEnabled());
        Color fg =
                isEnabled()
                        ? UIManager.getColor("ComboBox.foreground")
                        : UIManager.getColor("ComboBox.disabledForeground");
        if (fg != null) {
            this.editorField.setForeground(fg);
        }
        Color disabled = UIManager.getColor("ComboBox.disabledForeground");
        if (disabled != null) {
            this.editorField.setDisabledTextColor(disabled);
        }
    }

    private void applyPopupTheme() {
        if (this.popup == null || this.popupList == null) {
            return;
        }
        Color popupBg = UIManager.getColor("PopupMenu.background");
        if (popupBg != null) {
            this.popup.setBackground(popupBg);
            this.popup.setOpaque(true);
        }
        Color listBg = UIManager.getColor("List.background");
        Color listFg = UIManager.getColor("List.foreground");
        Color selBg = UIManager.getColor("List.selectionBackground");
        Color selFg = UIManager.getColor("List.selectionForeground");
        if (listBg != null) {
            this.popupList.setBackground(listBg);
        }
        if (listFg != null) {
            this.popupList.setForeground(listFg);
        }
        if (selBg != null) {
            this.popupList.setSelectionBackground(selBg);
        }
        if (selFg != null) {
            this.popupList.setSelectionForeground(selFg);
        }
        if (this.popupScroll != null) {
            Color viewportBg = listBg != null ? listBg : popupBg;
            if (viewportBg != null) {
                this.popupScroll.getViewport().setBackground(viewportBg);
            }
        }
        this.popupList.repaint();
    }

    private void fireActionEvent() {
        ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "comboBoxChanged");
        for (ActionListener listener : this.actionListeners) {
            listener.actionPerformed(event);
        }
    }
}
