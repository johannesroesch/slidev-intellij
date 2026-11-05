package dev.slidev.intellij.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

public final class ExpressionTest {
    private static int checks;
    private static void check(boolean value,String label) {if(!value)throw new AssertionError(label);checks++;}
    private static String encoded(String text) {return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));}
    public static void main(String[] args) throws Exception {
        String source="\ufeff---\r\ntheme: default\r\ninfo: '{{ 1 + }}'\r\n---\r\n# 🐇\r\n{{ 1 + }}\r\n";
        String template=MarkupCompletion.vueTemplate(source);
        check(template.length()==source.length(),"UTF-16 length preserved");
        check(template.lastIndexOf("{{ 1 + }}")==source.lastIndexOf("{{ 1 + }}"),"BOM CRLF Unicode offsets preserved");
        check(template.indexOf("{{")==source.lastIndexOf("{{"),"YAML expression excluded");
        for(int i=0;i<source.length();i++) if(source.charAt(i)=='\r'||source.charAt(i)=='\n') check(source.charAt(i)==template.charAt(i),"Line endings preserved");
        check(!MarkupCompletion.vueTemplate("```vue\n{{ 1 + }}\n```").contains("{{"),"Code sample excluded");
        check(!MarkupCompletion.vueTemplate("`{{ 1 + }}`").contains("{{"),"Inline code excluded");
        check(!MarkupCompletion.vueTemplate("<!-- {{ 1 + }} -->").contains("{{"),"Notes excluded");
        check(!MarkupCompletion.vueTemplate("    {{ 1 + }}").contains("{{"),"Indented Markdown code excluded");
        check(MarkupCompletion.vueTemplate("<div>\n    {{ 1 + }}\n</div>").contains("{{"),"Indented content inside HTML preserved");
        String binding="<div :title=\"`Hello ${name}`\" v-click=\"1 +\">X</div>";
        check(MarkupCompletion.vueTemplate(binding).equals(binding),"Template literal in bound attribute preserved");
        String interpolation="{{ `Hello ${name}` }}";
        check(MarkupCompletion.vueTemplate(interpolation).equals(interpolation),"Template literal in interpolation preserved");
        check(!MarkupCompletion.vueTemplate("<script setup>const x='{{ 1 + }}'</script>").contains("{{"),"Script text excluded");
        check(!MarkupCompletion.vueTemplate("<style>/* {{ 1 + }} */</style>").contains("{{"),"Style text excluded");
        String prefix="slidev-expression\t";
        check(ExpressionChecker.parse(prefix+"ready\n",10).unavailable()==null,"Clean response");
        var result=ExpressionChecker.parse("unrelated warning\n"+prefix+"ready\n"+prefix+"error\t3\t3\t"+encoded("Unexpected token 🐇")+"\n",10);
        check(result.diagnostics().size()==1 && result.diagnostics().get(0).start()==3,"Compiler offset retained");
        check(result.diagnostics().get(0).message().endsWith("🐇"),"UTF-8 protocol message");
        check(ExpressionChecker.parse(prefix+"unavailable\t"+encoded("missing dependency"),10).unavailable().equals("missing dependency"),"Missing dependency is not a clean check");
        check(ExpressionChecker.parse("",10).unavailable()!=null,"Missing response");
        check(ExpressionChecker.parse(prefix+"ready\n"+prefix+"error\t-1\t2\t"+encoded("bad"),10).unavailable()!=null,"Reject negative range");
        check(ExpressionChecker.parse(prefix+"ready\n"+prefix+"error\t1\t20\t"+encoded("bad"),10).unavailable()!=null,"Reject oversized range");
        check(ExpressionChecker.parse(prefix+"ready\n"+prefix+"error\t1\t2\t%%%",10).unavailable()!=null,"Reject invalid encoding");
        check(ExpressionChecker.parse(prefix+"ready\n"+prefix+"error\t1",10).unavailable()!=null,"Malformed response is not success");
        if(args.length==2) {
            String script;
            try(var input=ExpressionTest.class.getResourceAsStream("/dev/slidev/intellij/check-expressions.cjs")) {
                if(input==null)throw new IllegalStateException("Checker resource missing");
                script=new String(input.readAllBytes(),StandardCharsets.UTF_8);
            }
            var actual=ExpressionChecker.check(Path.of(args[1]),template,args[0],script,() -> {});
            check(actual.unavailable()==null,"Actual Node compiler subprocess available: "+actual.unavailable());
            check(actual.diagnostics().size()==1,"Actual subprocess returns exactly one non-masked error");
            check(actual.diagnostics().get(0).start()==source.lastIndexOf("1 +"),"Actual compiler diagnostic maps to original Markdown");
            check(ExpressionChecker.check(Path.of(args[1]),"{{ x }}","/missing/slidev-node",script,() -> {}).unavailable()!=null,"Missing executable reported");
            AtomicInteger polls=new AtomicInteger();boolean canceled=false;
            try {ExpressionChecker.check(Path.of(args[1]),"{{ x }}",args[0],"setInterval(()=>{},1000)",() -> {if(polls.incrementAndGet()>=3)throw new IllegalStateException("canceled");});}
            catch(IllegalStateException expected) {canceled=true;}
            check(canceled,"Cancellation propagates and kills subprocess");
            var timeout=ExpressionChecker.check(Path.of(args[1]),"{{ x }}",args[0],"setInterval(()=>{},1000)",() -> {});
            check(timeout.unavailable()!=null && timeout.unavailable().contains("timed out"),"Hung checker times out");
        }
        System.out.println("PASS: "+checks+" expression masking/protocol"+(args.length==2?"/subprocess":"")+" checks");
    }
}
