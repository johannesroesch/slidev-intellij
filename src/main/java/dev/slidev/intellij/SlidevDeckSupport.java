package dev.slidev.intellij;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.*;
import dev.slidev.intellij.core.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

final class SlidevDeckSupport {
    static VirtualFile entryFor(Project project,VirtualFile file) {
        var settings=SlidevSettings.get(project).getState();
        if(settings.entryFiles.contains(file.getUrl()))return file;
        String associated=settings.importedEntries.get(file.getPath());
        if(associated!=null) {var root=LocalFileSystem.getInstance().findFileByPath(associated);if(root!=null && root.isValid())return root;}
        return file;
    }
    static Map<Path,String> overrides(Project project,Path entry,Set<Path> watched) {
        return ReadAction.compute(() -> {
            Map<Path,String> result=new LinkedHashMap<>();var manager=FileDocumentManager.getInstance();
            Path base=project.getBasePath()==null?entry.getParent():ImportedDeck.normalize(Path.of(project.getBasePath()));
            for(var document:manager.getUnsavedDocuments()) {
                var file=manager.getFile(document);if(file==null || !file.isInLocalFileSystem() || !SlidevDetection.isMarkdown(file))continue;
                Path path=ImportedDeck.normalize(Path.of(file.getPath()));
                if(path.startsWith(base) || path.startsWith(entry.getParent()) || watched.contains(path))result.put(path,document.getText());
            }
            return result;
        });
    }
    static ImportedDeck.Snapshot load(Path entry,Map<Path,String> overrides,String node,Runnable canceled) {
        try(var resource=SlidevDeckSupport.class.getResourceAsStream("/dev/slidev/intellij/load-deck.cjs")) {
            if(resource==null)throw new IOException("Deck parser resource missing");
            var deck=DeckProcess.load(entry,overrides,node,new String(resource.readAllBytes(),StandardCharsets.UTF_8),canceled);
            for(Path path:deck.sources().keySet()){canceled.run();LocalFileSystem.getInstance().refreshAndFindFileByPath(path.toString().replace('\\','/'));}
            return deck;
        } catch(IOException ex) {
            String text=overrides.get(entry);if(text==null)try{text=Files.readString(entry);}catch(IOException ignored){text="";}
            List<ImportedDeck.Slide> slides=new ArrayList<>();for(var slide:DeckParser.parse(text).slides())slides.add(new ImportedDeck.Slide(slide.number(),entry,slide,List.of()));
            return new ImportedDeck.Snapshot(entry,List.copyOf(slides),Map.of(entry,text),Set.of(entry),List.of(new ImportedDeck.Issue(entry,0,"Deck mapping unavailable: "+ex.getMessage())));
        }
    }
    static boolean matches(ImportedDeck.Snapshot deck) {
        var manager=FileDocumentManager.getInstance();
        for(var source:deck.sources().entrySet()) {var file=LocalFileSystem.getInstance().findFileByPath(source.getKey().toString().replace('\\','/'));if(file==null)continue;var document=manager.getCachedDocument(file);if(document!=null && !document.getText().equals(source.getValue()))return false;}
        return true;
    }
    static void remember(Project project,ImportedDeck.Snapshot deck) {
        var associations=SlidevSettings.get(project).getState().importedEntries;String entry=deck.entry().toString().replace('\\','/');
        var before=new java.util.HashMap<>(associations);
        if(deck.reliable())associations.entrySet().removeIf(item -> item.getValue().equals(entry) && !deck.sources().containsKey(ImportedDeck.normalize(Path.of(item.getKey()))));
        for(Path source:deck.sources().keySet())if(!source.equals(deck.entry()))associations.put(source.toString().replace('\\','/'),entry);
        if(!before.equals(associations))com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.getInstance(project).restart();
    }
    private SlidevDeckSupport() {}
}
