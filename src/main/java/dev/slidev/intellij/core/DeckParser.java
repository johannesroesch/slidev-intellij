package dev.slidev.intellij.core;

import java.util.*;
import java.util.regex.Pattern;

/** Structural MVP parser, not the complete Slidev grammar. Offsets use Java/IDE UTF-16. */
public final class DeckParser {
    public record Span(int start, int end) {}
    public record Slide(int number, int start, int end, String title, Span frontmatter, int contentStart) {
        @Override public String toString() { return number + ". " + title; }
    }
    public record Deck(List<Slide> slides, List<Span> separators) {}
    private record Line(String text, int start, int end) {}
    private static final Pattern KEY = Pattern.compile("[A-Za-z_][\\w.-]*\\s*:.*");
    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+(.+?)\\s*#*\\s*$");

    public static Deck parse(String input) {
        List<Line> lines = new ArrayList<>();
        int offset = 0;
        for (String line : input.split("\\n", -1)) {
            lines.add(new Line(line.replace("\r", ""), offset, Math.min(input.length(), offset + line.length() + 1)));
            offset += line.length() + 1;
        }
        List<Slide> slides = new ArrayList<>();
        List<Span> separators = new ArrayList<>();
        int start = 0, content = 0;
        Span meta = null;
        if (!lines.isEmpty() && clean(lines.get(0).text).equals("---")) {
            int close = yamlClose(lines, 0);
            if (close > 0) {
                separators.add(span(lines.get(0))); separators.add(span(lines.get(close)));
                meta = new Span(lines.get(0).end, lines.get(close).start);
                content = close + 1;
            }
        }
        char fence = 0; int fenceLength = 0;
        boolean comment = false;
        for (int i = content; i < lines.size(); i++) {
            Line line = lines.get(i);
            String trimmed = line.text.stripLeading();
            if (comment) {
                if (trimmed.contains("-->")) comment = false;
                continue;
            }
            if (fence != 0) {
                if (trimmed.matches(Pattern.quote(String.valueOf(fence)) + "{" + fenceLength + ",}\\s*")) fence = 0;
                continue;
            }
            if (trimmed.startsWith("<!--")) {
                comment = !trimmed.substring(4).contains("-->"); continue;
            }
            // Four-space indentation is a Markdown code block, not a slide boundary.
            if (line.text.startsWith("    ") || line.text.startsWith("\t")) continue;
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                fence = trimmed.charAt(0); fenceLength = 0;
                while (fenceLength < trimmed.length() && trimmed.charAt(fenceLength) == fence) fenceLength++;
                continue;
            }
            if (!trimmed.stripTrailing().equals("---")) continue;
            slides.add(new Slide(slides.size()+1, start, line.start, title(lines, content, i), meta, contentOffset(lines, content, i, line.start)));
            separators.add(span(line));
            start = line.start;
            int close = yamlClose(lines, i);
            if (close > i) {
                meta = new Span(line.end, lines.get(close).start);
                separators.add(span(lines.get(close)));
                content = close + 1; i = close;
            } else { meta = null; content = i + 1; }
        }
        slides.add(new Slide(slides.size()+1, start, input.length(), title(lines, content, lines.size()), meta, contentOffset(lines, content, lines.size(), input.length())));
        return new Deck(List.copyOf(slides), List.copyOf(separators));
    }
    private static int contentOffset(List<Line> lines, int start, int end, int fallback) {
        while(start < end && lines.get(start).text.isBlank()) start++;
        return start < end ? lines.get(start).start : fallback;
    }
    private static String clean(String text) { return text.replace("\ufeff", "").strip(); }
    private static Span span(Line line) { return new Span(line.start, line.start + line.text.length()); }
    private static int yamlClose(List<Line> lines, int open) {
        boolean key = false;
        for (int i = open + 1; i < lines.size(); i++) {
            String text = lines.get(i).text;
            if (text.strip().equals("---")) return key ? i : -1;
            if (text.isBlank() || text.stripLeading().startsWith("#")) continue;
            if (!Character.isWhitespace(text.charAt(0))) {
                if (!KEY.matcher(text).matches()) return -1;
                key = true;
            }
        }
        return -1;
    }
    private static String title(List<Line> lines, int start, int end) {
        boolean comment = false;
        char fence = 0; int fenceLength = 0;
        for (int i = start; i < end; i++) {
            String text = lines.get(i).text.stripLeading();
            if (fence != 0) {
                if (text.matches(Pattern.quote(String.valueOf(fence))+"{"+fenceLength+",}\\s*")) fence=0;
                continue;
            }
            if (comment) { if (text.contains("-->")) comment=false; continue; }
            if (text.startsWith("<!--")) { comment=!text.substring(4).contains("-->"); continue; }
            if (text.startsWith("```") || text.startsWith("~~~")) {
                fence=text.charAt(0);fenceLength=0;
                while(fenceLength<text.length() && text.charAt(fenceLength)==fence) fenceLength++;
                continue;
            }
            var match = HEADING.matcher(text);
            if (match.matches()) return match.group(1);
        }
        return "Untitled slide";
    }
    private DeckParser() {}
}
