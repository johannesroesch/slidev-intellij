package dev.slidev.intellij;

import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;

/** Transfer only strings through a platform-owned store, never old plugin objects. */
public final class SlidevPluginListener implements DynamicPluginListener {
    private static final String ID="dev.slidev.editor",RESTORE="slidev.editor.restoreAfterUpdate";
    private static final Logger LOG=Logger.getInstance(SlidevPluginListener.class);
    @Override public void beforePluginUnload(@NotNull IdeaPluginDescriptor descriptor,boolean isUpdate) {
        if(!ID.equals(descriptor.getPluginId().getIdString()))return;
        for(var project:ProjectManager.getInstance().getOpenProjects()) {
            if(project.isDisposed())continue;
            var runtime=project.getServiceIfCreated(SlidevRuntime.class);
            if(runtime==null)continue;
            runtime.beginUnload();
            var editors=runtime.editors();
            var urls=editors.stream().map(editor->editor.getFile().getUrl()).distinct().toList();
            PropertiesComponent.getInstance(project).setValue(RESTORE,isUpdate?String.join("\n",urls):null);
            var manager=FileEditorManager.getInstance(project);
            for(var editor:editors) {
                manager.closeFile(editor.getFile());
                // Also covers an editor created but not yet attached to a tab.
                if(!com.intellij.openapi.util.Disposer.isDisposed(editor))com.intellij.openapi.util.Disposer.dispose(editor);
            }
            com.intellij.openapi.util.Disposer.dispose(runtime);
            LOG.info("Slidev unload: closed "+editors.size()+" editors; workers cancelled; update="+isUpdate);
        }
    }
    @Override public void pluginLoaded(@NotNull IdeaPluginDescriptor descriptor) {
        if(!ID.equals(descriptor.getPluginId().getIdString()))return;
        for(var project:ProjectManager.getInstance().getOpenProjects()) {
            if(project.isDisposed())continue;
            var properties=PropertiesComponent.getInstance(project);
            String saved=properties.getValue(RESTORE);
            properties.unsetValue(RESTORE);
            if(saved==null || saved.isBlank())continue;
            // New-version code only. Wait until the platform finishes registering extensions.
            ApplicationManager.getApplication().invokeLater(() -> {
                if(project.isDisposed())return;
                var manager=FileEditorManager.getInstance(project);
                for(String url:saved.split("\n")) {
                    var file=VirtualFileManager.getInstance().findFileByUrl(url);
                    if(file!=null && file.isValid())manager.openFile(file,false);
                }
            },ModalityState.nonModal(),ignored -> project.isDisposed());
        }
    }
}
