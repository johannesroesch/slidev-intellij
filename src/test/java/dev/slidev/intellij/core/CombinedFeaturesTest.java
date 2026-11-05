package dev.slidev.intellij.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Quick-fix plans, graph/protocol coordinates, optionally a real installed Slidev parser. */
public final class CombinedFeaturesTest {
    private static int checks;
    private static void check(boolean value,String text){if(!value)throw new AssertionError(text);checks++;}
    private static String b64(String text){return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));}
    private static String record(String... values){return "slidev-deck\t"+String.join("\t",values)+"\n";}
    public static void main(String[] args)throws Exception {
        String source="---\nhide: maybe # keep this\n---\n# Slide\n";int start=source.indexOf("maybe");
        var fixes=QuickFixes.values(source,start,start+5,SlidevSchema.find("hide",false));
        check(fixes.size()==2,"Boolean choices");
        check(fixes.get(0).apply(source).contains("hide: true # keep this"),"Only scalar replaced, comment preserved");
        check(fixes.get(1).apply(source).contains("hide: false"),"Alternative boolean");
        boolean stale=false;try{fixes.get(0).apply(source.replace("maybe","other"));}catch(IllegalStateException expected){stale=true;}
        check(stale,"Stale edit rejected");
        String quoted="hide: 'true'";
        check(QuickFixes.values(quoted,6,quoted.length(),SlidevSchema.find("hide",false)).get(0).apply(quoted).equals("hide: true"),"Quoted boolean corrected");
        String layout="layout: two-col";var reference=new ProjectReferences.Reference(8,layout.length(),"two-col",ProjectReferences.Kind.LAYOUT);
        var layouts=QuickFixes.layouts(layout,reference,List.of("center","two-cols","default","two-cols"));
        check(layouts.size()==3 && layouts.get(0).replacement().equals("two-cols"),"Closest layouts ranked and deduplicated");
        check(layouts.get(0).apply(layout).equals("layout: two-cols"),"Layout replacement range");
        String fence="# Slide\n````js\nconsole.log(1)";var problem=MetadataRegions.scan(fence).problems().get(0);
        check(QuickFixes.closing(fence,problem).orElseThrow().apply(fence).endsWith("\n````\n"),"Matching long code fence");
        String metadata="---\r\ntheme: default\r\n";var metaProblem=MetadataRegions.scan(metadata).problems().get(0);
        check(QuickFixes.closing(metadata,metaProblem).orElseThrow().apply(metadata).endsWith("\r\n---\r\n"),"CRLF frontmatter closure");
        String ambiguous="---\ntheme: default\n\n# Is this a comment or slide?";
        check(QuickFixes.closing(ambiguous,MetadataRegions.scan(ambiguous).problems().get(0)).isEmpty(),"Ambiguous boundary has no guessed fix");
        Path entry=Path.of(System.getProperty("java.io.tmpdir"),"slidev-combined-"+UUID.randomUUID(),"slides.md").toAbsolutePath();
        Path child=entry.getParent().resolve("child.md");String root="# Intro\n---\nsrc: ./child.md\n---\n";String imported="# 🐇\r\n\r\n{{ 1 + }}\r\n";
        int anchor=root.indexOf("---");String site=b64(entry.toString())+"\t2\t"+anchor+"\t"+root.length();
        String response=record("ready")+record("source",b64(entry.toString()),b64(root))+record("source",b64(child.toString()),b64(imported))+
            record("slide",b64(entry.toString()),"1","0","0","8",b64("Intro"),b64(""))+
            record("slide",b64(child.toString()),"1","0","0",Integer.toString(imported.length()),b64("🐇"),b64(site))+
            record("slide",b64(child.toString()),"1","0","0",Integer.toString(imported.length()),b64("🐇 repeated"),b64(site));
        var graph=DeckProcess.parse(entry,response);
        check(graph.reliable() && graph.slides().size()==3,"Graph protocol parsed");
        check(graph.at(child,0,3).orElseThrow().number()==3,"Repeated source keeps preferred occurrence");
        check(graph.at(child,0,0).orElseThrow().number()==2,"Repeated source defaults deterministically");
        check(graph.at(entry,root.indexOf("src:"),3).orElseThrow().number()==3,"Importer placeholder maps through its branch");
        check(graph.byNumber(2).orElseThrow().path().equals(child),"Reverse navigation target file");
        var diagnostic=new BuildDiagnostics.Diagnostic(child.toString(),2,3,1,"Unexpected token");
        var mapped=graph.locate(diagnostic).orElseThrow();
        check(mapped.path().equals(child) && mapped.location().offset()==imported.indexOf("{{"),"Build diagnostic global ID maps into imported CRLF/Unicode source");
        check(graph.locate(new BuildDiagnostics.Diagnostic(entry.toString(),2,3,1,"bad")).isEmpty(),"Mismatched virtual source filename is not guessed");
        check(graph.locate(new BuildDiagnostics.Diagnostic(child.toString(),99,3,1,"bad")).isEmpty(),"Unknown global ID rejected");
        boolean malformed=false;try{DeckProcess.parse(entry,record("ready")+record("source",b64(entry.toString()),b64(root))+record("slide",b64(entry.toString()),"1","-1","0","8",b64("bad"),b64("")));}catch(java.io.IOException expected){malformed=true;}
        check(malformed,"Invalid protocol range rejected");
        var missing=new ProjectReferences.Reference(0,10,"./new-file.md#2",ProjectReferences.Kind.IMPORT);
        check(ProjectReferences.missingSourceTarget(entry,missing).orElseThrow().equals(entry.getParent().resolve("new-file.md")),"Missing source creation target excludes range");
        check(ProjectReferences.missingSourceTarget(entry,new ProjectReferences.Reference(0,3,"https://example.com/a.md",ProjectReferences.Kind.IMPORT)).isEmpty(),"No remote file creation action");
        if(args.length==2) {
            Path folder=Files.createTempDirectory(Path.of(args[1]),"slidev-combined-");
            try {
                Path main=folder.resolve("slides.md"),part=folder.resolve("part.md");
                Files.writeString(main,"# Main\n---\nsrc: ./part.md\n---\n\n---\n\n# End\n\n---\nsrc: ./part.md#2,1,2\n---\n");
                Files.writeString(part,"# A🐇\r\n\r\n{{ 1 + }}\r\n---\r\n\r\n# B\r\n");
                String script;try(var input=CombinedFeaturesTest.class.getResourceAsStream("/dev/slidev/intellij/load-deck.cjs")){if(input==null)throw new IllegalStateException("Graph resource missing");script=new String(input.readAllBytes(),StandardCharsets.UTF_8);}
                var actual=DeckProcess.load(main,Map.of(),args[0],script,() -> {});
                check(actual.reliable() && actual.slides().size()==6,"Actual parser expands repeated imports");
                check(actual.byNumber(5).orElseThrow().path().equals(part),"Actual repeated import source");
                check(actual.at(part,0,5).orElseThrow().number()==5,"Actual repeated occurrence cursor mapping");
                check(actual.locate(new BuildDiagnostics.Diagnostic(part.toString(),2,3,1,"bad")).orElseThrow().location().offset()==Files.readString(part).indexOf("{{"),"Actual global virtual diagnostic mapping");
                var dirty=DeckProcess.load(main,Map.of(part,"# Dirty\n---\n\n# B\n"),args[0],script,() -> {});
                check(dirty.byNumber(2).orElseThrow().source().title().equals("Dirty"),"Actual unsaved override");
                Files.writeString(main,"# Main\n---\nsrc: ./created.md\n---\n");
                var absent=DeckProcess.load(main,Map.of(),args[0],script,() -> {});
                check(!absent.reliable() && absent.watched().contains(folder.resolve("created.md")),"Missing file invalidates mapping but remains watched");
                Files.writeString(folder.resolve("created.md"),"# New slide\n");
                check(DeckProcess.load(main,Map.of(),args[0],script,() -> {}).slides().size()==2,"Mapping recovers after source creation");
            } finally {try(var files=Files.walk(folder)){for(Path path:files.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}}
        }
        System.out.println("PASS: "+checks+" quick-fix/graph/diagnostic"+(args.length==2?"/real-parser":"")+" checks");
    }
}
