package dev.slidev.intellij.core;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** Bounded discovery of conventional local files. No package execution or installation. */
public final class ProjectCompletions {
    public record Catalog(Map<String,String> layouts,Map<String,String> components) {}
    public record Roots(Path entryDirectory,Path project,Path theme,Path client) {}
    public static Roots roots(Path entry,String source,Runnable check) {
        Path directory=entry.toAbsolutePath().normalize().getParent();
        Path project=directory;
        for(Path candidate=directory;candidate!=null;candidate=candidate.getParent()) {
            check.run();
            if(Files.isRegularFile(candidate.resolve("package.json"))) {project=candidate;break;}
        }
        Path client=null;
        for(Path candidate=project;candidate!=null && client==null;candidate=candidate.getParent()) {
            check.run();
            Path direct=candidate.resolve("node_modules/@slidev/client");
            if(Files.isDirectory(direct)) {client=direct;break;}
            Path cli=candidate.resolve("node_modules/@slidev/cli/bin/slidev.mjs");
            if(Files.isRegularFile(cli)) try {
                for(Path real=cli.toRealPath().getParent();real!=null;real=real.getParent()) {
                    check.run();Path nested=real.resolve("node_modules/@slidev/client");
                    if(Files.isDirectory(nested)) {client=nested;break;}
                }
            } catch(IOException | SecurityException ignored) {}
        }
        return new Roots(directory,project,resolveTheme(project,theme(source),check),client);
    }
    public static Catalog discover(Path entry,String source,Runnable checkCanceled) {
        Map<String,String> layouts=new TreeMap<>(), components=new TreeMap<>();
        Path directory=entry.toAbsolutePath().normalize().getParent();
        if(directory==null) return new Catalog(Map.of(),Map.of());
        Path root=directory;
        for(Path candidate=directory;candidate!=null;candidate=candidate.getParent()) {
            checkCanceled.run();
            if(Files.isRegularFile(candidate.resolve("package.json"))) { root=candidate;break; }
        }
        // Project paths win over theme names. Preserve provenance for the completion popup.
        collect(directory,"project",layouts,components,checkCanceled);
        if(!root.equals(directory)) collect(root,"project",layouts,components,checkCanceled);
        Path theme=resolveTheme(root,theme(source),checkCanceled);
        if(theme!=null) collect(theme,"theme",layouts,components,checkCanceled);
        return new Catalog(Collections.unmodifiableMap(layouts),Collections.unmodifiableMap(components));
    }
    private static void collect(Path root,String origin,Map<String,String> layouts,Map<String,String> components,Runnable check) {
        scan(root.resolve("layouts"),false,origin,layouts,check);
        scan(root.resolve("components"),true,origin,components,check);
    }
    private static void scan(Path directory,boolean component,String origin,Map<String,String> names,Runnable check) {
        if(!Files.isDirectory(directory)) return;
        try(var paths=Files.walk(directory,component?5:1)) {
            var iterator=paths.iterator();int examined=0;
            while(iterator.hasNext() && examined++<1000) {
                check.run();Path path=iterator.next();
                String file=path.getFileName().toString();
                if(!Files.isRegularFile(path) || !file.endsWith(".vue") || file.startsWith(".")) continue;
                String name=file.substring(0,file.length()-4);
                if(component) name=pascal(name);
                if(name.matches("[A-Za-z][\\w-]*")) names.putIfAbsent(name,origin+": "+directory.relativize(path).toString().replace('\\','/'));
            }
        } catch(IOException | java.io.UncheckedIOException | SecurityException ignored) { /* An unavailable directory must not break completion. */ }
    }
    static String pascal(String value) {
        StringBuilder result=new StringBuilder();boolean upper=true;
        for(char ch:value.toCharArray()) {
            if(ch=='-' || ch=='_' || ch=='.') {upper=true;continue;}
            result.append(upper?Character.toUpperCase(ch):ch);upper=false;
        }
        return result.toString();
    }
    public static String theme(String source) {
        for(var block:MetadataRegions.scan(source).blocks()) if(block.head()) {
            var match=Pattern.compile("(?m)^theme[ \\t]*:[ \\t]*(?:'([^'\\r\\n]*)'|\"([^\"\\r\\n]*)\"|([^#\\r\\n]+))").matcher(source.substring(block.start(),block.end()));
            if(match.find()) return (match.group(1)!=null?match.group(1):match.group(2)!=null?match.group(2):match.group(3)).strip();
        }
        return "default";
    }
    private static Path resolveTheme(Path root,String name,Runnable check) {
        if(name.isEmpty() || name.equals("none")) return null;
        if(name.startsWith(".") || name.startsWith("/") || name.matches("^[A-Za-z]:.*")) {
            try { Path local=root.resolve(name).normalize();return Files.isDirectory(local)?local:null; }
            catch(InvalidPathException ignored) { return null; }
        }
        if(name.contains("..") || name.contains("\\") || !name.matches("@?[A-Za-z0-9_./-]+")) return null;
        List<String> packages=name.startsWith("@") || name.startsWith("slidev-theme-")?List.of(name):List.of("@slidev/theme-"+name,"slidev-theme-"+name,name);
        for(Path directory=root;directory!=null;directory=directory.getParent()) for(String pkg:packages) {
            check.run();Path candidate=directory.resolve("node_modules").resolve(pkg);
            if(Files.isDirectory(candidate)) return candidate;
        }
        return null;
    }
    private ProjectCompletions() {}
}
