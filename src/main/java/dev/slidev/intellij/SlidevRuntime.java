package dev.slidev.intellij;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import dev.slidev.intellij.core.TaskScope;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Project service disposed on project close and dynamic plugin unload. EDT-owned editors. */
public final class SlidevRuntime implements Disposable {
    final TaskScope tasks=new TaskScope();
    private final Set<SlidevEditor> editors=new LinkedHashSet<>();
    private volatile boolean closing;
    static SlidevRuntime get(Project project) { return project.getService(SlidevRuntime.class); }
    boolean isClosing() { return closing; }
    void add(SlidevEditor editor) {
        editors.add(editor);
        com.intellij.openapi.util.Disposer.register(this,editor);
    }
    void remove(SlidevEditor editor) { editors.remove(editor); }
    List<SlidevEditor> editors() { return List.copyOf(editors); }
    void beginUnload() { closing=true; }
    @Override public void dispose() {
        closing=true;
        try { for(var editor:editors())com.intellij.openapi.util.Disposer.dispose(editor); }
        finally { editors.clear();tasks.close(); }
    }
}
