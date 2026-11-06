package dev.slidev.intellij;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import java.util.ArrayList;
import java.util.List;

@State(name="SlidevSettings", storages=@Storage(StoragePathMacros.WORKSPACE_FILE))
public final class SlidevSettings implements PersistentStateComponent<SlidevSettings.Data> {
    public interface Listener { void editorOptionsChanged(boolean debugConsole, boolean syncNavigation, boolean inspect); }
    public static final Topic<Listener> TOPIC=Topic.create("Slidev editor options",Listener.class);
    public static final class Data {
        public String nodeExecutable = "node";
        public boolean syncNavigation = true;
        public boolean showDebugConsole = false;
        public boolean enableInspect = false;
        public boolean checkVueExpressions = true;
        public List<String> entryFiles = new ArrayList<>();
        public java.util.Map<String,String> importedEntries = new java.util.LinkedHashMap<>();
    }
    private Data data = new Data();
    public static SlidevSettings get(Project project) { return project.getService(SlidevSettings.class); }
    public void setEditorOptions(Project project, String node, boolean debugConsole, boolean syncNavigation, boolean inspect) {
        if(data.nodeExecutable.equals(node) && data.showDebugConsole==debugConsole && data.syncNavigation==syncNavigation && data.enableInspect==inspect) return;
        data.nodeExecutable=node;
        data.showDebugConsole=debugConsole;
        data.syncNavigation=syncNavigation;
        data.enableInspect=inspect;
        project.getMessageBus().syncPublisher(TOPIC).editorOptionsChanged(debugConsole,syncNavigation,inspect);
    }
    @Override public Data getState() { return data; }
    @Override public void loadState(Data state) { data = state; }
}
