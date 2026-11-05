package dev.slidev.intellij;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

// Detection uses file contents and settings, without querying IDE indexes.
public final class SlidevEditorProvider implements FileEditorProvider, DumbAware {
    private static final Logger LOG=Logger.getInstance(SlidevEditorProvider.class);
    @Override public boolean accept(@NotNull Project project,@NotNull VirtualFile file) {
        boolean accepted=SlidevDetection.accepts(project,file);
        if(accepted) LOG.info("[Slidev 0.1.16] Provider accepted "+file.getPath());
        return accepted;
    }
    @Override public @NotNull FileEditor createEditor(@NotNull Project project,@NotNull VirtualFile file) {
        LOG.info("[Slidev 0.1.16] Creating editor for "+file.getPath());
        try { return new SlidevEditor(project,file); }
        catch(com.intellij.openapi.progress.ProcessCanceledException ex) { throw ex; }
        catch(RuntimeException | LinkageError ex) {
            LOG.warn("[Slidev 0.1.16] Editor creation failed",ex);
            throw ex;
        }
    }
    @Override public @NotNull String getEditorTypeId() { return "slidev-editor"; }
    @Override public @NotNull FileEditorPolicy getPolicy() { return FileEditorPolicy.HIDE_DEFAULT_EDITOR; }
}
