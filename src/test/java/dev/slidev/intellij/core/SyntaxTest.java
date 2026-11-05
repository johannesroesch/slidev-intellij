package dev.slidev.intellij.core;

/** Regression checks for metadata boundaries, recovery, context and open-schema validation. */
public final class SyntaxTest {
    private static int checks;
    private static void check(boolean value,String label) {
        if(!value) throw new AssertionError(label);
        checks++;
    }
    private static MetadataRegions.Completion completion(String marked) {
        int caret=marked.indexOf('|');
        return MetadataRegions.completion(marked.replace("|", ""),caret);
    }
    public static void main(String[] args) {
        String source="---\ntheme: default\nfonts:\n  sans: Roboto\n---\n# First\n---\nlayout: center\nhide: false\n---\n# Second\n---\n# Third\n";
        var scan=MetadataRegions.scan(source);
        check(scan.blocks().size()==2,"Headmatter and per-slide frontmatter only");
        var head=scan.blocks().get(0); var slide=scan.blocks().get(1);
        check(head.head() && !slide.head(),"Headmatter scope");
        check(source.substring(head.start(),head.end()).equals("theme: default\nfonts:\n  sans: Roboto\n"),"Exact YAML fragment range");
        check(scan.problems().isEmpty(),"Valid deck has no structural problems");
        check(MetadataRegions.scan("# Heading\n\n---\n# Next\nText").blocks().isEmpty(),"Ordinary slides are not YAML");
        check(MetadataRegions.scan("```yaml\n---\nlayout: center\n---\n```\n").blocks().isEmpty(),"Ignore code fences");
        check(MetadataRegions.scan("````md\n```\n---\nlayout: center\n---\n````").blocks().isEmpty(),"Nested shorter fence");
        check(MetadataRegions.scan("<!--\n---\nlayout: center\n---\n-->\n").blocks().isEmpty(),"Ignore notes");
        check(MetadataRegions.scan("    ---\nlayout: center\n    ---").blocks().isEmpty(),"Ignore indented code delimiters");
        check(MetadataRegions.scan("---\nlayout center\n---\n# Slide").blocks().size()==1,"Recover missing colon for YAML parser");
        check(MetadataRegions.scan("---\ntheme: default\n").problems().stream().anyMatch(p -> p.message().contains("closing ---")),"Missing frontmatter close");
        check(MetadataRegions.scan("# Slide\n```js\nconst x=1").problems().stream().anyMatch(p -> p.message().contains("fence")),"Missing code fence close");
        check(MetadataRegions.scan("~~~js\nconst x=1\n~~~").problems().isEmpty(),"Closed tilde fence");
        check(MetadataRegions.scan("---\n\n---\n# Slide").blocks().size()==1,"Empty initial headmatter");
        check(MetadataRegions.scan("# Slide\n---\n---\n# Third").blocks().isEmpty(),"Empty slide is not metadata");
        check(completion("---\nth|\n---\n# Slide").prefix().equals("th"),"Partial key completion");
        check(completion("---\n|\n---\n# Slide").key()==null,"Empty headmatter completion");
        check(completion("---\ntheme: de|\n---").key().equals("theme"),"Value completion");
        check(completion("---\nfonts:\n  sans: Ro|\n---").parent().equals("fonts"),"Nested YAML context excludes root fields");
        check(completion("---\ninfo: >\n  layout|\n---")==null,"No completion in block scalar");
        check(completion("---\n# theme: de|\n---")==null,"No completion in YAML comments");
        check(completion("---\ntheme: 'de|fault'\n---")==null,"No plain completion inside quoted value");
        check(completion("# theme|\n")==null,"No completion in Markdown body");
        check(completion("---\ntheme: default\n|---")==null,"No completion on closing delimiter");
        check(completion("---\nlayout: ce|").prefix().equals("ce"),"Completion in unfinished EOF block");
        check(completion("```yaml\n---\nth|\n---\n```")==null,"No completion in code example");
        check(!completion("# First\n---\nla|\n---\n# Next").block().head(),"Per-slide completion scope");
        var crlf=MetadataRegions.scan("\ufeff---\r\ntheme: default\r\n---\r\n# 🐇\r\n");
        check(crlf.blocks().get(0).start()==6,"BOM and CRLF start offset");
        check(crlf.blocks().get(0).end()==22,"CRLF end offset");
        check(MetadataRegions.completion("---\ntheme: default\n---",-1)==null,"Invalid caret is ignored");
        check(SlidevSchema.find("theme",true)!=null && SlidevSchema.find("theme",false)==null,"Deck-only key scope");
        check(SlidevSchema.find("layout",false)!=null,"Common slide key");
        check(SlidevSchema.scalarProblem("hide","true",false)==null,"Boolean");
        check(SlidevSchema.scalarProblem("hide","'true'",false)!=null,"Quoted string is not boolean");
        check(SlidevSchema.scalarProblem("hide","yes",false)!=null,"Reject YAML 1.1 boolean alias");
        check(SlidevSchema.scalarProblem("presenter","'dev'",true)==null,"Quoted mode");
        check(SlidevSchema.scalarProblem("presenter","sometimes",true)!=null,"Invalid mode");
        check(SlidevSchema.scalarProblem("clicks","-1",false)!=null,"Negative click count");
        check(SlidevSchema.scalarProblem("clicks","2.5",false)!=null,"Fractional click count");
        check(SlidevSchema.scalarProblem("clicks","0",false)==null,"Zero click count");
        check(SlidevSchema.scalarProblem("clicks","0x10",false)==null,"YAML hexadecimal number");
        check(SlidevSchema.scalarProblem("clicks","0o10",false)==null,"YAML octal number");
        check(SlidevSchema.scalarProblem("hide","{}",false)!=null,"Mapping is not a boolean");
        check(SlidevSchema.scalarProblem("zoom","0",false)!=null,"Zero zoom");
        check(SlidevSchema.scalarProblem("zoom","1.25",false)==null,"Fractional zoom");
        check(SlidevSchema.scalarProblem("layout","my-layout",false)==null,"Custom theme layout allowed");
        check(SlidevSchema.scalarProblem("customThemeSetting","anything",false)==null,"Custom key allowed");
        check(SlidevSchema.scalarProblem("hide","*sharedFlag",false)==null,"Alias needs resolution, no guessed type error");
        check(SlidevSchema.scalarProblem("colorSchema","sepia",true)!=null,"Known closed enum");
        System.out.println("PASS: "+checks+" metadata, completion and schema checks");
    }
}
