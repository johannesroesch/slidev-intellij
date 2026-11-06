package dev.slidev.intellij.core;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

/** Real subprocesses, HTTP, stdout/stderr and process-tree cleanup. No IDE mocks. */
public final class PreviewServerTest {
    private static int checks;
    private static void check(boolean condition,String label){checks++;if(!condition)throw new AssertionError(label);}
    private static void until(BooleanSupplier condition,String label)throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
        while(!condition.getAsBoolean() && System.nanoTime()<deadline)Thread.sleep(50);
        check(condition.getAsBoolean(),label);
    }
    private static int request(String url)throws Exception {
        var connection=(HttpURLConnection)URI.create(url.replace(" ","%20")).toURL().openConnection(Proxy.NO_PROXY);
        connection.setConnectTimeout(1000);connection.setReadTimeout(15000);
        try {return connection.getResponseCode();}finally{connection.disconnect();}
    }
    private static final class Session implements AutoCloseable {
        final List<String> log;final PreviewServer server;final CountDownLatch ready=new CountDownLatch(1),failed=new CountDownLatch(1);
        final AtomicReference<String> url=new AtomicReference<>(),failure=new AtomicReference<>();final AtomicInteger failures=new AtomicInteger();
        final AtomicBoolean tailAtFailure=new AtomicBoolean();
        Session(Executor executor,List<String> log){this.log=log;server=new PreviewServer(log::add,executor);}
        void start(Path entry,String node,boolean inspect){server.start(entry,node,inspect,value->{url.set(value);ready.countDown();},message->{tailAtFailure.set(log.contains("LAST-DIAGNOSTIC"));failure.set(message);failures.incrementAndGet();failed.countDown();});}
        void awaitReady()throws Exception{check(ready.await(60,TimeUnit.SECONDS),"Server becomes ready: "+failure.get()+" "+log);}
        void awaitFailure()throws Exception{check(failed.await(15,TimeUnit.SECONDS),"Failure callback delivered");}
        long pid(String prefix){return log.stream().filter(line->line.startsWith(prefix)).mapToLong(line->Long.parseLong(line.substring(prefix.length()))).reduce((a,b)->b).orElseThrow();}
        @Override public void close(){server.close();}
    }
    public static void main(String[] args)throws Exception {
        String node=args.length>0?args[0]:"node";
        Path root=Files.createTempDirectory("slidev process tests "),entry=root.resolve("entry space/slides.md");
        Files.createDirectories(entry.getParent());Path cli=root.resolve("node_modules/@slidev/cli/bin/slidev.mjs");Files.createDirectories(cli.getParent());
        Files.writeString(cli,"""
            import fs from 'node:fs';import http from 'node:http';import {spawn} from 'node:child_process';
            const entry=process.argv[2],mode=fs.readFileSync(entry,'utf8').trim();
            const port=Number(process.argv[process.argv.indexOf('--port')+1]);
            console.log('PID='+process.pid);console.log('ENTRY='+entry);console.log('INSPECT='+process.argv.includes('--inspect'));
            if(mode==='startup-crash'){console.error('SyntaxError: startup failure');console.log('LAST-DIAGNOSTIC');process.exitCode=9;}
            else {
              if(mode==='child'){const child=spawn(process.execPath,['-e','setInterval(()=>{},1000)'],{stdio:'ignore'});console.log('CHILD='+child.pid);}
              const server=http.createServer((req,res)=>{
                if(req.url==='/crash'){console.error('[vite] Internal server error: Invalid expression');console.error('  File: '+entry+'__slidev_2.md:3:7');for(let i=0;i<300;i++)console.log('tail '+i);console.log('LAST-DIAGNOSTIC');res.end('crash');server.close(()=>{process.exitCode=2});}
                else{res.statusCode=req.url==='/1'?200:404;res.end('slide');}
              });
              const start=()=>server.listen(port,'127.0.0.1',()=>console.log('http://127.0.0.1:'+port+'/1'));
              if(mode==='delayed')setTimeout(start,5000);else start();
            }
            """);
        var executor=Executors.newCachedThreadPool();List<Session> sessions=new ArrayList<>();
        try {
            var log=new CopyOnWriteArrayList<String>();Files.writeString(entry,"serve");var first=new Session(executor,log);sessions.add(first);first.start(entry,node,true);first.awaitReady();
            check(log.contains("ENTRY="+entry),"Entry containing spaces passed as one argument");check(log.contains("INSPECT=true"),"Inspect reaches CLI when enabled");
            check(request(first.url.get())==404 && request(first.url.get()+"1")==200,"Readiness/navigation uses slide /1, not root");
            request(first.url.get()+"crash");first.awaitFailure();
            check(first.tailAtFailure.get(),"Final stdout/stderr drained before crash notification");check(log.stream().anyMatch(line->line.contains("Invalid expression")),"stderr compilation error retained");
            check(first.failure.get().contains("exit 2") && first.failures.get()==1,"One actionable crash callback with exit code");first.close();
            var restarted=new Session(executor,log);sessions.add(restarted);restarted.start(entry,node,false);restarted.awaitReady();
            check(log.contains("INSPECT=false") && log.contains("LAST-DIAGNOSTIC"),"Restart without inspect retains previous log");
            long restartedPid=restarted.pid("PID=");restarted.close();restarted.close();until(()->ProcessHandle.of(restartedPid).map(p->!p.isAlive()).orElse(true),"Restarted server terminated on idempotent close");
            Files.writeString(entry,"startup-crash");var startup=new Session(executor,new CopyOnWriteArrayList<>());sessions.add(startup);startup.start(entry,node,false);startup.awaitFailure();
            check(startup.tailAtFailure.get() && startup.log.contains("SyntaxError: startup failure"),"Startup failure preserves last diagnostic before callback");
            Files.writeString(entry,"child");var tree=new Session(executor,new CopyOnWriteArrayList<>());sessions.add(tree);tree.start(entry,node,false);tree.awaitReady();long parent=tree.pid("PID="),child=tree.pid("CHILD=");tree.close();
            until(()->ProcessHandle.of(parent).map(p->!p.isAlive()).orElse(true),"Parent process terminated");
            until(()->ProcessHandle.of(child).map(p->!p.isAlive()).orElse(true),"Descendant process terminated");
            Files.writeString(entry,"delayed");var cancelled=new Session(executor,new CopyOnWriteArrayList<>());sessions.add(cancelled);cancelled.start(entry,node,false);
            until(()->cancelled.log.stream().anyMatch(line->line.startsWith("PID=")),"Delayed startup process started");long cancelledPid=cancelled.pid("PID=");cancelled.close();
            until(()->ProcessHandle.of(cancelledPid).map(p->!p.isAlive()).orElse(true),"Cancellation terminates startup process");
            // Outside the fake node_modules ancestor, so missing dependency discovery is real.
            Path absent=Files.createTempDirectory("slidev-absent-");
            try {var dependency=new Session(executor,new CopyOnWriteArrayList<>());sessions.add(dependency);dependency.start(absent.resolve("slides.md"),node,false);dependency.awaitFailure();check(dependency.failure.get().contains("Local @slidev/cli missing"),"Missing dependency is actionable");}
            finally{Files.deleteIfExists(absent);}
            if(args.length>1)realSlidev(node,Path.of(args[1]),executor,sessions);
            sessions.forEach(Session::close);executor.shutdown();check(executor.awaitTermination(15,TimeUnit.SECONDS),"All worker/read/termination tasks complete");
            check(restarted.failures.get()==0 && cancelled.failures.get()==0 && tree.failures.get()==0 && cancelled.url.get()==null,"Intentional closes never report failure or readiness after cancellation");
            System.out.println("PASS: "+checks+" preview process/HTTP/crash/restart/cleanup"+(args.length>1?"/actual-Slidev":"")+" checks");
        } finally {
            sessions.forEach(Session::close);executor.shutdownNow();executor.awaitTermination(10,TimeUnit.SECONDS);
            try(var files=Files.walk(root)){for(Path path:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}
        }
    }
    private static void realSlidev(String node,Path project,Executor executor,List<Session> sessions)throws Exception {
        Path folder=Files.createTempDirectory(project,"acceptance-"),entry=folder.resolve("slides.md"),part=folder.resolve("child.md");
        String broken="# Imported\n\n{{ 1 + }}\n";
        try {
            Files.writeString(entry,"---\ntheme: default\n---\n# Root\n\n---\nsrc: ./child.md\n---\n");Files.writeString(part,broken);
            var session=new Session(executor,new CopyOnWriteArrayList<>());sessions.add(session);session.start(entry,node,false);session.awaitReady();
            String base=session.url.get();
            boolean reachable=false;String available=null;
            for(String candidate:List.of(base,base.replace("localhost","127.0.0.1"),base.replace("localhost","[::1]"))) {
                try {if(request(candidate+"1")==200){reachable=true;available=candidate;break;}}
                catch(java.io.IOException ignored){}
            }
            check(reachable,"Actual Slidev serves /1: "+session.log);
            base=available;
            var facade=URI.create(base+"@slidev/slides/2/md").toURL().openConnection(Proxy.NO_PROXY);facade.setConnectTimeout(1000);facade.setReadTimeout(15000);
            String facadeText;try(var input=facade.getInputStream()){facadeText=new String(input.readAllBytes(),StandardCharsets.UTF_8);}
            var importPath=java.util.regex.Pattern.compile("from [\"']([^\"']+__slidev_2\\.md[^\"']*)[\"']").matcher(facadeText);
            check(importPath.find(),"Slidev facade exposes imported module: "+facadeText);
            String module=URI.create(base).resolve(importPath.group(1)).toString();
            check(request(module)==500,"Actual imported expression fails Vue transform");
            var stream=new PreviewDiagnostics();until(()->{stream.clear();session.log.forEach(stream::accept);return stream.diagnostics().stream().anyMatch(d->d.slide()==2);},"Actual Slidev diagnostic captured");
            String script;try(var resource=PreviewServerTest.class.getResourceAsStream("/dev/slidev/intellij/load-deck.cjs")){script=new String(Objects.requireNonNull(resource).readAllBytes(),StandardCharsets.UTF_8);}
            var deck=DeckProcess.load(entry,Map.of(),node,script,()->{});
            var diagnostic=stream.diagnostics().stream().filter(d->d.slide()==2).findFirst().orElseThrow();
            until(()->{stream.clear();session.log.forEach(stream::accept);return stream.diagnostics().stream().anyMatch(d->d.slide()==2 && stream.locateCurrent(deck,d).isPresent());},"Actual compiler excerpt maps into imported source: "+session.log);
            diagnostic=stream.diagnostics().stream().filter(d->d.slide()==2).findFirst().orElseThrow();
            var mapped=stream.locateCurrent(deck,diagnostic).orElseThrow();
            check(mapped.path().equals(part) && mapped.location().line()==3,"Actual import error maps to original child line 3");
            Files.writeString(part,broken.replace("1 + }", "1 + 2 }"));
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);boolean recovered=false;
            while(System.nanoTime()<deadline){if(request(module+"&t="+System.currentTimeMillis())==200){recovered=true;break;}Thread.sleep(100);}
            check(recovered,"Same actual Slidev session recovers after source correction");session.close();
        } finally {try(var files=Files.walk(folder)){for(Path path:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
    }
}
