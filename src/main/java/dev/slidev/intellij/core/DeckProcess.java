package dev.slidev.intellij.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class DeckProcess {
    public static ImportedDeck.Snapshot load(Path entry,Map<Path,String> overrides,String node,String script,Runnable canceled) throws IOException {
        Path input=Files.createTempFile("slidev-deck-", ".txt"),output=null;Process child=null;
        try {
            output=Files.createTempFile("slidev-deck-", ".log");StringBuilder request=new StringBuilder();
            for(var source:overrides.entrySet()) {canceled.run();request.append(encode(source.getKey().toString())).append('\t').append(encode(source.getValue())).append('\n');}
            if(request.length()>16_000_000)throw new IOException("Unsaved deck inputs exceed 16 MB");
            Files.writeString(input,request,StandardCharsets.UTF_8);
            child=new ProcessBuilder(node,"--max-old-space-size=192","-e",script,entry.toAbsolutePath().toString())
                .directory(entry.toAbsolutePath().getParent().toFile()).redirectInput(input.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(!child.waitFor(50,TimeUnit.MILLISECONDS)) {canceled.run();if(System.nanoTime()>deadline)throw new IOException("Deck resolution timed out");if(Files.size(output)>24_000_000)throw new IOException("Deck response exceeds 24 MB");}
            canceled.run();
            if(child.exitValue()!=0 || Files.size(output)>24_000_000)throw new IOException("Deck parser process failed or exceeded its output limit");
            return parse(entry,Files.readString(output,StandardCharsets.UTF_8));
        } catch(InterruptedException ex) {Thread.currentThread().interrupt();throw new IOException("Deck resolution interrupted",ex);}
        finally {
            if(child!=null && child.isAlive()) {child.destroyForcibly();try{child.waitFor(1,TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}}
            for(Path file:new Path[]{input,output})if(file!=null)try{Files.deleteIfExists(file);}catch(IOException ignored){}
        }
    }
    public static ImportedDeck.Snapshot parse(Path entry,String response) throws IOException {
        Map<Path,String> sources=new LinkedHashMap<>();List<ImportedDeck.Slide> slides=new ArrayList<>();Set<Path> watched=new LinkedHashSet<>();List<ImportedDeck.Issue> issues=new ArrayList<>();boolean ready=false;
        try {
            for(String line:response.split("\n")) {
                if(!line.startsWith("slidev-deck\t"))continue;
                String[] f=line.substring(12).split("\t",-1);
                switch(f[0]) {
                    case "ready" -> ready=true;
                    case "unavailable" -> throw new IOException(decode(f[1]));
                    case "source" -> {if(sources.size()>=256)throw new IOException("Too many deck sources");sources.put(ImportedDeck.normalize(Path.of(decode(f[1]))),decode(f[2]));}
                    case "watch" -> watched.add(ImportedDeck.normalize(Path.of(decode(f[1]))));
                    case "issue" -> issues.add(new ImportedDeck.Issue(ImportedDeck.normalize(Path.of(decode(f[1]))),Integer.parseInt(f[2]),decode(f[3])));
                    case "slide" -> {
                        if(slides.size()>=10000)throw new IOException("Too many deck slides");
                        Path path=ImportedDeck.normalize(Path.of(decode(f[1])));int local=Integer.parseInt(f[2]),start=Integer.parseInt(f[3]),content=Integer.parseInt(f[4]),end=Integer.parseInt(f[5]);
                        String text=sources.get(path);if(text==null || local<1 || start<0 || content<start || end<content || end>text.length())throw new IOException("Invalid slide source range");
                        List<ImportedDeck.Site> sites=new ArrayList<>();
                        for(String raw:decode(f[7]).split("\n"))if(!raw.isEmpty()) {
                            String[] s=raw.split("\t");Path importer=ImportedDeck.normalize(Path.of(decode(s[0])));int number=Integer.parseInt(s[1]),begin=Integer.parseInt(s[2]),finish=Integer.parseInt(s[3]);
                            if(!sources.containsKey(importer) || number<1 || begin<0 || finish<begin || finish>sources.get(importer).length())throw new IOException("Invalid importer range");
                            sites.add(new ImportedDeck.Site(importer,number,begin,finish));
                        }
                        slides.add(new ImportedDeck.Slide(slides.size()+1,path,new DeckParser.Slide(local,start,end,decode(f[6]),null,content),List.copyOf(sites)));
                    }
                    default -> throw new IOException("Unknown deck response record");
                }
            }
        } catch(IllegalArgumentException | IndexOutOfBoundsException ex) {throw new IOException("Malformed deck parser response",ex);}
        entry=ImportedDeck.normalize(entry);if(!ready || !sources.containsKey(entry))throw new IOException("Deck parser did not return its entry source");
        return new ImportedDeck.Snapshot(entry,List.copyOf(slides),Collections.unmodifiableMap(sources),Set.copyOf(watched),List.copyOf(issues));
    }
    private static String encode(String text) {return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));}
    private static String decode(String text) {return new String(Base64.getDecoder().decode(text),StandardCharsets.UTF_8);}
    private DeckProcess() {}
}
