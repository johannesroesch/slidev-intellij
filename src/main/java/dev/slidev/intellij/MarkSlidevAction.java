package dev.slidev.intellij;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

public final class MarkSlidevAction extends AnAction implements DumbAware {
    private static final Logger LOG=Logger.getInstance(MarkSlidevAction.class);
    @Override public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.BGT; }
    @Override public void update(@NotNull AnActionEvent event) {
        var file=event.getData(CommonDataKeys.VIRTUAL_FILE);
        event.getPresentation().setEnabledAndVisible(event.getProject()!=null && file!=null && SlidevDetection.isMarkdown(file));
    }
    @Override public void actionPerformed(@NotNull AnActionEvent event) {
        var project=event.getProject(); var file=event.getData(CommonDataKeys.VIRTUAL_FILE);
        if(project==null || file==null) return;
        var files=SlidevSettings.get(project).getState().entryFiles;
        if(!files.contains(file.getUrl())) files.add(file.getUrl());
        SlidevSettings.get(project).getState().importedEntries.remove(file.getPath());
        LOG.info("[Slidev 0.1.20] Explicit assignment: "+file.getPath());
        var manager=FileEditorManager.getInstance(project);
        try {
            manager.closeFile(file); manager.openFile(file,true);
            ApplicationManager.getApplication().invokeLater(() -> {
                if(project.isDisposed() || !file.isValid()) return;
                var editors=manager.getEditors(file);
                boolean found=java.util.Arrays.stream(editors).anyMatch(editor -> "Slidev".equals(editor.getName()));
                if(found) manager.setSelectedEditor(file,"slidev-editor");
                else {
                    String names=java.util.Arrays.stream(editors).map(editor -> editor.getClass().getName())
                        .collect(java.util.stream.Collectors.joining("\n"));
                    LOG.warn("[Slidev 0.1.20] Assigned file reopened without Slidev editor. Active editors: "+names);
                    Messages.showErrorDialog(project,
                        "Slidev 0.1.20: The file was assigned, but IntelliJ did not create the Slidev editor.\n\n"+
                        "Active editors:\n"+names+"\n\nOpen idea.log and search for [Slidev 0.1.20].",
                        "Slidev editor unavailable");
                }
            });
        } catch(com.intellij.openapi.progress.ProcessCanceledException ex) { throw ex; }
        catch(RuntimeException | LinkageError ex) {
            LOG.warn("[Slidev 0.1.20] Opening assigned file failed",ex);
            Messages.showErrorDialog(project,ex.toString()+"\n\nSee idea.log for [Slidev 0.1.20].","Slidev editor failed");
        }
    }
}
