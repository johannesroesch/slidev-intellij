package dev.slidev.intellij;

import com.intellij.openapi.application.ApplicationManager;
import dev.slidev.intellij.core.PreviewServer;
import java.util.function.Consumer;

/** IDE adapter. The process lifecycle is shared with the standalone integration tests. */
final class SlidevServer extends PreviewServer {
    SlidevServer(Consumer<String> log) {
        super(log,action -> ApplicationManager.getApplication().executeOnPooledThread(action));
    }
}
