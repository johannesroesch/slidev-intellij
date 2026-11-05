package dev.slidev.intellij.core;

import java.nio.file.*;
import java.util.*;

public final class ReferencesTest {
    private static int checks;
    private static void check(boolean value,String label) {if(!value)throw new AssertionError(label);checks++;}
    private static void file(Path root,String name) throws Exception {Path p=root.resolve(name);Files.createDirectories(p.getParent());Files.writeString(p,"");}
    private static List<ProjectReferences.Resolution> analyze(Path entry,String source) {return ProjectReferences.analyze(entry,source,() -> {});}
    private static ProjectReferences.Resolution single(Path entry,String source) {
        var refs=analyze(entry,source);check(refs.size()==1,"Exactly one reference: "+source);return refs.get(0);
    }
    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory("slidev-references-");
        try {
            file(root,"package.json");file(root,"slides/deck.md");file(root,"slides/pages/intro.md");
            file(root,"slides/img/cover.png");file(root,"slides/img/a b.png");file(root,"slides/img/a+b.png");
            file(root,"slides/img/a(b).png");file(root,"slides/img/a'b.png");file(root,"slides/img/a#b.png");
            file(root,"slides/img/a&b.png");
            file(root,"public/logo.svg");file(root,"layouts/project-layout.vue");
            file(root,"node_modules/@slidev/theme-default/layouts/theme-layout.vue");
            file(root,"node_modules/@slidev/theme-default/public/theme-logo.svg");
            file(root,"node_modules/@slidev/client/layouts/fact.vue");
            Path entry=root.resolve("slides/deck.md");
            var imported=single(entry,"---\nsrc: ./pages/intro.md\n---");
            check(imported.target().equals(root.resolve("slides/pages/intro.md")) && imported.warning()==null,"Relative import target");
            check(single(entry,"---\nsrc: ./pages/missing.md\n---").warning()!=null,"Missing import warning");
            check(single(entry,"---\nsrc: ./pages/intro.md#2,5-7\n---").target().equals(imported.target()),"Import slide-range fragment");
            check(single(entry,"---\nsrc: './pages/intro.md' # note\n---").target().equals(imported.target()),"Quoted YAML path and comment");
            check(single(entry,"![Cover](./img/cover.png)").target().equals(root.resolve("slides/img/cover.png")),"Relative Markdown image");
            check(single(entry,"![Cover](./img/missing.png)").warning()!=null,"Missing Markdown image");
            check(single(entry,"![Cover](<./img/a b.png>)").target().equals(root.resolve("slides/img/a b.png")),"Angle-wrapped path with spaces");
            check(single(entry,"![Cover](./img/a\\ b.png)").target().equals(root.resolve("slides/img/a b.png")),"Escaped Markdown space");
            check(single(entry,"![Cover](./img/a(b).png)").target().equals(root.resolve("slides/img/a(b).png")),"Balanced parentheses in path");
            check(single(entry,"![Nested [alt]](./img/cover.png \"A title\")").target()!=null,"Nested label and optional title");
            check(single(entry,"![Cover](./img/a+b.png)").target()!=null,"Plus is not decoded as a space");
            check(single(entry,"![Cover](./img/a%20b.png)").target()!=null,"URL-encoded space");
            check(single(entry,"![Cover](./img/a%23b.png#fragment)").target()!=null,"Encoded hash in filename");
            check(single(entry,"![Cover](./img/cover.png?width=400#image)").target()!=null,"Asset query and fragment");
            check(single(entry,"![Logo](/logo.svg)").target().equals(root.resolve("public/logo.svg")),"Leading slash maps to public");
            check(single(entry,"<img src=\"./img/cover.png\" />").target()!=null,"Static HTML image");
            check(single(entry,"<img src=\"./img/a&amp;b.png\" />").target()!=null,"HTML entity in path");
            check(single(entry,"![x](./img/a&#38;b.png)").target()!=null,"Numeric entity in Markdown image path");
            check(single(entry,"<img\n  src='./img/missing.png'\n/>").warning()!=null,"Multiline HTML image");
            check(single(entry,"<image href=\"/logo.svg\" />").target()!=null,"SVG image href");
            check(single(entry,"<SlidevVideo poster=\"./img/cover.png\" />").target()!=null,"Video poster image");
            check(single(entry,"---\nbackground: ./img/cover.png\n---").target()!=null,"Background image");
            check(single(entry,"---\nbackground: \"url('./img/cover.png')\"\n---").target()!=null,"CSS url background");
            check(single(entry,"---\nbackground: 'url(''./img/cover.png'')'\n---").target()!=null,"YAML-escaped quotes in CSS URL");
            check(single(entry,"---\nimage: /logo.svg\n---").target()!=null,"Layout image in metadata");
            check(single(entry,"---\nimage: './img/a''b.png'\n---").target()!=null,"YAML single-quote escape");
            check(single(entry,"---\ndefaults:\n  image: /logo.svg\n---").target()!=null,"Defaults image");
            check(single(entry,"---\nimage: /theme-logo.svg\n---").target()!=null,"Theme public asset");
            var layout=single(entry,"---\nlayout: project-layout\n---");
            check(layout.target().equals(root.resolve("layouts/project-layout.vue")),"Project layout navigation target");
            check(single(entry,"---\nlayout: theme-layout\n---").target()!=null,"Installed theme layout");
            check(single(entry,"---\nlayout: fact\n---").target()!=null,"Installed built-in layout source");
            check(single(entry,"---\nlayout: full\n---").warning()==null,"Known builtin without installed source");
            check(single(entry,"---\nlayout: typo-layout\n---").warning().contains("Addon"),"Unknown layout warning is qualified");
            check(single(entry,"---\ndefaults:\n  layout: project-layout\n---").target()!=null,"Defaults layout navigation");
            file(root,"local-theme/layouts/local-theme-layout.vue");
            check(single(entry,"---\ntheme: ./local-theme\nlayout: local-theme-layout\n---").target()!=null,"Local theme layout");
            file(root,"layouts/fact.vue");
            check(single(entry,"---\nlayout: fact\n---").target().equals(root.resolve("layouts/fact.vue")),"Project overrides built-in layout target");
            file(root,"secret.png");
            check(single(entry,"![x](/../secret.png)").target()==null,"Public URL cannot escape public root");
            file(root,"isolated/package.json");file(root,"isolated/slides.md");
            file(root,"store/node_modules/@slidev/cli/bin/slidev.mjs");
            file(root,"store/node_modules/@slidev/client/layouts/indirect.vue");
            Path link=root.resolve("isolated/node_modules/@slidev/cli");Files.createDirectories(link.getParent());
            boolean symlinks=true;
            try {Files.createSymbolicLink(link,root.resolve("store/node_modules/@slidev/cli"));}
            catch(java.io.IOException | UnsupportedOperationException | SecurityException ex) {symlinks=false;System.out.println("SKIP: symlink fixture unavailable on this filesystem");}
            if(symlinks)check(single(root.resolve("isolated/slides.md"),"---\nlayout: indirect\n---").target()!=null,"pnpm-style CLI symlink resolves built-in layout dependency");
            String positioned="\ufeff---\r\nsrc: './pages/intro.md'\r\n---\r\n# 🐇\r\n![x](./img/cover.png)\r\n";
            var positions=ProjectReferences.references(positioned);
            check(positions.size()==2 && positions.get(0).start()==positioned.indexOf("./pages"),"BOM and CRLF metadata offset");
            check(positions.get(1).start()==positioned.indexOf("./img"),"Unicode UTF-16 body offset");
            var go=ProjectReferences.at(entry,positioned,positioned.indexOf("intro"),() -> {});
            check(go!=null && go.target().equals(imported.target()),"Navigation by source caret offset");
            check(ProjectReferences.at(entry,positioned,positioned.indexOf("# 🐇"),() -> {})==null,"Ordinary text has no target");
            for(String source:List.of(
                "![x](https://example.com/x.png)","![x](//example.com/x.png)",
                "![x](data:image/png;base64,abc)","![x](@/image.png)","![x](/@fs/generated.png)",
                "![x](./image-${name}.png)","---\nsrc: https://example.com/slides.md\n---")) {
                var ref=single(entry,source);check(ref.target()==null && ref.warning()==null,"External/dynamic path ignored");
            }
            for(String source:List.of(
                "```md\n![x](missing.png)\n```","`![x](missing.png)`","<!-- ![x](missing.png) -->", "\\![x](missing.png)",
                "{{ '![x](missing.png)' }}", "<div title=\"![x](missing.png)\">X</div>",
                "<img :src=\"dynamicPath\" />", "<img v-bind:src=\"dynamicPath\" />", "<script>const x='![x](missing.png)'</script>",
                "---\nbackground: red\n---", "---\nbackground: '#fff'\n---", "---\nbackground: linear-gradient(red, blue)\n---",
                "---\ninfo: |\n  image: /missing.png\n---", "---\nfonts:\n  src: missing.md\n---", "---\nsrc: *sharedPath\n---",
                "![x](unescaped space.png)","![x](unfinished.png", "<img src=\"./img/unknown&custom;entity.png\" />")) {
                check(ProjectReferences.references(source).isEmpty(),"Excluded source: "+source);
            }
            boolean canceled=false;
            try {ProjectReferences.analyze(entry,"![x](missing.png)",() -> {throw new IllegalStateException("canceled");});}
            catch(IllegalStateException expected) {canceled=true;}
            check(canceled,"Cancellation propagated");
        } finally {try(var files=Files.walk(root)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
        System.out.println("PASS: "+checks+" project reference and navigation-target checks");
    }
}
