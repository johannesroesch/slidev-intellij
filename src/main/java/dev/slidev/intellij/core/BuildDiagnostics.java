package dev.slidev.intellij.core;

import java.util.*;
import java.util.regex.Pattern;

/** CLI diagnostics for Vite 7/Rollup and Vite 8/Rolldown. Generated positions
 * are mapped relative to slide content; custom transformations can shift them. */
public final class BuildDiagnostics {
    public record Diagnostic(String path, int slide, int line, int column, String message) {}
    public record Location(int offset, int line, int column) {}
    private static final Pattern LOCATION = Pattern.compile("(.+?\\.(?:md|markdown))(?:__slidev_(\\d+)\\.md)(?::(\\d+):(\\d+)|\\s*\\((\\d+):(\\d+)\\))");
    public static String clean(String text) {
        return text.replaceAll("\\x1B\\[[0-?]*[ -/]*[@-~]", "").replace('\r', '\n');
    }
    public static List<Diagnostic> parse(String output) {
        List<Diagnostic> result=new ArrayList<>();
        String path=null, message=null; int slide=0, line=0, column=0;
        for(String text:clean(output).split("\n")) {
            var match=LOCATION.matcher(text);
            if(match.find()) {
                if(path!=null && message!=null) add(result,new Diagnostic(path,slide,line,column,message));
                path=match.group(1).replaceFirst("^.*?\\[plugin [^]]+\\]\\s*", "")
                    .replaceFirst("^.*?\\[vite:[^]]+\\]\\s*", "").replaceFirst("^file:\\s*", "").strip();
                slide=Integer.parseInt(match.group(2));
                line=Integer.parseInt(match.group(3)!=null?match.group(3):match.group(5));
                column=Integer.parseInt(match.group(4)!=null?match.group(4):match.group(6));
                message=null;
                String tail=text.substring(match.end()).replaceFirst("^\\s*:\\s*", "").strip();
                if(!tail.isEmpty()) message=tail;
            } else if(path!=null && message==null && text.strip().matches("(?:SyntaxError|RolldownError|Error):.*")) {
                message=text.strip().replaceFirst("^[^:]+:\\s*", "");
            }
        }
        if(path!=null && message!=null) add(result,new Diagnostic(path,slide,line,column,message));
        return List.copyOf(result);
    }
    private static void add(List<Diagnostic> target, Diagnostic diagnostic) {
        if(!target.contains(diagnostic)) target.add(diagnostic);
    }
    public static Optional<Location> locate(String source, Diagnostic diagnostic) {
        var slides=DeckParser.parse(source).slides();
        if(diagnostic.slide()<1 || diagnostic.slide()>slides.size() || diagnostic.line()<1 || diagnostic.column()<1) return Optional.empty();
        var slide=slides.get(diagnostic.slide()-1);
        int start=slide.contentStart();
        for(int i=1;i<diagnostic.line();i++) {
            int next=source.indexOf('\n',start);
            if(next<0 || next+1>=slide.end()) return Optional.empty();
            start=next+1;
        }
        if(start>=slide.end()) return Optional.empty();
        int end=source.indexOf('\n',start);
        if(end<0 || end>slide.end()) end=slide.end();
        if(end>start && source.charAt(end-1)=='\r') end--;
        int offset=Math.min(end,start+diagnostic.column()-1);
        int originalLine=1;
        for(int i=0;i<start;i++) if(source.charAt(i)=='\n') originalLine++;
        return Optional.of(new Location(offset,originalLine,offset-start+1));
    }
    private BuildDiagnostics() {}
}
