package dev.slidev.intellij.core;

import java.util.*;

/** Small, reviewable edits. Applying to another snapshot is explicitly rejected. */
public final class QuickFixes {
    public record Edit(int start,int end,String expected,String replacement,String text) {
        public String apply(String source) {
            if(start<0 || end<start || end>source.length() || !source.substring(start,end).equals(expected))throw new IllegalStateException("Quick fix is stale");
            return source.substring(0,start)+replacement+source.substring(end);
        }
    }
    public static List<Edit> values(String source,int start,int end,SlidevSchema.Field field) {
        if(field==null)return List.of();
        List<String> values=field.values();
        if(values.isEmpty())values=switch(field.rule()){case "positive" -> List.of("1");case "nonnegative" -> List.of("0");default -> List.of();};
        List<Edit> edits=new ArrayList<>();
        for(String value:values) edits.add(new Edit(start,end,source.substring(start,end),value,"Set "+field.name()+" to "+value));
        return List.copyOf(edits);
    }
    public static List<Edit> layouts(String source,ProjectReferences.Reference reference,Collection<String> available) {
        if(reference.value().length()>256)return List.of();
        return available.stream().distinct().filter(value -> value.length()<=256 && !value.equals(reference.value()))
            .sorted(Comparator.comparingInt((String value) -> distance(reference.value(),value)).thenComparing(value -> value))
            .limit(3).map(value -> new Edit(reference.start(),reference.end(),source.substring(reference.start(),reference.end()),value,"Use layout '"+value+"'" )).toList();
    }
    public static Optional<Edit> closing(String source,MetadataRegions.Problem problem) {
        String marker;
        if(problem.message().equals("Code fence is not closed")) {
            int end=source.indexOf('\n',problem.start());if(end<0)end=source.length();
            String opening=source.substring(problem.start(),end).stripLeading();
            if(opening.isEmpty())return Optional.empty();char ch=opening.charAt(0);int count=0;
            while(count<opening.length() && opening.charAt(count)==ch)count++;
            if((ch!='`' && ch!='~') || count<3)return Optional.empty();marker=(""+ch).repeat(count);
        } else if(problem.message().equals("Frontmatter is missing its closing ---")) {
            int first=source.indexOf('\n',problem.start());if(first<0)return Optional.empty();boolean key=false;
            for(String raw:source.substring(first+1).split("\n")) {
                String line=raw.replace("\r", "");
                if(line.isBlank())continue;
                if(line.startsWith("#") || (!Character.isWhitespace(line.charAt(0)) && !line.matches("[\\w.-]+\\s*:.*")))return Optional.empty();
                if(line.matches("[\\w.-]+\\s*:.*"))key=true;
            }
            if(!key)return Optional.empty();marker="---";
        } else return Optional.empty();
        String newline=source.contains("\r\n")?"\r\n":"\n";
        return Optional.of(new Edit(source.length(),source.length(),"",(source.endsWith("\n")?"":newline)+marker+newline,"Close block with "+marker));
    }
    private static int distance(String left,String right) {
        int[] previous=new int[right.length()+1];for(int i=0;i<previous.length;i++)previous[i]=i;
        for(int i=1;i<=left.length();i++){int[] current=new int[right.length()+1];current[0]=i;for(int j=1;j<=right.length();j++)current[j]=Math.min(Math.min(current[j-1],previous[j])+1,previous[j-1]+(left.charAt(i-1)==right.charAt(j-1)?0:1));previous=current;}
        return previous[right.length()];
    }
    private QuickFixes() {}
}
