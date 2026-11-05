package dev.slidev.intellij.core;

import java.util.*;
import java.util.regex.Pattern;

/** Lexical completion contexts only. Does not claim Vue expression/type analysis. */
public final class MarkupCompletion {
    public enum Kind { TAG, CLOSE_TAG, ATTRIBUTE }
    public record Context(Kind kind,String prefix,String tag,Set<String> attributes) {}
    public record Attribute(String name,boolean value,String description) {}
    public record Component(String name,String description,List<Attribute> attributes) {}
    public record Edit(String text,int caret) {}
    private static Attribute attr(String name,String description) { return new Attribute(name,true,description); }
    private static Attribute flag(String name,String description) { return new Attribute(name,false,description); }
    public static final List<Attribute> DIRECTIVES=List.of(
        flag("v-click","Reveal on the next click"), flag("v-click.hide","Hide on click"),
        flag("v-after","Reveal together with the previous click"), flag("v-after.hide","Hide together with the previous click"),
        flag("v-mark","Annotate with a rough marker"), flag("v-mark.circle","Circle the content"),
        flag("v-mark.underline","Underline the content"), flag("v-mark.highlight","Highlight the content"),
        attr("v-motion","Motion configuration expression"), attr("v-drag","Draggable element position"));
    private static Component component(String name,String description,Attribute... attrs) { return new Component(name,description,List.of(attrs)); }
    public static final List<Component> COMPONENTS=List.of(
        component("v-click","Reveal on click",attr("at","Click position"),flag("hide","Hide instead of reveal"),attr("tag","Rendered HTML tag")),
        component("v-after","Reveal with the previous click",flag("hide","Hide instead of reveal"),attr("tag","Rendered HTML tag")),
        component("v-clicks","Reveal child elements one by one",attr("depth","Nested list depth"),attr("every","Items per click"),attr("at","Starting click position"),attr("tag","Rendered HTML tag")),
        component("v-click-gap","Reserve click steps",attr("size","Number of click steps")),
        component("v-switch","Switch content by click step",attr("tag","Rendered HTML tag"),attr("childTag","Child HTML tag"),attr(":unmount","Unmount previous content")),
        component("v-drag","Draggable content",attr("pos","Element position")),
        component("SlidevVideo","Video playback",attr("src","Video URL"),flag("controls","Show playback controls"),flag("autoplay","Start playback automatically"),attr("poster","Poster image"),attr("autoreset","Reset on slide or click")),
        component("Toc","Table of contents",attr("columns","Column count"),attr("minDepth","Minimum heading depth"),attr("maxDepth","Maximum heading depth"),attr("mode","Display mode")),
        component("Link","Link to a slide",attr("to","Slide number or route alias"),attr("title","Link title")),
        component("Arrow","Draw an arrow",attr("x1","Start X"),attr("y1","Start Y"),attr("x2","End X"),attr("y2","End Y"),attr("color","Arrow color"),attr("width","Line width")),
        component("AutoFitText","Fit text into the available space",attr(":max","Maximum font size expression"),attr(":min","Minimum font size expression"),attr("modelValue","Text content")),
        component("LightOrDark","Content for light and dark themes"),
        component("RenderWhen","Render for selected display contexts",attr("context","Display context")),
        component("SlideCurrentNo","Current slide number"), component("SlidesTotal","Total number of slides"),
        component("Transform","Scale or transform content",attr("scale","Scale factor")),
        component("Youtube","Embed a YouTube video",attr("id","Video ID"),attr("width","Video width"),attr("height","Video height"))
    );
    public static List<Attribute> attributes(String tag) {
        Map<String,Attribute> attrs=new LinkedHashMap<>();
        DIRECTIVES.forEach(a -> attrs.put(a.name(),a));
        String normalized=tag.replace("-", "").toLowerCase(Locale.ROOT);
        COMPONENTS.stream().filter(c -> c.name().replace("-", "").toLowerCase(Locale.ROOT).equals(normalized)).findFirst().ifPresent(c -> c.attributes().forEach(a -> attrs.put(a.name(),a)));
        return List.copyOf(attrs.values());
    }
    public static Context context(String source,int caret) {
        if(caret<0 || caret>source.length()) return null;
        boolean[] masked=mask(source,false);
        if(caret>0 && masked[caret-1]) return null;
        int open=-1; char quote=0;
        for(int i=0;i<caret;i++) {
            if(masked[i]) continue;
            char ch=source.charAt(i);
            if(open>=0 && quote!=0) { if(ch==quote) quote=0; continue; }
            if(ch=='<' && !escaped(source,i)) open=i;
            else if(open>=0 && (ch=='\'' || ch=='"')) quote=ch;
            else if(ch=='>') open=-1;
        }
        if(open<0 || quote!=0) return null;
        String text=source.substring(open+1,caret);
        if(text.matches("/?[A-Za-z][\\w-]*") || text.isEmpty() || text.equals("/")) {
            boolean closing=text.startsWith("/");
            return new Context(closing?Kind.CLOSE_TAG:Kind.TAG,closing?text.substring(1):text,"",Set.of());
        }
        var match=Pattern.compile("([A-Za-z][\\w-]*)\\s+([\\s\\S]*)").matcher(text);
        if(!match.matches()) return null;
        String attrs=match.group(2); int start=attrs.length();
        while(start>0 && !Character.isWhitespace(attrs.charAt(start-1))) start--;
        String prefix=attrs.substring(start);
        if(!prefix.isEmpty() && !prefix.matches("[:@A-Za-z_][\\w:.-]*")) return null;
        Set<String> existing=new HashSet<>();
        // Values are consumed as one token, so directive-like text inside quotes is ignored.
        var names=Pattern.compile("([:@A-Za-z_][\\w:.-]*)(?:\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+))?").matcher(attrs.substring(0,start));
        while(names.find()) existing.add(names.group(1));
        return new Context(Kind.ATTRIBUTE,prefix,match.group(1),Set.copyOf(existing));
    }
    /** Insertion suffix after the completion engine has inserted the selected name. */
    public static Edit insertion(String after,Kind kind,String name,boolean value) {
        if(kind==Kind.TAG) {
            // Preserve tags whose attributes/closing delimiter are already present.
            if(!after.isEmpty() && (after.charAt(0)=='>' || after.charAt(0)=='/' || after.charAt(0)==' ' || after.charAt(0)=='\t')) return new Edit("",0);
            return new Edit("></"+name+">",1);
        }
        if(kind==Kind.CLOSE_TAG) return after.startsWith(">")?new Edit("",0):new Edit(">",1);
        if(value && !after.stripLeading().startsWith("=")) return new Edit("=\"\"",2);
        return new Edit("",0);
    }
    /** Blank Markdown-only regions while preserving UTF-16 offsets for Vue diagnostics. */
    public static String vueTemplate(String source) {
        return blank(source,mask(source,true));
    }
    public static String referenceText(String source) {return blank(source,mask(source,false));}
    private static String blank(String source,boolean[] masked) {
        char[] result=source.toCharArray();
        for(int i=0;i<result.length;i++) if(masked[i] && result[i]!='\n' && result[i]!='\r') result[i]=' ';
        return new String(result);
    }
    private static boolean[] mask(String source,boolean preserveExpressions) {
        boolean[] mask=new boolean[source.length()];
        for(var b:MetadataRegions.scan(source).blocks()) Arrays.fill(mask,b.marker(),Math.min(source.length(),b.closed()?b.end()+3:b.end()),true);
        boolean inTag=false,closingTag=false;char attributeQuote=0;int htmlDepth=0;
        String tagName="";
        for(int i=0;i<source.length();i++) {
            if(mask[i]) continue;
            if(preserveExpressions) {
                char ch=source.charAt(i);
                if(attributeQuote!=0) { if(ch==attributeQuote) attributeQuote=0;continue; }
                if(inTag && (ch=='\'' || ch=='"')) { attributeQuote=ch;continue; }
                if(ch=='<' && !escaped(source,i)) {
                    var tag=Pattern.compile("</?([A-Za-z][\\w-]*)").matcher(source);tag.region(i,source.length());
                    if(tag.lookingAt()) {inTag=true;closingTag=source.startsWith("</",i);tagName=tag.group(1).toLowerCase(Locale.ROOT);}
                }
                if(ch=='>' && inTag) {
                    if(closingTag) htmlDepth=Math.max(0,htmlDepth-1);
                    else if((i==0 || source.charAt(i-1)!='/') && !Set.of("img","br","hr","input","source","meta","link","area","base","col","embed","param","track","wbr").contains(tagName)) htmlDepth++;
                    inTag=false;
                }
            }
            if(i==0 || source.charAt(i-1)=='\n') {
                int lineEnd=source.indexOf('\n',i);if(lineEnd<0)lineEnd=source.length();
                String line=source.substring(i,lineEnd).replace("\r", "");
                var marker=Pattern.compile(" {0,3}(`{3,}|~{3,})(.*)").matcher(unquote(line));
                if(marker.matches()) {
                    char fence=marker.group(1).charAt(0);int length=marker.group(1).length();
                    int end=source.length();
                    for(int next=lineEnd+1;next<source.length();) {
                        int nextEnd=source.indexOf('\n',next);if(nextEnd<0)nextEnd=source.length();
                        if(unquote(source.substring(next,nextEnd).replace("\r", "")).matches(" {0,3}"+Pattern.quote(""+fence)+"{"+length+",}\\s*")) { end=nextEnd;break; }
                        next=nextEnd+1;
                    }
                    Arrays.fill(mask,i,end,true);i=end-1;continue;
                }
                if((line.startsWith("    ") || line.startsWith("\t")) && !(preserveExpressions && (inTag || htmlDepth>0)) && source.lastIndexOf('<',i-1)<=source.lastIndexOf('>',i-1)) {
                    Arrays.fill(mask,i,lineEnd,true);i=lineEnd-1;continue;
                }
            }
            if(source.startsWith("<!--",i)) {
                int end=source.indexOf("-->",i+4);end=end<0?source.length():end+3;
                Arrays.fill(mask,i,end,true);i=end-1;
            } else if(source.charAt(i)=='`') {
                int count=1;while(i+count<source.length() && source.charAt(i+count)=='`') count++;
                int end=source.indexOf("`".repeat(count),i+count);
                if(end<0) { end=source.indexOf('\n',i);if(end<0)end=source.length(); } else end+=count;
                Arrays.fill(mask,i,end,true);i=end-1;
            } else if(source.startsWith("{{",i)) {
                int end=source.indexOf("}}",i+2);end=end<0?source.length():end+2;
                if(!preserveExpressions) Arrays.fill(mask,i,end,true);
                i=end-1;
            } else if(source.regionMatches(true,i,"<script",0,7) || source.regionMatches(true,i,"<style",0,6)) {
                var raw=Pattern.compile("(?is)<(script|style)\\b[^>]*>.*?(?:</\\1\\s*>|\\z)").matcher(source);
                raw.region(i,source.length());
                if(raw.lookingAt()) { Arrays.fill(mask,i,raw.end(),true);i=raw.end()-1;inTag=false; }
            }
        }
        return mask;
    }
    private static boolean escaped(String source,int offset) {
        int count=0;while(offset>0 && source.charAt(--offset)=='\\')count++;
        return count%2==1;
    }
    private static String unquote(String line) { return line.replaceFirst("^(?: {0,3}> ?)+", ""); }
    private MarkupCompletion() {}
}
