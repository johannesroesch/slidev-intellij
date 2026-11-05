package dev.slidev.intellij;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import javax.swing.*;
import java.awt.*;

public final class SlidevConfigurable implements Configurable {
    private final SlidevSettings settings;
    private final Project project;
    private JTextField node;
    private JCheckBox showDebugConsole, syncNavigation, checkVueExpressions;
    public SlidevConfigurable(Project project) { this.project=project;settings = SlidevSettings.get(project); }
    @Override public String getDisplayName() { return "Slidev"; }
    @Override public JComponent createComponent() {
        JPanel panel = new JPanel(new BorderLayout(8,8));
        node = new JTextField(settings.getState().nodeExecutable);
        panel.add(new JLabel("Node.js executable (absolute path recommended):"), BorderLayout.NORTH);
        panel.add(node, BorderLayout.CENTER);
        showDebugConsole=new JCheckBox("Show debug console",settings.getState().showDebugConsole);
        syncNavigation=new JCheckBox("Sync editor and preview",settings.getState().syncNavigation);
        checkVueExpressions=new JCheckBox("Check Vue expressions while typing",settings.getState().checkVueExpressions);
        JPanel options=new JPanel(new GridLayout(0,1,0,4));
        options.add(showDebugConsole);options.add(syncNavigation);options.add(checkVueExpressions);
        panel.add(options,BorderLayout.SOUTH);
        JPanel outer = new JPanel(new BorderLayout()); outer.add(panel, BorderLayout.NORTH);
        return outer;
    }
    @Override public boolean isModified() {
        return node!=null && (!node.getText().trim().equals(settings.getState().nodeExecutable) ||
            showDebugConsole.isSelected()!=settings.getState().showDebugConsole ||
            syncNavigation.isSelected()!=settings.getState().syncNavigation ||
            checkVueExpressions.isSelected()!=settings.getState().checkVueExpressions);
    }
    @Override public void apply() {
        if(node==null) return;
        settings.getState().checkVueExpressions=checkVueExpressions.isSelected();
        settings.setEditorOptions(project,node.getText().trim(),showDebugConsole.isSelected(),syncNavigation.isSelected());
        com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.getInstance(project).restart();
    }
    @Override public void reset() {
        if(node==null) return;
        node.setText(settings.getState().nodeExecutable);
        showDebugConsole.setSelected(settings.getState().showDebugConsole);
        syncNavigation.setSelected(settings.getState().syncNavigation);
        checkVueExpressions.setSelected(settings.getState().checkVueExpressions);
    }
    @Override public void disposeUIResources() { node=null;showDebugConsole=null;syncNavigation=null;checkVueExpressions=null; }
}
