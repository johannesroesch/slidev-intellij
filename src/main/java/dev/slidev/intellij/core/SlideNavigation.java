package dev.slidev.intellij.core;

import java.util.Optional;
import java.util.regex.Pattern;

/** File-local navigation only. src imports need a deck-wide source map. */
public final class SlideNavigation {
    private static final Pattern SRC=Pattern.compile("(?m)^\\s*src\\s*:");
    public static boolean supports(String source) {
        return DeckParser.parse(source).slides().stream().noneMatch(slide -> slide.frontmatter()!=null &&
            SRC.matcher(source.substring(slide.frontmatter().start(),slide.frontmatter().end())).find());
    }
    public static Optional<DeckParser.Slide> atOffset(String source, int offset) {
        if(offset<0 || offset>source.length()) return Optional.empty();
        var slides=DeckParser.parse(source).slides();
        for(int i=slides.size()-1;i>=0;i--) if(offset>=slides.get(i).start()) return Optional.of(slides.get(i));
        return Optional.empty();
    }
    public static Optional<DeckParser.Slide> byNumber(String source, int number) {
        var slides=DeckParser.parse(source).slides();
        return number<1 || number>slides.size()?Optional.empty():Optional.of(slides.get(number-1));
    }
    public static int caretOffset(DeckParser.Slide slide) {
        return slide.contentStart()<slide.end()?slide.contentStart():slide.start();
    }
    private SlideNavigation() {}
}
