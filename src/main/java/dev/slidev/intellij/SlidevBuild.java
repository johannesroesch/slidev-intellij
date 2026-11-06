package dev.slidev.intellij;

import com.intellij.openapi.application.ApplicationManager;
import dev.slidev.intellij.core.BuildDiagnostics;
import dev.slidev.intellij.core.PreviewDiagnostics;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Explicit build validation, never overwrites the project's dist directory. */
final class SlidevBuild implements AutoCloseable {
    record Result(boolean success, String message, List<BuildDiagnostics.Diagnostic> diagnostics, PreviewDiagnostics details) {}
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private volatile Process process;
    void start(Path entry, String node, Consumer<String> log, Consumer<Result> complete) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            Path output=null;
            try {
                Path cli=null, root=null;
                for(Path dir=entry.getParent();dir!=null;dir=dir.getParent()) {
                    Path candidate=dir.resolve("node_modules/@slidev/cli/bin/slidev.mjs");
                    if(Files.isRegularFile(candidate)) { cli=candidate;root=dir;break; }
                }
                if(cli==null) throw new IOException("Local @slidev/cli missing. Install the presentation's dependencies first.");
                output=Files.createTempDirectory("slidev-ide-check-");
                if(cancelled.get()) return;
                Process child=new ProcessBuilder(node,cli.toString(),"build",entry.toString(),"--out",output.toString())
                    .directory(root.toFile()).redirectErrorStream(true).start();
                process=child;
                if(cancelled.get()) { SlidevServer.terminate(child);return; }
                Future<String> captured=ApplicationManager.getApplication().executeOnPooledThread(() -> {
                    StringBuilder text=new StringBuilder();
                    try(BufferedReader reader=new BufferedReader(new InputStreamReader(child.getInputStream(),StandardCharsets.UTF_8))) {
                        String line;
                        while((line=reader.readLine())!=null) {
                            String clean=BuildDiagnostics.clean(line);
                            log.accept(clean);
                            text.append(clean).append('\n');
                            if(text.length()>2_000_000) text.delete(0,text.length()-2_000_000);
                        }
                    }
                    return text.toString();
                });
                if(!child.waitFor(120,TimeUnit.SECONDS)) {
                    SlidevServer.terminate(child);
                    throw new IOException("Build check timed out after 120 seconds.");
                }
                String text=captured.get(10,TimeUnit.SECONDS);
                var details=new PreviewDiagnostics();details.accept(text);
                var diagnostics=new java.util.ArrayList<>(BuildDiagnostics.parse(text));
                for(var diagnostic:details.diagnostics())if(diagnostic.slide()==0 && diagnostic.path().endsWith(".vue") && !diagnostics.contains(diagnostic))diagnostics.add(diagnostic);
                if(!cancelled.get()) complete.accept(new Result(child.exitValue()==0,
                    child.exitValue()==0?"Build passed":"Build failed (exit "+child.exitValue()+"). See log.",List.copyOf(diagnostics),details));
            } catch(Exception ex) {
                if(process!=null) SlidevServer.terminate(process);
                if(!cancelled.get()) complete.accept(new Result(false,ex.getMessage()==null?ex.toString():ex.getMessage(),List.of(),new PreviewDiagnostics()));
                if(ex instanceof InterruptedException) Thread.currentThread().interrupt();
            } finally {
                if(output!=null) try(var paths=Files.walk(output)) {
                    for(Path path:paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                } catch(IOException ex) { log.accept("Temporary build cleanup: "+ex.getMessage()); }
            }
        });
    }
    @Override public void close() {
        cancelled.set(true);
        Process child=process;
        if(child!=null) ApplicationManager.getApplication().executeOnPooledThread(() -> SlidevServer.terminate(child));
    }
}
