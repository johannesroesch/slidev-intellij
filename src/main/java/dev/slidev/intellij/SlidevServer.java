package dev.slidev.intellij;

import com.intellij.openapi.application.ApplicationManager;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.LinkedHashMap;
import java.util.regex.Pattern;
import dev.slidev.intellij.core.BuildDiagnostics;
import java.util.function.Consumer;

/** One server per open deck editor. No shell, npm install or global Slidev invocation. */
final class SlidevServer implements AutoCloseable {
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private volatile Process process;
    private static final Pattern LOCAL_URL=Pattern.compile("http://(localhost|127\\.0\\.0\\.1|\\[::1\\]):([0-9]{1,5})(?:/|(?=\\s|$))");
    static String announcedBase(String line) {
        var match=LOCAL_URL.matcher(BuildDiagnostics.clean(line));
        if(!match.find()) return null;
        int port=Integer.parseInt(match.group(2));
        return port>0 && port<=65535 ? "http://"+match.group(1)+":"+port+"/" : null;
    }
    private final Consumer<String> log;
    SlidevServer(Consumer<String> log) { this.log=log; }

    void start(Path entry, String node, Consumer<String> ready, Consumer<String> failed) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                Path cli=null, root=null;
                for(Path dir=entry.getParent();dir!=null;dir=dir.getParent()) {
                    Path candidate=dir.resolve("node_modules/@slidev/cli/bin/slidev.mjs");
                    if(Files.isRegularFile(candidate)) { cli=candidate;root=dir;break; }
                }
                if(cli==null) throw new IOException("Local @slidev/cli missing. Install the presentation's dependencies first.");
                int port;
                try(ServerSocket socket=new ServerSocket(0,0,InetAddress.getLoopbackAddress())) { port=socket.getLocalPort(); }
                if(cancelled.get()) return;
                Process child=new ProcessBuilder(node,cli.toString(),entry.toString(),"--port",Integer.toString(port))
                    .directory(root.toFile()).redirectErrorStream(true).start();
                process=child;
                if(cancelled.get()) { terminate(child);return; }
                AtomicReference<String> announced=new AtomicReference<>();
                ApplicationManager.getApplication().executeOnPooledThread(() -> {
                    try(BufferedReader reader=new BufferedReader(new InputStreamReader(child.getInputStream(),StandardCharsets.UTF_8))) {
                        String line;
                        while((line=reader.readLine())!=null) {
                            String clean=BuildDiagnostics.clean(line);
                            log.accept(clean);
                            String address=announcedBase(clean);
                            if(address!=null) announced.compareAndSet(null,address);
                        }
                    } catch(IOException ex) { if(!cancelled.get()) log.accept(ex.getMessage()); }
                });
                String base=null;
                log.accept("[Slidev] Waiting for a CLI address or HTTP response on slide /1, requested port "+port);
                long deadline=System.nanoTime()+90_000_000_000L;
                var lastResults=new LinkedHashMap<String,String>();
                while(!cancelled.get() && child.isAlive() && System.nanoTime()<deadline) {
                    // Slidev publishes its address after starting the listener. Use this
                    // signal directly: Chromium and the JVM can have different localhost
                    // resolution/network behavior. Java HTTP is only the fallback.
                    base=announced.get();
                    if(base!=null) {
                        log.accept("[Slidev] Using CLI address "+base+"; Chromium will load the slide directly.");
                        break;
                    }
                    for(String host:new String[]{"127.0.0.1","localhost","[::1]"}) {
                        if(cancelled.get() || System.nanoTime()>=deadline) break;
                        String reported=announced.get();
                        if(reported!=null) { base=reported;break; }
                        String candidate="http://"+host+":"+port+"/";
                        HttpURLConnection connection=null;
                        String result;
                        try {
                            connection=(HttpURLConnection)URI.create(candidate+"1").toURL().openConnection(Proxy.NO_PROXY);
                            connection.setConnectTimeout(500);connection.setReadTimeout(3000);connection.setInstanceFollowRedirects(false);
                            int status=connection.getResponseCode();
                            result="HTTP "+status;
                            if(status>=200 && status<400) base=candidate;
                        } catch(IOException ex) { result=ex.toString(); }
                        finally { if(connection!=null) connection.disconnect(); }
                        if(!result.equals(lastResults.put(candidate+"1",result))) log.accept("[Slidev] "+candidate+"1: "+result);
                        if(base!=null) break;
                    }
                    if(base!=null) break;
                    Thread.sleep(200);
                }
                if(cancelled.get()) return;
                if(base==null) {
                    lastResults.forEach((address,result) -> log.accept("[Slidev] Last result "+address+": "+result));
                    throw new IOException(child.isAlive()?"Server startup timed out. See log for tested addresses.":"Slidev exited during startup. See log for details.");
                }
                ready.accept(base);
                int exit=child.waitFor();
                if(!cancelled.get()) failed.accept("Slidev stopped (exit "+exit+"). See log.");
            } catch(Exception ex) {
                Process child=process;
                if(child!=null) terminate(child);
                if(!cancelled.get()) {
                    String message=ex.getMessage()==null?ex.toString():ex.getMessage();
                    log.accept("[Slidev] Startup/session error: "+message);
                    failed.accept(message);
                }
                if(ex instanceof InterruptedException) Thread.currentThread().interrupt();
            }
        });
    }
    boolean isAlive() { return process!=null && process.isAlive() && !cancelled.get(); }
    @Override public void close() {
        cancelled.set(true);
        Process child=process;
        if(child!=null) ApplicationManager.getApplication().executeOnPooledThread(() -> terminate(child));
    }
    static void terminate(Process child) {
        var descendants=child.descendants().toList();
        descendants.forEach(ProcessHandle::destroy);child.destroy();
        try { child.waitFor(1500,java.util.concurrent.TimeUnit.MILLISECONDS); }
        catch(InterruptedException ignored) { Thread.currentThread().interrupt(); }
        descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        if(child.isAlive()) child.destroyForcibly();
    }
}
