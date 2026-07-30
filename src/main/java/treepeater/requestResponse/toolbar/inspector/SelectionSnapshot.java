package treepeater.requestResponse.toolbar.inspector;

import burp.api.montoya.core.Range;

/** Snapshot of the editor selection currently reflected in the Inspector UI. */
record SelectionSnapshot(EditorSelectionWatcher.Source source, Range offsets, String raw, InspectorEncoding autoEncoding) {

    static SelectionSnapshot empty() {
        return new SelectionSnapshot(EditorSelectionWatcher.Source.NONE, null, null, InspectorEncoding.PLAIN);
    }

    boolean canApply() {
        return this.source == EditorSelectionWatcher.Source.REQUEST && this.offsets != null;
    }
}
