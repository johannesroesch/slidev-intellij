package dev.slidev.intellij;

import java.util.concurrent.Executor;
import dev.slidev.intellij.core.PreviewServer;
import java.util.function.Consumer;

/** IDE adapter. The process lifecycle is shared with the standalone integration tests. */
final class SlidevServer extends PreviewServer {
    SlidevServer(Executor executor,Consumer<String> log) {
        super(log,executor);
    }
}
