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
    private static final Pattern CODE_FRAME=Pattern.compile("^\\s*(?:>\\s*)?(\\d+)\\s*\\| ?(.*)$");
    private final Map<BuildDiagnostics.Diagnostic,String> frames=new HashMap<>();
    private final List<BuildDiagnostics.Diagnostic> diagnostics=new ArrayList<>();
    private String message;
    private BuildDiagnostics.Diagnostic pending;

    public List<BuildDiagnostics.Diagnostic> diagnostics(){return List.copyOf(diagnostics);}
    public void clear(){diagnostics.clear();frames.clear();message=null;pending=null;}
    public boolean accept(String output) {
        if(output==null)return false;
        var before=diagnostics();boolean frameChanged=false;
        for(String raw:BuildDiagnostics.clean(output).split("\n")) {
            if(raw.length()>16384)continue;
            var codeFrame=CODE_FRAME.matcher(raw);
            if(codeFrame.matches()) {
                try {if(pending!=null && Integer.parseInt(codeFrame.group(1))==pending.line()) {
                    String prior=frames.put(pending,codeFrame.group(2));frameChanged|=!Objects.equals(prior,codeFrame.group(2));
                }}catch(NumberFormatException ignored){}
                continue;
            }
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
            if((text.contains("__slidev_") || text.startsWith("[plugin ") || text.startsWith("[vite:")) && !text.startsWith("(")) {
                String location=text.replaceFirst("^.*?\\[plugin [^]]+\\]\\s*", "").replaceFirst("^.*?\\[vite:[^]]+\\]\\s*", "").replaceFirst("^file:\\s*", "");
                var position=POSITION.matcher(location);
                if(position.matches()) {
                    if(message==null || text.startsWith("[plugin ") || text.startsWith("[vite:"))begin(position.group(6)!=null?position.group(6):"Slidev compilation error");
                    setLocation(location);
                }
            }
        }
        return frameChanged || !before.equals(diagnostics);
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
        var diagnostic=new BuildDiagnostics.Diagnostic(path,slide,line,column,message);
        replace(diagnostic);
    }
    private void replace(BuildDiagnostics.Diagnostic next) {
        boolean same=pending!=null && pending.path().equals(next.path()) && pending.slide()==next.slide() && pending.line()==next.line() && pending.column()==next.column();
        String previousFrame=same?frames.get(pending):null;
        if(pending!=null)diagnostics.remove(pending);
        // Internal/pre-transform reports of the same error share one list entry.
        diagnostics.removeIf(item -> item.equals(next) || (!next.path().isEmpty() && item.path().equals(next.path()) && item.slide()==next.slide() && item.line()==next.line() && item.column()==next.column()));
        diagnostics.add(next);pending=next;
        if(diagnostics.size()>100)diagnostics.remove(0);
        frames.keySet().retainAll(diagnostics);
        if(previousFrame!=null)frames.put(next,previousFrame);
    }
    public void invalidateVue(Path entry,Path path) {
        diagnostics.removeIf(d->DiagnosticSources.vuePath(entry,d).filter(path::equals).isPresent());
        frames.keySet().retainAll(diagnostics);
        if(pending!=null && !diagnostics.contains(pending)){pending=null;message=null;}
    }
    public Optional<ImportedDeck.MappedDiagnostic> locateVue(Path entry,BuildDiagnostics.Diagnostic diagnostic,DiagnosticSources.Source source,ExpressionChecker.Result checked) {
        if(DiagnosticSources.vuePath(entry,diagnostic).filter(source.path()::equals).isEmpty())return Optional.empty();
        var compiled=DiagnosticSources.compilerPosition(source,diagnostic,checked);
        if(compiled.isPresent())return compiled.map(point->new ImportedDeck.MappedDiagnostic(source.path(),point));
        // Slidev injects code without a Source Map. Bare .vue coordinates alone are
        // not evidence of original positions, even when the query string is absent.
        String frame=frames.get(diagnostic);if(frame==null)return Optional.empty();
        var normalized=new BuildDiagnostics.Diagnostic(source.path().toString(),0,diagnostic.line(),diagnostic.column(),diagnostic.message());
        var temporary=new PreviewDiagnostics();temporary.frames.put(normalized,frame);
        var snapshot=new ImportedDeck.Snapshot(entry,List.of(),Map.of(source.path(),source.text()),Set.of(source.path()),List.of());
        return temporary.locateCurrent(snapshot,normalized);
    }
    /** Match a compiler excerpt to one source line, accounting for generated <p> wrappers.
     * No Source Map is implied: ambiguous/changed excerpts deliberately stay unmapped. */
    public Optional<ImportedDeck.MappedDiagnostic> locateCurrent(ImportedDeck.Snapshot deck,BuildDiagnostics.Diagnostic diagnostic) {
        String frame=frames.get(diagnostic);if(frame==null)return locate(deck,diagnostic);
        var anchor=locate(deck,new BuildDiagnostics.Diagnostic(diagnostic.path(),diagnostic.slide(),1,1,diagnostic.message()));
        if(anchor.isEmpty())return Optional.empty();
        Path path=anchor.get().path();String text=deck.sources().get(path);int begin=0,end=text.length();
        if(diagnostic.slide()>0){var slide=deck.byNumber(diagnostic.slide()).orElseThrow();begin=slide.source().contentStart();end=slide.source().end();}
        BuildDiagnostics.Location found=null;
        for(int start=begin;start<end;) {
            int finish=text.indexOf('\n',start);if(finish<0 || finish>end)finish=end;
            String raw=text.substring(start,finish).replace("\r", ""),line=raw.strip();
            if(line.length()>=3) {
                int generated=frame.indexOf(line);
                if(generated>=0 && generated==frame.lastIndexOf(line) &&
                    frame.substring(0,generated).matches("(?:\\s*<[^>]+>)*\\s*") &&
                    frame.substring(generated+line.length()).matches("(?:\\s*<[^>]+>)*\\s*")) {
                    int relative=diagnostic.column()-1-generated;
                    if(relative>=0 && relative<line.length()) {
                        if(found!=null)return Optional.empty();
                        int indentation=raw.length()-raw.stripLeading().length(),offset=start+indentation+relative;
                        int originalLine=1;for(int i=0;i<start;i++)if(text.charAt(i)=='\n')originalLine++;
                        found=new BuildDiagnostics.Location(offset,originalLine,indentation+relative+1);
                    }
                }
            }
            start=finish+1;
        }
        return found==null?Optional.empty():Optional.of(new ImportedDeck.MappedDiagnostic(path,found));
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
