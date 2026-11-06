package dev.slidev.intellij.core;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/** Bounded, incremental Vite dev-server diagnostics. Stack frames are never locations. */
public final class PreviewDiagnostics {
    private static final Pattern HEADER=Pattern.compile("(?:^|.*?\\[vite\\].*?)(?:Internal server error|Pre-transform error):\\s*(.+)");
    private static final Pattern GENERIC=Pattern.compile("^(?:SyntaxError|RolldownError|Error|YAMLException|YAMLParseError):\\s*(.+)");
    private static final Pattern POSITION=Pattern.compile("^(.+?)(?::(\\d+):(\\d+)|\\s+\\((\\d+):(\\d+)\\))\\s*(?::\\s*(.*))?$");
    private static final Pattern VIRTUAL=Pattern.compile("^(.+\\.(?:md|markdown))__slidev_(\\d+)\\.md$");
    private final List<BuildDiagnostics.Diagnostic> diagnostics=new ArrayList<>();
    private String message;
    private BuildDiagnostics.Diagnostic pending;

    public List<BuildDiagnostics.Diagnostic> diagnostics(){return List.copyOf(diagnostics);}
    public void clear(){diagnostics.clear();message=null;pending=null;}
    public boolean accept(String output) {
        if(output==null)return false;
        var before=diagnostics();
        for(String raw:BuildDiagnostics.clean(output).split("\n")) {
            if(raw.length()>16384)continue;
            String text=raw.strip();if(text.isEmpty() || text.startsWith("at ") || text.matches("\\d+\\s*\\|.*") || text.startsWith("^"))continue;
            var header=HEADER.matcher(text);
            if(header.matches()) {begin(header.group(1));continue;}
            var generic=GENERIC.matcher(text);
            if(generic.matches()) {
                if(message==null)begin(generic.group(1));
                else if(message.equals("Slidev compilation error")) {
                    message=generic.group(1);
                    if(pending!=null)replace(new BuildDiagnostics.Diagnostic(pending.path(),pending.slide(),pending.line(),pending.column(),message));
                }
                continue;
            }
            if(text.regionMatches(true,0,"File:",0,5)) {
                if(message==null)begin("Slidev compilation error");
                setLocation(text.substring(5).strip());continue;
            }
            // Also accept the explicit virtual-file format used by startup failures.
            if(text.contains("__slidev_") && !text.startsWith("(")) {
                String location=text.replaceFirst("^.*?\\[plugin [^]]+\\]\\s*", "").replaceFirst("^.*?\\[vite:[^]]+\\]\\s*", "").replaceFirst("^file:\\s*", "");
                var position=POSITION.matcher(location);
                if(position.matches()) {
                    if(message==null)begin(position.group(6)!=null?position.group(6):"Slidev compilation error");
                    setLocation(location);
                }
            }
        }
        return !before.equals(diagnostics);
    }
    private void begin(String text) {
        message=text.length()>2000?text.substring(0,2000):text;pending=null;
        replace(new BuildDiagnostics.Diagnostic("",0,0,0,message));
    }
    private void setLocation(String text) {
        var position=POSITION.matcher(text);String path=text;int line=0,column=0;
        if(position.matches()) {
            path=position.group(1);
            try {line=Integer.parseInt(position.group(2)!=null?position.group(2):position.group(4));column=Integer.parseInt(position.group(3)!=null?position.group(3):position.group(5));}
            catch(NumberFormatException ignored){return;}
        }
        int query=path.indexOf('?');if(query>=0)path=path.substring(0,query);
        if(!path.matches(".+\\.(?:md|markdown|vue|[cm]?[jt]sx?|ya?ml|json|css|scss)"))return;
        int slide=0;var virtual=VIRTUAL.matcher(path);
        if(virtual.matches()) {path=virtual.group(1);try{slide=Integer.parseInt(virtual.group(2));}catch(NumberFormatException ignored){return;}}
        replace(new BuildDiagnostics.Diagnostic(path,slide,line,column,message));
    }
    private void replace(BuildDiagnostics.Diagnostic next) {
        if(pending!=null)diagnostics.remove(pending);
        // Internal/pre-transform reports of the same error share one list entry.
        diagnostics.removeIf(item -> item.equals(next) || (!next.path().isEmpty() && item.path().equals(next.path()) && item.slide()==next.slide() && item.line()==next.line() && item.column()==next.column()));
        diagnostics.add(next);pending=next;
        if(diagnostics.size()>100)diagnostics.remove(0);
    }
    public static Optional<ImportedDeck.MappedDiagnostic> locate(ImportedDeck.Snapshot deck,BuildDiagnostics.Diagnostic diagnostic) {
        if(deck==null || diagnostic.path().isEmpty())return Optional.empty();
        if(diagnostic.slide()>0)return deck.locate(diagnostic);
        try {
            Path path=Path.of(diagnostic.path());if(!path.isAbsolute())path=deck.entry().getParent().resolve(path);path=ImportedDeck.normalize(path);
            String text=deck.sources().get(path);if(text==null)return Optional.empty();
            Path target=path;
            return BuildDiagnostics.locate(text,0,text.length(),diagnostic.line(),diagnostic.column()).map(value -> new ImportedDeck.MappedDiagnostic(target,value));
        } catch(RuntimeException ignored){return Optional.empty();}
    }
}
