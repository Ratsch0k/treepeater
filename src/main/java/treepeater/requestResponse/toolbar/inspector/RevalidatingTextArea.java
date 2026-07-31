package treepeater.requestResponse.toolbar.inspector;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import javax.swing.JTextArea;

/**
 * A minimal wrapping text area that revalidates whenever its width changes. A wrapping {@link JTextArea} derives
 * its preferred height from its width but does not recompute it on a plain resize; this one triggers the recompute
 * so the field's height follows the panel width in both directions (grows when narrowed, shrinks when widened).
 */
final class RevalidatingTextArea extends JTextArea {
    private int lastWidth = -1;

    RevalidatingTextArea() {
        setLineWrap(true);
        setWrapStyleWord(false);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int w = getWidth();
                if (w != RevalidatingTextArea.this.lastWidth) {
                    RevalidatingTextArea.this.lastWidth = w;
                    revalidate();
                }
            }
        });
    }
}
