package dev.slidev.intellij;
import javax.swing.*;
import java.awt.*;

/** Headless regression checks for the real Swing layout, independent of SDK. */
public final class SlidevViewTest {
    public static void main(String[] args)throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel code=new JPanel();code.setPreferredSize(new Dimension(1600,600));
            JPanel preview=new JPanel();preview.setPreferredSize(new Dimension(100,600));
            JPanel browser=new JPanel();preview.add(browser);
            SlidevView view=new SlidevView(code,preview);
            view.setSize(1000,700);view.ensureLayout();
            if(code.getWidth()<400 || preview.getWidth()<400) throw new AssertionError("Initial split must reserve visible preview space");
            Container parent=preview.getParent(), browserParent=browser.getParent();
            for(int i=0;i<5;i++) {
                view.setMode(SlidevView.Mode.CODE);view.ensureLayout();
                if(!code.isVisible() || preview.isVisible() || code.getWidth()<990) throw new AssertionError("Code-only layout");
                view.setMode(SlidevView.Mode.PREVIEW);view.ensureLayout();
                if(code.isVisible() || !preview.isVisible() || preview.getWidth()<990) throw new AssertionError("Preview-only layout");
                view.setMode(SlidevView.Mode.SPLIT);view.ensureLayout();
                if(code.getWidth()<400 || preview.getWidth()<400) throw new AssertionError("Restored split");
                if(preview.getParent()!=parent || browser.getParent()!=browserParent) throw new AssertionError("Browser hierarchy must remain stable");
            }
            ((JSplitPane)parent).setDividerLocation(650);
            view.setMode(SlidevView.Mode.CODE);view.ensureLayout();
            view.setMode(SlidevView.Mode.SPLIT);view.ensureLayout();
            if(Math.abs(((JSplitPane)parent).getDividerLocation()-650)>2) throw new AssertionError("Split ratio lost");
            view.setMode(SlidevView.Mode.PREVIEW);view.setSize(1300,700);view.ensureLayout();
            if(preview.getWidth()<1290) throw new AssertionError("Preview resize");
            System.out.println("PASS: stable browser hierarchy, initial 50/50 split, all modes, ratio and resize");
        });
    }
}
