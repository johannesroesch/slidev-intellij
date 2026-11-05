package dev.slidev.intellij.core;

import java.util.*;
import java.util.regex.Pattern;

/** Recovery ranges for editor assistance, including unfinished frontmatter. UTF-16 offsets. */
public final class MetadataRegions {
    public record Block(int marker, int start, int end, boolean head, boolean closed) {}
    public record Problem(int start, int end, String message) {}
    public record Scan(List<Block> blocks, List<Problem> problems) {}
    public record Completion(Block block, String key, String prefix, int prefixStart) {}
    private static final Pattern KEY=Pattern.compile("(?:[\\w.-]+|'[^']+'|\"[^\"]+\")\\s*:.*");
    private record Line(String text, int start, int end) {}

    public static Scan scan(String source) {
        List<Line> lines=new ArrayList<>();
        int pos=0;
        for(String part:source.split("\n",-1)) {
            lines.add(new Line(part.replace("\r", ""),pos,Math.min(source.length(),pos+part.length()+1)));
            pos+=part.length()+1;
        }
        List<Block> blocks=new ArrayList<>(); List<Problem> problems=new ArrayList<>();
        char fence=0; int length=0, fenceStart=0; boolean comment=false;
        for(int i=0;i<lines.size();i++) {
            Line line=lines.get(i); String text=line.text().stripLeading();
            if(comment) { if(text.contains("-->")) comment=false; continue; }
            if(fence!=0) {
                if(text.matches(Pattern.quote(""+fence)+"{"+length+",}\\s*")) fence=0;
                continue;
            }
            if(text.startsWith("<!--")) { comment=!text.substring(4).contains("-->"); continue; }
            if(line.text().startsWith("    ") || line.text().startsWith("\t")) continue;
            if(text.startsWith("```") || text.startsWith("~~~")) {
                fence=text.charAt(0);length=0;fenceStart=line.start();
                while(length<text.length() && text.charAt(length)==fence) length++;
                continue;
            }
            if(!line.text().replace("\ufeff", "").strip().equals("---")) continue;
            boolean head=i==0;
            int close=i+1;
            while(close<lines.size() && !lines.get(close).text().strip().equals("---")) close++;
            int first=i+1;
            while(first<close && (lines.get(first).text().isBlank() || lines.get(first).text().stripLeading().startsWith("#"))) first++;
            boolean candidate=first<close && looksLikeMetadata(lines.get(first).text(),head);
            // Empty initial headmatter is useful while typing. Empty later slides stay slides.
            if(!candidate && head) candidate=first==close;
            if(!candidate) continue;
            boolean closed=close<lines.size();
            int end=closed ? lines.get(close).start() : source.length();
            blocks.add(new Block(line.start(),line.end(),end,head,closed));
            if(!closed) problems.add(new Problem(line.start(),Math.min(source.length(),line.start()+3),"Frontmatter is missing its closing ---"));
            i=closed ? close : lines.size();
        }
        if(fence!=0) problems.add(new Problem(fenceStart,Math.min(source.length(),fenceStart+length),"Code fence is not closed"));
        return new Scan(List.copyOf(blocks),List.copyOf(problems));
    }
    private static boolean looksLikeMetadata(String line, boolean head) {
        if(!line.equals(line.stripLeading())) return false;
        if(KEY.matcher(line).matches()) return true;
        // Recover a missing colon or a partially typed known key, without treating prose as YAML.
        String key=line.strip().split("\\s+",2)[0];
        return !key.isEmpty() && SlidevSchema.fields(head).stream().anyMatch(f -> f.name().equals(key) || (line.strip().equals(key) && f.name().startsWith(key)));
    }
    public static Completion completion(String source, int offset) {
        if(offset<0 || offset>source.length()) return null;
        for(Block block:scan(source).blocks()) {
            if(offset<block.start() || offset>block.end() || (block.closed() && offset==block.end())) continue;
            int lineStart=Math.max(block.start(),source.lastIndexOf('\n',Math.max(0,offset-1))+1);
            String before=source.substring(lineStart,offset);
            var key=Pattern.compile("([A-Za-z_][\\w-]*)?").matcher(before);
            if(key.matches()) return new Completion(block,null,before,lineStart);
            var value=Pattern.compile("([A-Za-z_][\\w-]*)\\s*:\\s*([\\w-]*)").matcher(before);
            if(value.matches()) return new Completion(block,value.group(1),value.group(2),offset-value.group(2).length());
        }
        return null;
    }
    private MetadataRegions() {}
}
