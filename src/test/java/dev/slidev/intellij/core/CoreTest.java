package dev.slidev.intellij.core;

public final class CoreTest {
    private static int checks;
    private static void check(boolean condition,String label) { checks++;if(!condition) throw new AssertionError(label); }
    public static void main(String[] args) {
        var deck=DeckParser.parse("---\ntheme: default\ntitle: Test\n---\n# First\n\n---\nlayout: center\n---\n# Second\n\n---\n# Third\n");
        check(deck.slides().size()==3,"Headmatter and per-slide frontmatter are not extra slides");
        check(deck.slides().get(1).title().equals("Second"),"Heading after YAML");
        check(deck.slides().get(0).frontmatter()!=null,"Headmatter span");
        check(deck.slides().get(1).frontmatter()!=null,"Per-slide frontmatter span");
        check(deck.slides().get(2).frontmatter()==null,"Slide without frontmatter");
        String fenced="# One\n````md\n---\n```\n# Fake\n````\n\n---\n# Two";
        check(DeckParser.parse(fenced).slides().size()==2,"Ignore separators inside longer code fences");
        check(DeckParser.parse("~~~yaml\n---\n~~~\n\n---\n# Two").slides().size()==2,"Tilde fences");
        check(DeckParser.parse("<!--\n---\n# Note\n-->\n# One\n\n---\n# Two").slides().size()==2,"Notes do not introduce slides");
        check(DeckParser.parse("<!--\n# Note\n-->\n# Actual").slides().get(0).title().equals("Actual"),"Ignore note headings");
        check(DeckParser.parse("    ---\n# One").slides().size()==1,"Indented code block");
        var windows=DeckParser.parse("\ufeff---\r\ntheme: default\r\n---\r\n# One\r\n\r\n---\r\n# Two\r\n");
        check(windows.slides().size()==2,"BOM and CRLF");
        check(windows.slides().get(1).title().equals("Two"),"CRLF heading");
        check(DeckParser.parse("# One\n---\n---\n# Three").slides().size()==3,"Empty slide");
        var block=DeckParser.parse("# One\n\n---\n```yaml\nlayout: center\n```\n# Two");
        check(block.slides().size()==2,"Block frontmatter doesn't split deck");
        check(block.slides().get(1).title().equals("Two"),"Heading outside block frontmatter");
        String unicode="# 🐇 First\n\n---\n# Second";
        var uni=DeckParser.parse(unicode);
        check(uni.slides().get(1).start()==unicode.indexOf("---"),"UTF-16 offsets for IDE");
        for(var slide:deck.slides()) check(slide.end()>=slide.start(),"Monotonic offsets");
        String source="\n".repeat(50)+"---\nlayout: pb\n---\n# Welcome to Performance Bites\n\n**Every two weeks, one concept in 30 minutes**\n\n{{ 1 + }}\n";
        // First slide ends at line 51; content of slide 2 starts at line 54.
        var oldErrors=BuildDiagnostics.parse("\u001b[31m[vite:vue] [plugin vite:vue] slides.md__slidev_2.md (5:7): Error parsing JavaScript expression: Unexpected token (1:4)\u001b[0m\nfile: /deck/slides.md__slidev_2.md:5:7\nSyntaxError: [plugin vite:vue] slides.md__slidev_2.md (5:7): Error parsing JavaScript expression: Unexpected token (1:4)");
        check(oldErrors.size()==1,"Deduplicate Vite 7 diagnostic");
        check(oldErrors.get(0).path().equals("slides.md"),"Vite 7 source filename");
        var modernErrors=BuildDiagnostics.parse("[plugin vite:vue] /deck/slides.md__slidev_2.md:5:7\nRolldownError: Error parsing JavaScript expression: Unexpected token (1:4)\nSyntaxError: Error parsing JavaScript expression: Unexpected token (1:4)");
        check(modernErrors.size()==1,"Vite 8 diagnostic on following line");
        check(modernErrors.get(0).path().equals("/deck/slides.md"),"Vite 8 absolute path");
        var location=BuildDiagnostics.locate(source,modernErrors.get(0)).orElseThrow();
        check(location.line()==58 && location.column()==7,"Slide-relative 5:7 maps to 58:7");
        check(location.offset()==source.indexOf("{{ 1 + }}")+6,"Mapped error character");
        var crlf=BuildDiagnostics.locate(source.replace("\n","\r\n"),modernErrors.get(0)).orElseThrow();
        check(crlf.line()==58 && crlf.column()==7,"CRLF diagnostic mapping");
        check(BuildDiagnostics.locate(source,new BuildDiagnostics.Diagnostic("slides.md",99,1,1,"bad")).isEmpty(),"Unknown slide has no invented location");
        check(BuildDiagnostics.locate(source,new BuildDiagnostics.Diagnostic("slides.md",2,99,1,"bad")).isEmpty(),"Out-of-slide line has no invented location");
        String blank="# First\n---\n\n\n# Second\n{{ 1 + }}";
        check(BuildDiagnostics.locate(blank,new BuildDiagnostics.Diagnostic("slides.md",2,2,7,"bad")).orElseThrow().line()==6,"Leading blank content lines trimmed");
        check(BuildDiagnostics.parse("SyntaxError: no location\n at compiler.js:4:2").isEmpty(),"Stack trace is not a source diagnostic");
        check(BuildDiagnostics.parse("[plugin vite:vue] /a path/slides.md__slidev_2.md:5:7\nSyntaxError: bad").get(0).path().equals("/a path/slides.md"),"Paths with spaces");
        String navigation="# First\n---\nlayout: center\n---\n# Second\n";
        int boundary=navigation.indexOf("---");
        check(SlideNavigation.atOffset(navigation,boundary-1).orElseThrow().number()==1,"Cursor before separator stays on previous slide");
        check(SlideNavigation.atOffset(navigation,boundary).orElseThrow().number()==2,"Separator belongs to next slide");
        check(SlideNavigation.atOffset(navigation,navigation.indexOf("layout")).orElseThrow().number()==2,"Frontmatter cursor maps to slide");
        check(SlideNavigation.atOffset(navigation,navigation.length()).orElseThrow().number()==2,"End of document navigation");
        check(SlideNavigation.byNumber(navigation,2).orElseThrow().contentStart()==navigation.indexOf("# Second"),"Preview maps to content after frontmatter");
        check(SlideNavigation.byNumber(navigation,0).isEmpty(),"Invalid preview slide ignored");
        check(SlideNavigation.byNumber(navigation,3).isEmpty(),"Out-of-deck preview slide ignored");
        check(SlideNavigation.atOffset(navigation,-1).isEmpty(),"Invalid caret offset ignored");
        check(SlideNavigation.supports(navigation),"Local deck supports sync");
        check(!SlideNavigation.supports("---\ntheme: default\n---\n# First\n---\nsrc: ./other.md\n---\n"),"Imported deck blocks ambiguous numbering");
        check(SlideNavigation.supports("# First\n```yaml\nsrc: example\n```"),"Example src in code does not block sync");
        String emptySlide="# One\n---\n---\n# Three";
        var emptyTarget=SlideNavigation.byNumber(emptySlide,2).orElseThrow();
        check(SlideNavigation.atOffset(emptySlide,SlideNavigation.caretOffset(emptyTarget)).orElseThrow().number()==2,"Empty slide reverse navigation stays on empty slide");
        System.out.println("PASS: "+checks+" parser, diagnostic and navigation checks");
    }
}
