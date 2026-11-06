package dev.slidev.intellij.core;

import java.nio.file.*;
import java.util.*;

public final class PreviewDiagnosticsTest {
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    public static void main(String[] args)throws Exception {
        var stream=new PreviewDiagnostics();
        check(!stream.accept("12:00:00 [vite] (client) hmr update /slides.md"),"Normal HMR output is not an error");
        check(stream.accept("12:00:01 \u001b[31m[vite]\u001b[0m (client) Pre-transform error: Error parsing JavaScript expression: Unexpected token (1:4)"),"Error header immediately visible");
        check(stream.diagnostics().get(0).path().isEmpty(),"No guessed location before File record");
        check(!stream.accept("  Plugin: vite:vue"),"Plugin line does not create a diagnostic");
        check(stream.accept("  File: /a path/parts/child.md__slidev_2.md:5:7"),"Location arrives separately");
        var error=stream.diagnostics().get(0);
        check(stream.diagnostics().size()==1 && error.path().equals("/a path/parts/child.md") && error.slide()==2 && error.line()==5 && error.column()==7,"Virtual source coordinates parsed");
        check(!stream.accept("    at createCompilerError (/node_modules/@vue/compiler-core/core.js:12:3)\n  5 | {{ 1 + }}\n    |      ^"),"Stack and code-frame lines ignored");
        stream.accept("[vite] Internal server error: Error parsing JavaScript expression: Unexpected token (1:4)\n  File: /a path/parts/child.md__slidev_2.md:5:7");
        check(stream.diagnostics().size()==1,"Pre-transform/internal repeats deduplicated");
        stream.accept("[vite] Internal server error: Second error\nFile: C:\\Deck\\parts\\other.markdown__slidev_3.md?vue&type=template:8:2");
        check(stream.diagnostics().size()==2 && stream.diagnostics().get(1).path().equals("C:\\Deck\\parts\\other.markdown") && stream.diagnostics().get(1).slide()==3,"Windows paths and Vite query suffix");
        stream.clear();check(stream.diagnostics().isEmpty(),"Edit/restart clears accumulated errors and pending location");
        check(!stream.accept("at oldCompiler (/deck/slides.md__slidev_2.md:5:7)"),"Late stack frame after invalidation ignored");
        stream.accept("[plugin vite:vue] /deck/slides.md__slidev_2.md:5:7\nSyntaxError: Unexpected token");
        check(stream.diagnostics().get(0).message().equals("Unexpected token"),"Startup format message follows position");
        stream.clear();stream.accept("[vite] Internal server error: Invalid YAML\nFile: /deck/slides.md:2:6");
        check(stream.diagnostics().get(0).slide()==0 && stream.diagnostics().get(0).line()==2,"Direct source position is not a global slide");
        stream.clear();stream.accept("Error: Missing theme");
        check(stream.diagnostics().size()==1 && stream.diagnostics().get(0).line()==0,"Positionless startup errors retained");
        stream.clear();stream.accept("[vite] Internal server error: Bad component\nFile: /deck/components/Chart.vue:4:2");
        check(stream.diagnostics().get(0).path().endsWith("Chart.vue"),"External component remains a reported location");
        stream.clear();stream.accept("[vite] Internal server error: Bad source\nFile: /deck/slides.md__slidev_99999999999999.md:1:1");
        check(stream.diagnostics().get(0).path().isEmpty(),"Overflowing virtual number is not applied");
        stream.clear();stream.accept("[vite] Internal server error: Bad source\nFile: /deck/slides.md__slidev_2.md:99999999999999:1");
        check(stream.diagnostics().get(0).path().isEmpty(),"Overflowing line is not applied");
        stream.clear();for(int i=0;i<150;i++)stream.accept("[vite] Internal server error: Error "+i);
        check(stream.diagnostics().size()==100 && stream.diagnostics().get(0).message().equals("Error 50"),"History is bounded");
        Path entry=Path.of(System.getProperty("java.io.tmpdir"),"preview-diagnostic-fixture","slides.md").toAbsolutePath();Path child=entry.getParent().resolve("child.md");
        String main="# Intro\n",source="---\r\nlayout: default\r\n---\r\n# 🐇 Child\r\n\r\n{{ 1 + }}\r\n";
        int content=source.indexOf("# 🐇");
        var rootSlide=new ImportedDeck.Slide(1,entry,new DeckParser.Slide(1,0,main.length(),"Intro",null,0),List.of());
        var childSlide=new ImportedDeck.Slide(2,child,new DeckParser.Slide(1,0,source.length(),"Child",null,content),List.of());
        var deck=new ImportedDeck.Snapshot(entry,List.of(rootSlide,childSlide),Map.of(entry,main,child,source),Set.of(entry,child),List.of());
        var mapped=PreviewDiagnostics.locate(deck,new BuildDiagnostics.Diagnostic(child.toString(),2,3,4,"Invalid expression")).orElseThrow();
        check(mapped.path().equals(child) && mapped.location().offset()==source.indexOf("{{")+3 && mapped.location().line()==6,"Imported global error maps into original CRLF/Unicode source");
        check(PreviewDiagnostics.locate(deck,new BuildDiagnostics.Diagnostic(entry.toString(),2,3,4,"bad")).isEmpty(),"Mismatched virtual filename rejected");
        check(PreviewDiagnostics.locate(deck,new BuildDiagnostics.Diagnostic(child.toString(),99,3,4,"bad")).isEmpty(),"Unknown global slide rejected");
        check(PreviewDiagnostics.locate(deck,new BuildDiagnostics.Diagnostic(child.toString(),0,6,4,"bad")).orElseThrow().location().offset()==source.indexOf("{{")+3,"Direct Markdown position maps only inside a known source");
        check(PreviewDiagnostics.locate(deck,new BuildDiagnostics.Diagnostic(entry.getParent().resolve("Chart.vue").toString(),0,1,1,"bad")).isEmpty(),"Non-deck source not assigned a guessed Markdown location");
        var invalid=new ImportedDeck.Snapshot(entry,deck.slides(),deck.sources(),deck.watched(),List.of(new ImportedDeck.Issue(entry,0,"unavailable")));
        check(PreviewDiagnostics.locate(invalid,new BuildDiagnostics.Diagnostic(child.toString(),2,3,4,"bad")).isEmpty(),"Unreliable global mapping disabled");
        for(String file:args) {
            stream.clear();for(String line:Files.readAllLines(Path.of(file)))stream.accept(line);
            check(stream.diagnostics().size()==1,"Actual Vite log yields exactly one diagnostic");
            var actual=stream.diagnostics().get(0);
            check(actual.slide()==2 && actual.line()==3 && actual.column()==4 && actual.message().contains("Error parsing JavaScript expression"),"Actual Vite/Vue coordinates and message");
        }
        System.out.println("PASS: "+checks+" live preview diagnostic parsing/mapping"+(args.length>0?"/actual-Vite":"")+" checks");
    }
}
