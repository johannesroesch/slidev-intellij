package dev.slidev.intellij.core;

import java.nio.file.Path;
import java.util.*;

/** Deck-wide positions from the installed Slidev parser, including importer coordinates. */
public final class ImportedDeck {
    public record Site(Path path,int localNumber,int start,int end) {}
    public record Slide(int number,Path path,DeckParser.Slide source,List<Site> sites) {
        @Override public String toString() {return number+". "+source.title()+" ["+path.getFileName()+"]";}
    }
    public record Issue(Path path,int offset,String message) {}
    public record Snapshot(Path entry,List<Slide> slides,Map<Path,String> sources,Set<Path> watched,List<Issue> issues) {
        public boolean reliable() {return issues.isEmpty();}
        public Optional<Slide> byNumber(int number) {return number<1 || number>slides.size()?Optional.empty():Optional.of(slides.get(number-1));}
        public Optional<Slide> at(Path path,int offset,int preferred) {
            path=normalize(path);String text=sources.get(path);if(text==null)return Optional.empty();
            if(offset<0 || offset>text.length())return Optional.empty();
            List<Slide> candidates=new ArrayList<>();
            for(var slide:slides) if(slide.path().equals(path) && contains(slide.source().start(),slide.source().end(),offset,text.length()))candidates.add(slide);
            if(candidates.isEmpty()) for(var slide:slides) for(var site:slide.sites()) if(site.path().equals(path) && contains(site.start(),site.end(),offset,text.length())) {candidates.add(slide);break;}
            for(var candidate:candidates)if(candidate.number()==preferred)return Optional.of(candidate);
            return candidates.stream().findFirst();
        }
        public Optional<MappedDiagnostic> locate(BuildDiagnostics.Diagnostic diagnostic) {
            Path reported;boolean relative;
            try {reported=Path.of(diagnostic.path());relative=!reported.isAbsolute();if(relative)reported=entry.getParent().resolve(reported);reported=normalize(reported);}
            catch(RuntimeException ignored) {return Optional.empty();}
            Slide target=byNumber(diagnostic.slide()).orElse(null);
            if(target!=null && !target.path().equals(reported) && !(relative && Path.of(diagnostic.path()).getNameCount()==1 && target.path().getFileName().equals(reported.getFileName())))return Optional.empty();
            if(target==null || !reliable())return Optional.empty();
            String text=sources.get(target.path());
            var location=BuildDiagnostics.locate(text,target.source().contentStart(),target.source().end(),diagnostic.line(),diagnostic.column());
            Path finalPath=target.path();return location.map(value -> new MappedDiagnostic(finalPath,value));
        }
    }
    public record MappedDiagnostic(Path path,BuildDiagnostics.Location location) {}
    private static boolean contains(int start,int end,int offset,int length) {return offset>=start && (offset<end || (offset==end && end==length));}
    public static Path normalize(Path path) {return path.toAbsolutePath().normalize();}
    private ImportedDeck() {}
}
