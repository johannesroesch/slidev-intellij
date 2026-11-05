package dev.slidev.intellij;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/** Keep the Chromium host in the same displayable parent across view changes. */
final class SlidevView extends JPanel {
    private static final long serialVersionUID=1L;
    enum Mode { CODE, SPLIT, PREVIEW }
    private final JSplitPane split;
    private final JComponent source, preview;
    private final int dividerSize;
    private Mode mode=Mode.SPLIT;
    private double ratio=0.5;
    private boolean initialized;

    SlidevView(JComponent source, JComponent preview) {
        super(new BorderLayout());
        this.source=source;this.preview=preview;
        source.setMinimumSize(new Dimension(0,0));
        preview.setMinimumSize(new Dimension(0,0));
        split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,source,preview);
        split.setResizeWeight(0.5);
        dividerSize=split.getDividerSize();
        add(split,BorderLayout.CENTER);
        split.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) {
                if(!initialized || mode!=Mode.SPLIT) placeDivider();
            }
        });
    }
    void setMode(Mode next) {
        if(next==mode) return;
        if(mode==Mode.SPLIT && initialized && split.getWidth()>dividerSize) {
            ratio=Math.max(0.1,Math.min(0.9,(double)split.getDividerLocation()/(split.getWidth()-dividerSize)));
        }
        mode=next;
        source.setVisible(mode!=Mode.PREVIEW);
        preview.setVisible(mode!=Mode.CODE);
        split.setDividerSize(mode==Mode.SPLIT?dividerSize:0);
        split.setEnabled(mode==Mode.SPLIT);
        placeDivider();
        revalidate();repaint();
    }
    void ensureLayout() {
        doLayout();split.doLayout();
        if(!initialized || mode!=Mode.SPLIT) placeDivider();
        split.doLayout();
    }
    private void placeDivider() {
        if(split.getWidth()<=0) return;
        switch(mode) {
            case CODE -> split.setDividerLocation(split.getWidth());
            case PREVIEW -> split.setDividerLocation(0);
            case SPLIT -> {
                split.setDividerLocation((int)Math.round((split.getWidth()-dividerSize)*ratio));
                initialized=true;
            }
        }
    }
}
