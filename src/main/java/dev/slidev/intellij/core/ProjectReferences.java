package dev.slidev.intellij.core;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** Static local references only. Unknown aliases and dynamic expressions are intentionally unresolved. */
public final class ProjectReferences {
    public enum Kind { IMPORT, IMAGE, LAYOUT }
    public record Reference(int start,int end,String value,Kind kind) {}
    public record Resolution(Reference reference,Path target,String warning) {}
    private static final Pattern META=Pattern.compile("(?m)^([ ]*)(src|image|background|layout)[ \\t]*:[ \\t]*([^\\r\\n]*)");
    private static final Set<String> BUILTINS=new HashSet<>(SlidevSchema.find("layout",false).values());
    static { BUILTINS.add("fact");BUILTINS.add("full"); }
    private static final Pattern TAG=Pattern.compile("(?is)<(img|image|SlidevVideo)\\b(?:[^\"'<>]|\"[^\"]*\"|'[^']*')*>");
    private static final Pattern ATTRIBUTE=Pattern.compile("([:@A-Za-z_][\\w:.-]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')");
    public static List<Reference> references(String source) {
        List<Reference> references=new ArrayList<>();
        for(var block:MetadataRegions.scan(source).blocks()) {
            var matcher=META.matcher(source.substring(block.start(),block.end()));
            while(matcher.find() && references.size()<1000) {
                if(!matcher.group(1).isEmpty()) {
                    int keyOffset=block.start()+matcher.start(2)+matcher.group(2).length();
                    var context=MetadataRegions.completion(source,keyOffset);
                    if(context==null || !(block.head() && context.parent().equals("defaults"))) continue;
                }
                String field=matcher.group(2);String raw=matcher.group(3);
                int base=block.start()+matcher.start(3);
                Scalar scalar=scalar(raw,base);
                if(scalar==null) continue;
                Kind kind=field.equals("src")?Kind.IMPORT:field.equals("layout")?Kind.LAYOUT:Kind.IMAGE;
                if(field.equals("background")) {
                    var url=Pattern.compile("url\\(\\s*(['\"]?)(.*?)\\1\\s*\\)",Pattern.CASE_INSENSITIVE).matcher(scalar.value());
                    if(url.matches()) scalar=scalar.end()-scalar.start()==scalar.value().length()?
                        new Scalar(scalar.start()+url.start(2),scalar.start()+url.end(2),url.group(2)):
                        new Scalar(scalar.start(),scalar.end(),url.group(2));
                    else if(!looksLikeImage(scalar.value())) continue;
                }
                references.add(new Reference(scalar.start(),scalar.end(),scalar.value(),kind));
            }
        }
        String body=MarkupCompletion.referenceText(source);
        // Markdown image syntax inside an HTML attribute is plain attribute text.
        String markdown=Pattern.compile("(?s)</?[A-Za-z][\\w-]*\\b(?:[^\"'<>]|\"[^\"]*\"|'[^']*')*>").matcher(body)
            .replaceAll(match -> {
                int previous=match.start()-1;while(previous>=0 && Character.isWhitespace(body.charAt(previous)))previous--;
                return previous>=0 && body.charAt(previous)=='('?match.group():" ".repeat(match.group().length());
            });
        for(int i=0;i<markdown.length()-2 && references.size()<1000;i++) {
            if(markdown.charAt(i)!='!' || markdown.charAt(i+1)!='[' || escaped(markdown,i)) continue;
            int labelEnd=closing(markdown,i+1,'[',']');
            if(labelEnd<0 || labelEnd+1>=markdown.length() || markdown.charAt(labelEnd+1)!='(') continue;
            int begin=labelEnd+2;while(begin<markdown.length() && Character.isWhitespace(markdown.charAt(begin)))begin++;
            int end=begin;
            boolean angle=begin<markdown.length() && markdown.charAt(begin)=='<';
            if(angle) {
                begin++;end=markdown.indexOf('>',begin);if(end<0)continue;
            } else {
                int depth=0;
                while(end<markdown.length()) {
                    char ch=markdown.charAt(end);
                    if(escaped(markdown,end)) {end++;continue;}
                    if(ch=='(')depth++;
                    else if(ch==')') {if(depth==0)break;depth--;}
                    if(Character.isWhitespace(ch) && depth==0)break;
                    end++;
                }
            }
            if(end>begin && end<markdown.length()) {
                if(!destinationClosed(markdown,end,angle))continue;
                String value=markdown.substring(begin,end).replaceAll("\\\\([\\\\() !#])","$1");
                value=entities(value);if(value==null)continue;
                references.add(new Reference(begin,end,value,Kind.IMAGE));
                i=end;
            }
        }
        var tags=TAG.matcher(body);
        while(tags.find() && references.size()<1000) {
            var attrs=ATTRIBUTE.matcher(tags.group());
            while(attrs.find() && references.size()<1000) {
                String tag=tags.group(1),name=attrs.group(1);
                boolean image=(tag.equalsIgnoreCase("img") && name.equals("src")) ||
                    (tag.equalsIgnoreCase("image") && (name.equals("href") || name.equals("xlink:href"))) ||
                    (tag.equalsIgnoreCase("SlidevVideo") && name.equals("poster"));
                if(!image)continue;
                int group=attrs.group(2)!=null?2:3;
                String value=entities(attrs.group(group));
                if(value!=null) references.add(new Reference(tags.start()+attrs.start(group),tags.start()+attrs.end(group),value,Kind.IMAGE));
            }
        }
        return references.stream().sorted(Comparator.comparingInt(Reference::start)).toList();
    }
    public static List<Resolution> analyze(Path entry,String source,Runnable check) {
        List<Reference> references=references(source);
        if(references.isEmpty())return List.of();
        var roots=ProjectCompletions.roots(entry,source,check);
        List<Resolution> result=new ArrayList<>();
        for(var reference:references) {check.run();result.add(resolve(roots,reference));}
        return List.copyOf(result);
    }
    public static Resolution at(Path entry,String source,int offset,Runnable check) {
        for(var reference:references(source)) if(reference.start()<=offset && offset<reference.end()) {
            return resolve(ProjectCompletions.roots(entry,source,check),reference);
        }
        return null;
    }
    private static Resolution resolve(ProjectCompletions.Roots roots,Reference reference) {
        String value=reference.kind()==Kind.LAYOUT?reference.value().strip():reference.value();
        if(value.isEmpty() || value.startsWith("#") || value.startsWith("//") || value.startsWith("@") || value.startsWith("~") ||
            value.startsWith("/@") || value.startsWith("/node_modules/") || value.contains("{{") || value.contains("${") ||
            value.matches("^[A-Za-z][A-Za-z0-9+.-]*:.*")) return new Resolution(reference,null,null);
        try {
            if(reference.kind()==Kind.LAYOUT) {
                if(!value.matches("[A-Za-z][\\w-]*")) return new Resolution(reference,null,null);
                for(Path directory:directories(roots)) {
                    Path layout=directory.resolve("layouts").resolve(value+".vue");
                    if(Files.isRegularFile(layout))return new Resolution(reference,layout,null);
                }
                return new Resolution(reference,null,BUILTINS.contains(value)?null:"Layout '"+value+"' not found locally. Addon and Vite layouts are not checked.");
            }
            int suffix=value.length();
            for(char marker:new char[]{'?','#'}) {int pos=value.indexOf(marker);if(pos>=0)suffix=Math.min(suffix,pos);}
            value=URLDecoder.decode(value.substring(0,suffix).replace("+","%2B"),StandardCharsets.UTF_8);
            if(value.isEmpty() || value.indexOf('\0')>=0 || value.contains("\n") || value.contains("\r"))return new Resolution(reference,null,null);
            if(reference.kind()==Kind.IMPORT) {
                Path target=roots.entryDirectory().resolve(value).normalize();
                return new Resolution(reference,Files.isRegularFile(target)?target:null,Files.isRegularFile(target)?null:"Imported Markdown file not found: "+reference.value());
            }
            if(value.startsWith("/")) {
                // A URL-root asset belongs to public/, not to the OS filesystem root.
                for(Path directory:directories(roots)) {
                    Path publicRoot=directory.resolve("public").normalize();
                    Path target=publicRoot.resolve(value.substring(1)).normalize();
                    if(target.startsWith(publicRoot) && Files.isRegularFile(target))return new Resolution(reference,target,null);
                }
            } else {
                Path target=roots.entryDirectory().resolve(value).normalize();
                if(Files.isRegularFile(target))return new Resolution(reference,target,null);
            }
            return new Resolution(reference,null,"Local image not found: "+reference.value());
        } catch(IllegalArgumentException | SecurityException ignored) {return new Resolution(reference,null,null);}
    }
    private static List<Path> directories(ProjectCompletions.Roots roots) {
        LinkedHashSet<Path> paths=new LinkedHashSet<>();
        paths.add(roots.entryDirectory());paths.add(roots.project());
        if(roots.theme()!=null)paths.add(roots.theme());if(roots.client()!=null)paths.add(roots.client());
        return List.copyOf(paths);
    }
    private record Scalar(int start,int end,String value) {}
    private static Scalar scalar(String raw,int start) {
        if(raw.isBlank())return null;
        int begin=0;while(begin<raw.length() && Character.isWhitespace(raw.charAt(begin)))begin++;
        char quote=raw.charAt(begin);
        if(quote=='\'' || quote=='"') {
            for(int end=begin+1;end<raw.length();end++) if(raw.charAt(end)==quote) {
                if(quote=='\'' && end+1<raw.length() && raw.charAt(end+1)=='\'') {end++;continue;}
                if(quote=='"' && escaped(raw,end))continue;
                String value=raw.substring(begin+1,end);
                // Escaped double-quoted YAML paths need a complete YAML decoder; avoid guessed targets.
                if(quote=='"' && value.contains("\\"))return null;
                return new Scalar(start+begin+1,start+end,quote=='\''?value.replace("''","'"):value);
            }
            return null;
        }
        int end=raw.length();
        for(int i=begin;i<end;i++) if(raw.charAt(i)=='#' && (i==begin || Character.isWhitespace(raw.charAt(i-1)))) {end=i;break;}
        while(end>begin && Character.isWhitespace(raw.charAt(end-1)))end--;
        if(end==begin)return null;
        String value=raw.substring(begin,end);
        if("|>!&*[{".indexOf(value.charAt(0))>=0 || Set.of("null","true","false","~").contains(value))return null;
        return new Scalar(start+begin,start+end,value);
    }
    private static boolean looksLikeImage(String value) {
        String lower=value.toLowerCase(Locale.ROOT);
        if(lower.contains("gradient(") || lower.startsWith("rgb(") || lower.startsWith("rgba(") || lower.startsWith("hsl(") || lower.startsWith("var("))return false;
        return value.startsWith(".") || value.startsWith("/") || value.matches("(?i).*\\.(png|jpe?g|svg|webp|avif|gif|ico|bmp|tiff?)([?#].*)?");
    }
    private static int closing(String text,int start,char open,char close) {
        int depth=0;
        for(int i=start;i<text.length() && i<start+10000;i++) {
            if(escaped(text,i))continue;
            if(text.charAt(i)==open)depth++;
            if(text.charAt(i)==close && --depth==0)return i;
        }
        return -1;
    }
    private static boolean destinationClosed(String text,int end,boolean angle) {
        int cursor=end+(angle?1:0);
        while(cursor<text.length() && Character.isWhitespace(text.charAt(cursor)))cursor++;
        if(cursor>=text.length())return false;
        if(text.charAt(cursor)==')')return true;
        char quote=text.charAt(cursor++);
        if(quote!='\'' && quote!='"')return false;
        while(cursor<text.length() && (text.charAt(cursor)!=quote || escaped(text,cursor)))cursor++;
        if(cursor>=text.length())return false;
        cursor++;while(cursor<text.length() && Character.isWhitespace(text.charAt(cursor)))cursor++;
        return cursor<text.length() && text.charAt(cursor)==')';
    }
    private static String entities(String text) {
        var matcher=Pattern.compile("&(#x[0-9a-fA-F]+|#[0-9]+|[A-Za-z]+);").matcher(text);
        StringBuilder result=new StringBuilder();
        while(matcher.find()) {
            String token=matcher.group(1),replacement;
            replacement=switch(token) {case "amp" -> "&";case "quot" -> "\"";case "apos" -> "'";case "lt" -> "<";case "gt" -> ">";default -> null;};
            if(replacement==null && token.startsWith("#")) try {
                int code=Integer.parseInt(token.substring(token.startsWith("#x")?2:1),token.startsWith("#x")?16:10);
                replacement=new String(Character.toChars(code));
            } catch(IllegalArgumentException ignored) {return null;}
            if(replacement==null)return null;
            matcher.appendReplacement(result,java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);return result.toString();
    }
    private static boolean escaped(String text,int offset) {
        int count=0;while(offset>0 && text.charAt(--offset)=='\\')count++;
        return count%2==1;
    }
    private ProjectReferences() {}
}
