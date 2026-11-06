package dev.slidev.intellij.core;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Bounded local Vue snapshots, with coordinates normalized to IntelliJ document text. */
public final class DiagnosticSources {
    public record Source(Path path,String text) {
        public boolean matches(String document){return text.equals(document);}
        public Optional<BuildDiagnostics.Location> position(int line,int column) {
            if(line<1 || column<1)return Optional.empty();int start=0;
            for(int row=1;row<line;row++){int end=text.indexOf('\n',start);if(end<0)return Optional.empty();start=end+1;}
            int end=text.indexOf('\n',start);if(end<0)end=text.length();
            if(column-1>end-start)return Optional.empty();
            return Optional.of(new BuildDiagnostics.Location(start+column-1,line,column));
        }
        public String line(int number) {
            var point=position(number,1);if(point.isEmpty())return null;
            int start=point.get().offset(),end=text.indexOf('\n',start);return text.substring(start,end<0?text.length():end);
        }
    }
    public static Optional<Path> vuePath(Path entry,BuildDiagnostics.Diagnostic diagnostic) {
        if(diagnostic.slide()!=0 || diagnostic.path().isBlank())return Optional.empty();
        String raw=diagnostic.path();int query=raw.indexOf('?');if(query>=0)raw=raw.substring(0,query);
        try {
            Path path;
            if(raw.startsWith("file:"))path=Path.of(URI.create(raw));
            else {
                if(raw.startsWith("/@fs/"))raw=raw.substring(4);
                if(raw.startsWith("/@id/"))raw=raw.substring(5);
                if(raw.matches("/[A-Za-z]:/.*"))raw=raw.substring(1);
                if(raw.contains("\0") || raw.contains("://"))return Optional.empty();
                path=Path.of(raw);if(!path.isAbsolute())path=entry.getParent().resolve(path);
            }
            if(!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".vue"))return Optional.empty();
            return Optional.of(ImportedDeck.normalize(path));
        } catch(RuntimeException ignored){return Optional.empty();}
    }
    public static Optional<Source> read(Path path) {
        try {
            if(!Files.isRegularFile(path))return Optional.empty();
            var before=Files.readAttributes(path,java.nio.file.attribute.BasicFileAttributes.class);
            if(before.size()>2_000_000)return Optional.empty();
            byte[] bytes;try(var input=Files.newInputStream(path)){bytes=input.readNBytes(2_000_001);}
            if(bytes.length>2_000_000)return Optional.empty();
            String text=StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            var after=Files.readAttributes(path,java.nio.file.attribute.BasicFileAttributes.class);
            if(before.size()!=after.size() || !before.lastModifiedTime().equals(after.lastModifiedTime()) || text.length()>1_000_000)return Optional.empty();
            text=text.replace("\r\n","\n").replace('\r','\n');if(text.startsWith("\ufeff"))text=text.substring(1);
            return Optional.of(new Source(ImportedDeck.normalize(path),text));
        } catch(IOException | SecurityException ignored){return Optional.empty();}
    }
    public static ExpressionChecker.Result analyze(Source source,Path entry,String node,Runnable canceled) {
        try(var input=DiagnosticSources.class.getResourceAsStream("/dev/slidev/intellij/check-vue-source.cjs")) {
            if(input==null)return new ExpressionChecker.Result(List.of(),"Vue compiler helper unavailable");
            return ExpressionChecker.check(entry,source.text(),node,new String(input.readAllBytes(),StandardCharsets.UTF_8),canceled);
        } catch(IOException ex){return new ExpressionChecker.Result(List.of(),ex.toString());}
    }
    public static Optional<BuildDiagnostics.Location> compilerPosition(Source source,BuildDiagnostics.Diagnostic reported,ExpressionChecker.Result checked) {
        if(checked==null || checked.unavailable()!=null)return Optional.empty();
        String message=messageKey(reported.message());if(message.isBlank())return Optional.empty();
        var matches=checked.diagnostics().stream().filter(d->messageKey(d.message()).equals(message)).toList();
        if(matches.size()!=1)return Optional.empty();int offset=matches.get(0).start();
        if(offset<0 || offset>source.text().length())return Optional.empty();
        int line=1,start=0;for(int i=0;i<offset;i++)if(source.text().charAt(i)=='\n'){line++;start=i+1;}
        return Optional.of(new BuildDiagnostics.Location(offset,line,offset-start+1));
    }
    private static String messageKey(String message) {
        String first=BuildDiagnostics.clean(message).lines().filter(line->!line.isBlank()).findFirst().orElse("").strip();
        return first.replaceFirst("^\\[vue/[^]]+\\]\\s*", "").replaceFirst("^.*?\\.vue:\\s*", "")
            .replaceAll("\\(\\d+:\\d+\\)", "").replaceAll("\\s+", " ").strip();
    }
    private DiagnosticSources(){}
}
