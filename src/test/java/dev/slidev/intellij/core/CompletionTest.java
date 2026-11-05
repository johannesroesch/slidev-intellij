package dev.slidev.intellij.core;

import java.nio.file.*;
import java.util.*;

/** Real filesystem fixtures plus lexical contexts/insertion plans. No IDE API stubs. */
public final class CompletionTest {
    private static int checks;
    private static void check(boolean condition,String label) {
        if(!condition) throw new AssertionError(label);checks++;
    }
    private static MarkupCompletion.Context markup(String source) {
        return MarkupCompletion.context(source.replace("§", ""),source.indexOf('§'));
    }
    private static MetadataRegions.Completion yaml(String source) {
        return MetadataRegions.completion(source.replace("§", ""),source.indexOf('§'));
    }
    private static boolean offers(MetadataRegions.Completion context,String key) {
        return context!=null && SlidevSchema.fields(context.parent(),context.block().head()).stream().anyMatch(f -> f.name().equals(key));
    }
    private static void file(Path root,String path) throws Exception {
        Path target=root.resolve(path);Files.createDirectories(target.getParent());Files.writeString(target,"");
    }
    public static void main(String[] args) throws Exception {
        check(markup("<§").kind()==MarkupCompletion.Kind.TAG,"Component after opening bracket");
        check(markup("<v-cl§").prefix().equals("v-cl"),"Kebab component prefix");
        check(markup("<SlidevV§").prefix().equals("SlidevV"),"Pascal component prefix");
        check(markup("</v-cl§").kind()==MarkupCompletion.Kind.CLOSE_TAG,"Closing tag context");
        check(markup("<div v-§").kind()==MarkupCompletion.Kind.ATTRIBUTE,"Directive inside HTML tag");
        check(markup("<SlidevVideo co§").tag().equals("SlidevVideo"),"Component prop context");
        check(markup("<SlidevVideo\n    co§")!=null,"Indented multiline attributes");
        check(markup("<div title=\"a > b\" v-§")!=null,"Angle bracket inside quoted value");
        check(markup("<div title=\"v-cl§\"")==null,"No directive inside quoted attribute");
        check(markup("<div :class=\"{ x: 'v-cl§' }\"")==null,"No directive inside expression string");
        check(markup("<div src=abc§")==null,"No completion in unquoted value");
        check(markup("<div>v-cl§")==null,"No completion in normal text");
        check(markup("\\<v-cl§")==null,"Escaped Markdown bracket");
        check(markup("`<v-cl§`")==null,"Inline code exclusion");
        check(markup("``<v-cl§``")==null,"Long inline code delimiter");
        check(markup("```html\n<v-cl§\n```")==null,"Code fence exclusion");
        check(markup("````md\n```\n<v-cl§\n````")==null,"Shorter nested code fence");
        check(markup("~~~html\n<v-cl§\n~~~")==null,"Tilde fence exclusion");
        check(markup("> ```html\n> <v-cl§\n> ```")==null,"Blockquoted code fence exclusion");
        check(markup("    <v-cl§")==null,"Indented code exclusion");
        check(markup("<!-- <v-cl§ -->")==null,"Inline comment exclusion");
        check(markup("<!--\n```\n-->\n<v-cl§")!=null,"Fence inside comment does not mask later content");
        check(markup("```md\n<!--\n```\n<v-cl§")!=null,"Comment inside code does not mask later content");
        check(markup("---\ntitle: <v-cl§\n---")==null,"Metadata exclusion");
        check(markup("{{ '<v-cl§' }}")==null,"Vue interpolation exclusion");
        check(markup("<script setup>const x='<v-cl§'</script>")==null,"Script exclusion");
        check(markup("<style>/* <v-cl§ */</style>")==null,"Style exclusion");
        var attrs=markup("<div title=\"v-after\" v-click v-§");
        check(attrs.attributes().contains("v-click") && !attrs.attributes().contains("v-after"),"Existing attribute names exclude quoted contents");
        check(MarkupCompletion.DIRECTIVES.stream().noneMatch(a -> a.name().equals("v-clicks")),"v-clicks is a component, not a directive");
        check(MarkupCompletion.attributes("VClicks").stream().anyMatch(a -> a.name().equals("depth")),"Component props accept Pascal alias");
        check(MarkupCompletion.attributes("div").stream().noneMatch(a -> a.name().equals("depth")),"No component-only props on HTML");
        check(MarkupCompletion.attributes("AutoFitText").stream().anyMatch(a -> a.name().equals(":max")),"Numeric-only prop uses Vue binding");
        var pair=MarkupCompletion.insertion("",MarkupCompletion.Kind.TAG,"v-click",false);
        check(pair.text().equals("></v-click>") && pair.caret()==1,"New tag pair with caret inside");
        check(MarkupCompletion.insertion(">old</v-click>",MarkupCompletion.Kind.TAG,"v-click",false).text().isEmpty(),"Preserve existing tag delimiters");
        check(MarkupCompletion.insertion(" />",MarkupCompletion.Kind.TAG,"SlidevVideo",false).text().isEmpty(),"Preserve self-closing tag");
        check(MarkupCompletion.insertion("",MarkupCompletion.Kind.CLOSE_TAG,"v-click",false).text().equals(">"),"Close end tag");
        check(MarkupCompletion.insertion(">",MarkupCompletion.Kind.CLOSE_TAG,"v-click",false).text().isEmpty(),"No doubled end delimiter");
        var value=MarkupCompletion.insertion(" />",MarkupCompletion.Kind.ATTRIBUTE,"src",true);
        check(value.text().equals("=\"\"") && value.caret()==2,"Prop value quotes and caret");
        check(MarkupCompletion.insertion(" =\"old\"",MarkupCompletion.Kind.ATTRIBUTE,"src",true).text().isEmpty(),"Preserve existing prop value");
        check(MarkupCompletion.insertion("",MarkupCompletion.Kind.ATTRIBUTE,"v-click",false).text().isEmpty(),"Boolean directive needs no value");

        check(offers(yaml("---\nfonts:\n  sa§\n---"),"sans"),"Nested font keys");
        check(!offers(yaml("---\nfonts:\n  sa§\n---"),"theme"),"Nested context excludes deck keys");
        check(offers(yaml("---\ndrawings:\n  en§\n---"),"enabled"),"Drawing keys");
        check(offers(yaml("---\nexport:\n  wi§\n---"),"withClicks"),"Export keys");
        check(offers(yaml("---\ndefaults:\n  la§\n---"),"layout"),"Default slide keys");
        check(!offers(yaml("---\ndefaults:\n  th§\n---"),"theme"),"Defaults exclude deck keys");
        check(SlidevSchema.find("export","format",true).values().contains("pdf"),"Nested enum values");
        check(yaml("---\nfonts:\n  provider: go§\n---").parent().equals("fonts"),"Nested value parent");
        check(yaml("---\nfonts:\n  custom:\n    - X\n    sa§\n---")==null,"No suggestions inside sequence");
        check(yaml("---\ninfo: >\n  fonts:\n    sa§\n---")==null,"No nested suggestions inside block scalar");
        check(yaml("---\ninfo: |2-\n  fonts:\n    sa§\n---")==null,"Literal scalar with indentation and chomping");
        check(offers(yaml("---\ninfo: >\n  paragraph\nfonts:\n  sa§\n---"),"sans"),"Resume mapping after block scalar");
        check(offers(yaml("---\nfonts:\n  sans: Roboto\nco§\n---"),"colorSchema"),"Dedent returns to root");
        check(!offers(yaml("---\nunknown:\n  la§\n---"),"layout"),"Unknown nested mapping stays open without guessed keys");
        check(!offers(yaml("# First\n---\nfonts:\n  sa§\n---"),"sans"),"Head-only mappings excluded from slide metadata");
        check(offers(yaml("\ufeff---\r\nfonts:\r\n  sa§\r\n---"),"sans"),"Nested CRLF and BOM context");

        Path root=Files.createTempDirectory("slidev-completion-test-");
        try {
            file(root,"package.json");file(root,"slides/deck.md");
            file(root,"layouts/local.vue");file(root,"layouts/ignore.txt");
            file(root,"components/my-chart.vue");file(root,"components/nested/StatusBox.vue");
            file(root,"node_modules/@slidev/theme-default/layouts/theme-layout.vue");
            file(root,"node_modules/@slidev/theme-default/layouts/local.vue");
            file(root,"node_modules/@slidev/theme-default/components/ThemeCard.vue");
            var catalog=ProjectCompletions.discover(root.resolve("slides/deck.md"),"# Slides",() -> {});
            check(catalog.layouts().containsKey("local") && catalog.layouts().containsKey("theme-layout"),"Project and installed theme layouts");
            check(catalog.layouts().get("local").startsWith("project:"),"Project overrides theme provenance");
            check(!catalog.layouts().containsKey("ignore"),"Only Vue files");
            check(catalog.components().containsKey("MyChart") && catalog.components().containsKey("StatusBox"),"Local and nested Vue components");
            check(catalog.components().containsKey("ThemeCard"),"Theme components");
            file(root,"local-theme/layouts/from-local-theme.vue");
            check(ProjectCompletions.discover(root.resolve("slides/deck.md"),"---\ntheme: './local-theme'\n---",() -> {}).layouts().containsKey("from-local-theme"),"Local theme path");
            check(ProjectCompletions.theme("---\ntheme: 'seriph' # comment\n---").equals("seriph"),"Quoted theme name");
            check(ProjectCompletions.theme("---\ntheme:\nfonts:\n  sans: Roboto\n---").equals("default"),"Empty theme cannot consume the next YAML line");
            check(ProjectCompletions.discover(root.resolve("slides/deck.md"),"---\ntheme: missing-theme\n---",() -> {}).components().containsKey("MyChart"),"Missing theme retains project suggestions");
            check(!ProjectCompletions.discover(root.resolve("slides/deck.md"),"---\ntheme: none\n---",() -> {}).components().containsKey("ThemeCard"),"Explicit none skips default theme");
            file(root,"node_modules/slidev-theme-custom/layouts/custom.vue");
            check(ProjectCompletions.discover(root.resolve("slides/deck.md"),"---\ntheme: custom\n---",() -> {}).layouts().containsKey("custom"),"Community theme package resolution");
            boolean canceled=false;
            try { ProjectCompletions.discover(root.resolve("slides/deck.md"),"",() -> { throw new IllegalStateException("canceled"); }); }
            catch(IllegalStateException expected) { canceled=true; }
            check(canceled,"Discovery propagates cancellation");
        } finally {
            try(var paths=Files.walk(root)) { for(Path path:paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path); }
        }
        System.out.println("PASS: "+checks+" extended completion and project discovery checks");
    }
}
