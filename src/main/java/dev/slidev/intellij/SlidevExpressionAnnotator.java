package dev.slidev.intellij;

import com.intellij.lang.annotation.*;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import dev.slidev.intellij.core.ExpressionChecker;
import dev.slidev.intellij.core.MarkupCompletion;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Collect a snapshot under the read lock; run Node only in doAnnotate, outside it. */
public final class SlidevExpressionAnnotator extends ExternalAnnotator<SlidevExpressionAnnotator.Input,SlidevExpressionAnnotator.Output> {
    public record Input(Path entry,String source,String node) {}
    public record Output(Input input,ExpressionChecker.Result result) {}
    private static final Pattern DIRECTIVE=Pattern.compile("(?:\\bv-[\\w-]+(?:[.:][\\w-]+)*|[:@#][\\w.-]+)\\s*=");
    @Override public @Nullable Input collectInformation(@NotNull PsiFile file) {
        if(!SlidevLanguageSupport.accepts(file)) return null;
        var settings=SlidevSettings.get(file.getProject()).getState();
        if(!settings.checkVueExpressions) return null;
        return new Input(Path.of(file.getVirtualFile().getPath()),file.getText(),settings.nodeExecutable);
    }
    @Override public @Nullable Output doAnnotate(Input input) {
        if(input==null) return null;
        ProgressManager.checkCanceled();
        if(input.source().length()>500_000) return new Output(input,new ExpressionChecker.Result(List.of(),"Live expression check skipped: document exceeds 500,000 characters."));
        String template=MarkupCompletion.vueTemplate(input.source());
        if(!template.contains("{{") && !DIRECTIVE.matcher(template).find()) return new Output(input,new ExpressionChecker.Result(List.of(),null));
        try(var resource=getClass().getResourceAsStream("/dev/slidev/intellij/check-expressions.cjs")) {
            if(resource==null) return new Output(input,new ExpressionChecker.Result(List.of(),"Expression checker resource is missing."));
            String script=new String(resource.readAllBytes(),StandardCharsets.UTF_8);
            return new Output(input,ExpressionChecker.check(input.entry(),template,input.node(),script,ProgressManager::checkCanceled));
        } catch(IOException ex) {return new Output(input,new ExpressionChecker.Result(List.of(),ex.getMessage()));}
    }
    @Override public void apply(@NotNull PsiFile file,Output output,@NotNull AnnotationHolder holder) {
        if(output==null || !file.isValid() || file.getProject().isDisposed()) return;
        var settings=SlidevSettings.get(file.getProject()).getState();
        if(!settings.checkVueExpressions || !settings.nodeExecutable.equals(output.input().node()) || !file.getText().equals(output.input().source())) return;
        int length=output.input().source().length();
        if(length==0) return;
        if(output.result().unavailable()!=null) {
            holder.newAnnotation(HighlightSeverity.WEAK_WARNING,output.result().unavailable()+" (Settings > Slidev: Check Vue expressions while typing)")
                .range(new TextRange(0,1)).create();
            return;
        }
        for(var diagnostic:output.result().diagnostics()) {
            int start=Math.min(diagnostic.start(),length-1),end=Math.min(length,Math.max(start+1,diagnostic.end()));
            holder.newAnnotation(HighlightSeverity.ERROR,"Vue: "+diagnostic.message()).range(new TextRange(start,end)).create();
        }
    }
}
