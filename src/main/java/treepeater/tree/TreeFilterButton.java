package treepeater.tree;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.UIManager;

import treepeater.Treepeater;
import treepeater.icons.FilterIcon;
import treepeater.requestResponse.Status;
import treepeater.requestResponse.toolbar.ToolbarIconButton;
import treepeater.settings.StatusRegistry;

/**
 * Toolbar button next to the tree search field. Opens a popup where the tree can be restricted
 * to certain node statuses and request methods. The icon is tinted while a restriction is
 * active and the tooltip summarizes it.
 */
public class TreeFilterButton extends ToolbarIconButton {
    private static final String DEFAULT_TOOLTIP = "Filter by status and request method";

    private final RequestTree tree;
    private int iconSize = 20;

    public TreeFilterButton(RequestTree tree) {
        super(new FilterIcon());
        this.tree = tree;
        this.addActionListener(e -> this.showMenu());
        tree.addPropertyChangeListener(RequestTree.PROPERTY_TREE_FILTER, ev -> this.updateIndicator());
        this.updateIndicator();
    }

    @Override
    public void applyLocalTheme(int size) {
        super.applyLocalTheme(size);
        this.iconSize = size;
        this.updateIndicator();
    }

    private void updateIndicator() {
        TreeFilter filter = this.tree.getTreeFilter();
        boolean active = filter.hasAttributeFilter();

        Color color = active ? accentColor() : UIManager.getColor("Label.foreground");
        Color disabled = UIManager.getColor("Label.disabledForeground");
        // Separate instances: SvgIcon is mutable, so sharing one would tint both states alike.
        this.setIcon(new FilterIcon().withSize(this.iconSize, this.iconSize).withColor(color));
        this.setDisabledIcon(new FilterIcon().withSize(this.iconSize, this.iconSize)
                .withColor(disabled != null ? disabled : Color.GRAY));
        this.setToolTipText(active ? "Filters: " + describe(filter) : DEFAULT_TOOLTIP);
        this.repaint();
    }

    private static Color accentColor() {
        Color color = UIManager.getColor("Colors.ui.groups.2.accent");
        if (color == null) {
            color = UIManager.getColor("Component.accentColor");
        }
        return color != null ? color : new Color(0x3592C4);
    }

    private static String describe(TreeFilter filter) {
        List<String> parts = new ArrayList<>();
        if (!filter.statusIds().isEmpty()) {
            StatusRegistry registry = Treepeater.getStatusRegistry();
            List<String> names = new ArrayList<>();
            for (String id : filter.statusIds()) {
                Status status = registry != null ? registry.getById(id) : null;
                names.add(status != null ? status.getStatus() : id);
            }
            names.sort(String.CASE_INSENSITIVE_ORDER);
            parts.add("Status = " + String.join(", ", names));
        }
        if (!filter.methods().isEmpty()) {
            Set<String> methods = new TreeSet<>(TreeFilter.METHOD_ORDER);
            methods.addAll(filter.methods());
            parts.add("Method = " + String.join(", ", methods));
        }
        return String.join(" \u00b7 ", parts);
    }

    private void showMenu() {
        TreeFilter filter = this.tree.getTreeFilter();
        JPopupMenu menu = new JPopupMenu();

        menu.add(header("Status"));
        StatusRegistry registry = Treepeater.getStatusRegistry();
        if (registry != null) {
            for (Status status : registry.getAll()) {
                String id = TreeFilter.normalizeStatusId(status.getId());
                menu.add(toggleItem(status.getStatus(), status.getIcon(),
                        filter.statusIds().contains(id), selected -> this.toggleStatus(id, selected)));
            }
        }

        menu.addSeparator();
        menu.add(header("Method"));
        Set<String> methods = new TreeSet<>(TreeFilter.METHOD_ORDER);
        methods.addAll(this.tree.collectRequestMethods());
        methods.addAll(filter.methods());
        if (methods.isEmpty()) {
            JMenuItem none = new JMenuItem("No requests");
            none.setEnabled(false);
            menu.add(none);
        }
        for (String method : methods) {
            menu.add(toggleItem(method, null, filter.methods().contains(method),
                    selected -> this.toggleMethod(method, selected)));
        }

        menu.addSeparator();
        JMenuItem clear = new JMenuItem("Clear filters");
        clear.setEnabled(filter.hasAttributeFilter());
        clear.addActionListener(e -> this.tree.clearAttributeFilters());
        menu.add(clear);

        int x = Math.min(0, this.getWidth() - menu.getPreferredSize().width);
        menu.show(this, x, this.getHeight());
    }

    /**
     * A menu entry that toggles a filter value without closing the popup. Selected entries are
     * drawn bold and italic instead of with a check mark (a checked item with an icon gets a
     * light background behind the icon).
     */
    private static JMenuItem toggleItem(String text, Icon icon, boolean selected, Consumer<Boolean> onToggle) {
        boolean[] state = { selected };
        JMenuItem item = new JMenuItem(text, icon) {
            @Override
            protected void processMouseEvent(MouseEvent e) {
                if (e.getID() == MouseEvent.MOUSE_RELEASED && this.contains(e.getPoint())) {
                    this.doClick(0);
                    this.setArmed(true);
                } else {
                    super.processMouseEvent(e);
                }
            }
        };
        Font plain = item.getFont();
        Font emphasized = plain.deriveFont(Font.BOLD | Font.ITALIC);

        // Reserve the wider (bold italic) width so toggling does not clip the text.
        item.setFont(emphasized);
        Dimension wide = item.getPreferredSize();
        item.setFont(plain);
        item.setPreferredSize(new Dimension(wide.width, item.getPreferredSize().height));

        item.setFont(selected ? emphasized : plain);
        item.addActionListener(e -> {
            state[0] = !state[0];
            item.setFont(state[0] ? emphasized : plain);
            onToggle.accept(state[0]);
        });
        return item;
    }

    private static JLabel header(String text) {
        JLabel label = new JLabel(text.toUpperCase());
        label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize2D() - 1f));
        label.setForeground(UIManager.getColor("Label.disabledForeground"));
        label.setBorder(BorderFactory.createEmptyBorder(4, 12, 2, 12));
        return label;
    }

    private void toggleStatus(String id, boolean selected) {
        Set<String> next = new HashSet<>(this.tree.getTreeFilter().statusIds());
        if (selected) {
            next.add(id);
        } else {
            next.remove(id);
        }
        this.tree.setStatusFilter(next);
    }

    private void toggleMethod(String method, boolean selected) {
        Set<String> next = new HashSet<>(this.tree.getTreeFilter().methods());
        if (selected) {
            next.add(method);
        } else {
            next.remove(method);
        }
        this.tree.setMethodFilter(next);
    }
}
