package dev.slidev.intellij.core;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public final class VueDiagnosticsTest {
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private record Response(int status,String body){}
    private static Response request(String url)throws Exception {
        var connection=(HttpURLConnection)URI.create(url.replace(" ","%20")).toURL().openConnection(Proxy.NO_PROXY);
        connection.setConnectTimeout(1000);connection.setReadTimeout(15000);
        try {int status=connection.getResponseCode();try(var input=status>=400?connection.getErrorStream():connection.getInputStream()){return new Response(status,input==null?"":new String(input.readAllBytes(),StandardCharsets.UTF_8));}}
        finally{connection.disconnect();}
    }
    public static void main(String[] args)throws Exception {
        Path root=Files.createTempDirectory("slidev-vue-diagnostics-"),entry=root.resolve("slides.md"),file=root.resolve("components/Bad chart.vue");
        Files.createDirectories(file.getParent());
        String text="<template>\n  <div>{{ 1 + }}</div>\n</template>\n";
        try {
            Files.writeString(file,"\ufeff"+text.replace("\n","\r\n"));
            var source=DiagnosticSources.read(file).orElseThrow();
            check(source.text().equals(text),"Vue snapshot normalizes BOM/CRLF like IntelliJ");
            check(source.position(2,10).orElseThrow().offset()==text.indexOf("  <div>")+9,"Normalized UTF-16 offset");
            check(source.position(2,999).isEmpty() && source.position(999,1).isEmpty() && source.position(2,0).isEmpty(),"Invalid coordinates are not clamped into source");
            check(!source.matches(text.replace("1 +", "1 + 2")),"Changed native document rejects stored snapshot");
            var diagnostic=new BuildDiagnostics.Diagnostic(file.toString(),0,2,10,"bad");
            check(DiagnosticSources.vuePath(entry,diagnostic).orElseThrow().equals(file),"Absolute Vue target");
            check(DiagnosticSources.vuePath(entry,new BuildDiagnostics.Diagnostic("components/Bad chart.vue",0,2,10,"bad")).orElseThrow().equals(file),"Relative Vue target");
            check(DiagnosticSources.vuePath(entry,new BuildDiagnostics.Diagnostic(file.toUri().toString(),0,2,10,"bad")).orElseThrow().equals(file),"Encoded file URI target");
            check(DiagnosticSources.vuePath(entry,new BuildDiagnostics.Diagnostic("/@fs"+file,0,2,10,"bad")).orElseThrow().equals(file),"Vite fs path");
            check(DiagnosticSources.vuePath(entry,new BuildDiagnostics.Diagnostic("https://example.org/Bad.vue",0,2,10,"bad")).isEmpty(),"Remote targets are not local files");
            check(DiagnosticSources.vuePath(entry,new BuildDiagnostics.Diagnostic(file.toString(),2,2,10,"bad")).isEmpty(),"Slide diagnostics do not become Vue file targets");
            check(DiagnosticSources.read(root.resolve("missing.vue")).isEmpty(),"Missing Vue source remains unavailable");
            Path large=root.resolve("large.vue");Files.writeString(large,"x".repeat(2_000_001));check(DiagnosticSources.read(large).isEmpty(),"Oversized Vue source is bounded");
            var parser=new PreviewDiagnostics();parser.accept("[vite] Internal server error: bad\nFile: "+file+":2:10\n2 |   <div>{{ 1 + }}</div>");
            var d=parser.diagnostics().get(0);check(parser.locateVue(entry,d,source,null).orElseThrow().location().line()==2,"Direct component diagnostic maps to native file");
            String repeated=text+"  <div>{{ 1 + }}</div>\n";var duplicate=new DiagnosticSources.Source(file,repeated);
            check(parser.locateVue(entry,d,duplicate,null).isEmpty(),"Repeated Vue excerpt without original compiler proof remains ambiguous");
            parser.clear();parser.accept("[plugin vite:vue] "+file+"?vue&type=script:1:10\nSyntaxError: transformed");d=parser.diagnostics().get(0);
            check(parser.locateVue(entry,d,source,null).isEmpty(),"Generated query without map/excerpt never guesses original coordinates");
            parser.accept("1 |   <div>{{ 1 + }}</div>");
            check(parser.locateVue(entry,d,source,null).orElseThrow().location().line()==2,"Unique excerpt maps a transformed Vue block");
            check(parser.locateVue(entry,d,duplicate,null).isEmpty(),"Generated repeated excerpt remains ambiguous");
            parser.clear();parser.accept("[plugin vite:vue] "+file+":2:10\nRolldownError: first error\n[plugin vite:vue] "+root.resolve("Layout.vue")+":4:3\nSyntaxError: second error");
            check(parser.diagnostics().size()==2 && parser.diagnostics().get(1).message().equals("second error"),"Multiple Vue build diagnostics retain their own message");
            parser.invalidateVue(entry,file);check(parser.diagnostics().size()==1 && parser.diagnostics().get(0).path().endsWith("Layout.vue"),"File invalidation preserves unrelated errors");
            int offset=text.indexOf("1 +");
            var checked=new ExpressionChecker.Result(List.of(new ExpressionChecker.Diagnostic(offset,offset+3,"Error parsing JavaScript expression: Unexpected token (1:4)")),null);
            var shifted=new BuildDiagnostics.Diagnostic(file.toString(),0,7,11,"Error parsing JavaScript expression: Unexpected token (1:4)");
            parser.clear();
            check(parser.locateVue(entry,shifted,source,checked).orElseThrow().location().line()==2,"Original compiler evidence corrects Slidev injection without a map");
            var repeatedErrors=new ExpressionChecker.Result(List.of(checked.diagnostics().get(0),new ExpressionChecker.Diagnostic(offset+1,offset+2,"Error parsing JavaScript expression: Unexpected token (1:9)")),null);
            check(parser.locateVue(entry,shifted,source,repeatedErrors).isEmpty(),"Repeated compiler messages never select an arbitrary error");
            check(parser.locateVue(entry,shifted,source,new ExpressionChecker.Result(List.of(),null)).isEmpty(),"No original compiler error means no guessed transformed location");
            check(parser.locateVue(entry,shifted,source,new ExpressionChecker.Result(List.of(),"missing compiler")).isEmpty(),"Unavailable compiler without excerpt leaves only file navigation");
            var ts=new BuildDiagnostics.Diagnostic(file.toString(),0,9,14,"[vue/compiler-sfc] Unexpected token (9:14)");
            var tsErrors=new ExpressionChecker.Result(List.of(new ExpressionChecker.Diagnostic(offset,offset+1,"[vue/compiler-sfc] Unexpected token (2:14)")),null);
            check(parser.locateVue(entry,ts,source,tsErrors).orElseThrow().location().offset()==offset,"Script coordinates do not leak into message correlation");
            check(parser.locateVue(entry,new BuildDiagnostics.Diagnostic(file.toString(),0,7,11,"Different error"),source,checked).isEmpty(),"Unrelated server/compiler messages are not correlated");
            if(args.length==2)actual(args[0],Path.of(args[1]));
            System.out.println("PASS: "+checks+" Vue source/navigation/invalidation"+(args.length==2?"/actual-Slidev":"")+" checks");
        } finally {try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
    }
    private static void actual(String node,Path project)throws Exception {
        Path root=Files.createTempDirectory(project,"vue-acceptance-"),entry=root.resolve("slides.md");
        var pool=Executors.newCachedThreadPool();var log=new CopyOnWriteArrayList<String>();var ready=new CountDownLatch(1);var address=new AtomicReference<String>();var failure=new AtomicReference<String>();
        var server=new PreviewServer(log::add,pool);
        try {
            Files.createDirectories(root.resolve("components"));Files.createDirectories(root.resolve("layouts"));
            Files.writeString(entry,"---\ntheme: default\n---\n# Root\n\n<Bad />\n");
            Path component=root.resolve("components/Bad.vue"),layout=root.resolve("layouts/broken.vue");
            server.start(entry,node,false,value->{address.set(value);ready.countDown();},failure::set);
            check(ready.await(60,TimeUnit.SECONDS),"Actual Slidev ready: "+failure.get()+log);
            String base=null;
            for(String candidate:List.of(address.get(),address.get().replace("localhost","127.0.0.1"),address.get().replace("localhost","[::1]")))try{if(request(candidate+"1").status()==200){base=candidate;break;}}catch(java.io.IOException ignored){}
            check(base!=null,"Actual Slidev reachable");
            String[][] cases={{"<template>\n  <div>{{ 1 + }}</div>\n</template>\n","2"},{"<template>\n  <div :class=\"{ broken: }\" />\n</template>\n","2"},{"<script setup lang=\"ts\">\nconst broken: = 1\n</script>\n<template><div/></template>\n","2"},{"<template><div>🐇</div></template>\n<script setup lang=\"ts\">\nconst broken: = 1\n</script>\n","3"}};
            for(int i=0;i<cases.length;i++) {
                Path target=i==1?layout:component;log.clear();Files.writeString(target,cases[i][0]);
                var source=DiagnosticSources.read(target).orElseThrow();var checked=DiagnosticSources.analyze(source,entry,node,()->{});
                String url=base+"@fs"+target+"?import&t="+System.currentTimeMillis();
                var response=request(url);check(response.status()==500,"Actual Vue case "+i+" is rejected");
                check(!response.body().contains("sourceMappingURL=data:") && !response.body().contains("\"map\":"),"Failed transform does not expose a current usable Source Map");
                var parser=new PreviewDiagnostics();BuildDiagnostics.Diagnostic found=null;
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
                while(System.nanoTime()<deadline){parser.clear();log.forEach(parser::accept);found=parser.diagnostics().stream().filter(d->DiagnosticSources.vuePath(entry,d).filter(target::equals).isPresent()).findFirst().orElse(null);if(found!=null)break;Thread.sleep(50);}
                check(found!=null,"Actual Vue source diagnostic captured: "+log);
                var point=parser.locateVue(entry,found,source,checked).orElseThrow(()->new AssertionError("Unmapped Vue diagnostic: "+log));
                check(point.path().equals(target) && point.location().line()==Integer.parseInt(cases[i][1]),"Actual Vue component/layout/TypeScript error maps to original source line");
                Files.writeString(target,"<template><div>fixed</div></template>\n");
                boolean recovered=false;deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
                while(System.nanoTime()<deadline){if(request(url+"&changed="+System.nanoTime()).status()==200){recovered=true;break;}Thread.sleep(100);}
                check(recovered,"Actual Vue correction compiles in same server");
            }
            Files.writeString(component,cases[0][0]);
            Path output=Files.createTempDirectory("vue-build-output-");
            try {
                Path cli=project.resolve("node_modules/@slidev/cli/bin/slidev.mjs");
                Process child=new ProcessBuilder(node,cli.toString(),"build",entry.toString(),"--out",output.toString()).directory(project.toFile()).redirectErrorStream(true).start();
                var captured=pool.submit(()->new String(child.getInputStream().readAllBytes(),StandardCharsets.UTF_8));
                try {
                    check(child.waitFor(120,TimeUnit.SECONDS) && child.exitValue()!=0,"Actual Slidev build fails on component error");
                    var parser=new PreviewDiagnostics();parser.accept(captured.get(10,TimeUnit.SECONDS));
                    var d=parser.diagnostics().stream().filter(error->DiagnosticSources.vuePath(entry,error).filter(component::equals).isPresent()).findFirst().orElseThrow(()->new AssertionError("Build Vue diagnostic missing"));
                    check(parser.locateVue(entry,d,DiagnosticSources.read(component).orElseThrow(),DiagnosticSources.analyze(DiagnosticSources.read(component).orElseThrow(),entry,node,()->{})).orElseThrow().location().line()==2,"Actual Vue build error maps to original component");
                } finally {if(child.isAlive())PreviewServer.terminate(child);}
            } finally {try(var files=Files.walk(output)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
        } finally {
            server.close();pool.shutdown();if(!pool.awaitTermination(15,TimeUnit.SECONDS)){pool.shutdownNow();pool.awaitTermination(5,TimeUnit.SECONDS);}
            try(var files=Files.walk(root)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}
        }
    }
}
