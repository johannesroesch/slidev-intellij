package dev.slidev.intellij;

import com.intellij.lang.annotation.*;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import dev.slidev.intellij.core.MetadataRegions;
import dev.slidev.intellij.core.SlidevSchema;
import dev.slidev.intellij.core.ProjectReferences;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.*;
import java.util.HashSet;

/** Native YAML lexing/parsing on nonphysical fragments, diagnostics mapped to Markdown offsets. */
public final class SlidevAnnotator implements Annotator, DumbAware {
    @Override public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if(!(element instanceof PsiFile file) || !SlidevLanguageSupport.accepts(file)) return;
        String source=file.getText();
        var scan=MetadataRegions.scan(source);
        for(var resolution:ProjectReferences.analyze(Path.of(file.getVirtualFile().getPath()),source,ProgressManager::checkCanceled)) {
            if(resolution.warning()==null)continue;
            var reference=resolution.reference();
            report(holder,source.length(),reference.start(),reference.end(),resolution.warning(),
                reference.kind()==ProjectReferences.Kind.LAYOUT?HighlightSeverity.WEAK_WARNING:HighlightSeverity.WARNING);
        }
        for(var problem:scan.problems()) report(holder,source.length(),problem.start(),problem.end(),problem.message(),HighlightSeverity.WARNING);
        var type=SlidevLanguageSupport.yamlType();
        var highlighter=SyntaxHighlighterFactory.getSyntaxHighlighter(type,file.getProject(),null);
        for(var block:scan.blocks()) {
            ProgressManager.checkCanceled();
            // Delimiters override Markdown's thematic-break/header interpretation.
            color(holder,block.marker(),Math.min(source.length(),block.marker()+3),DefaultLanguageHighlighterColors.METADATA);
            if(block.closed()) color(holder,block.end(),Math.min(source.length(),block.end()+3),DefaultLanguageHighlighterColors.METADATA);
            String yaml=source.substring(block.start(),block.end());
            if(highlighter!=null) {
                var lexer=highlighter.getHighlightingLexer(); lexer.start(yaml);
                while(lexer.getTokenType()!=null) {
                    ProgressManager.checkCanceled();
                    var keys=highlighter.getTokenHighlights(lexer.getTokenType());
                    if(keys.length>0) color(holder,block.start()+lexer.getTokenStart(),block.start()+lexer.getTokenEnd(),keys[0]);
                    lexer.advance();
                }
            }
            if(yaml.isBlank()) continue;
            var parsed=PsiFileFactory.getInstance(file.getProject()).createFileFromText("slidev-frontmatter.yaml",type,yaml);
            for(var error:PsiTreeUtil.findChildrenOfType(parsed,PsiErrorElement.class)) {
                var range=error.getTextRange();
                report(holder,source.length(),block.start()+range.getStartOffset(),block.start()+range.getEndOffset(),"YAML: "+error.getErrorDescription(),HighlightSeverity.ERROR);
            }
            if(!(parsed instanceof YAMLFile yamlFile)) continue;
            for(var document:yamlFile.getDocuments()) {
                var root=document.getTopLevelValue();
                if(root!=null && !(root instanceof YAMLMapping)) report(holder,source.length(),block.start()+root.getTextOffset(),block.start()+root.getTextRange().getEndOffset(),"Slidev frontmatter must be a YAML mapping",HighlightSeverity.ERROR);
            }
            // Duplicate keys are invalid at every nesting level; arbitrary custom keys are legal.
            for(var mapping:PsiTreeUtil.findChildrenOfType(parsed,YAMLMapping.class)) {
                var names=new HashSet<String>();
                boolean topLevel=mapping.getParent() instanceof YAMLDocument;
                for(var pair:mapping.getKeyValues()) {
                    var key=pair.getKey(); if(key==null) continue;
                    int start=block.start()+key.getTextOffset(), end=block.start()+key.getTextRange().getEndOffset();
                    String name=pair.getKeyText();
                    if(!names.add(name)) report(holder,source.length(),start,end,"Duplicate YAML key: "+name,HighlightSeverity.WARNING);
                    if(!topLevel || pair.getValue()==null) continue;
                    var field=SlidevSchema.find(name,block.head());
                    if(field!=null) color(holder,start,end,DefaultLanguageHighlighterColors.KEYWORD);
                    if(field!=null && !field.rule().isEmpty()) {
                        var value=pair.getValue();
                        String problem=SlidevSchema.scalarProblem(name,value.getText(),block.head());
                        if(problem!=null) report(holder,source.length(),block.start()+value.getTextOffset(),block.start()+value.getTextRange().getEndOffset(),name+": "+problem,HighlightSeverity.WARNING);
                    }
                }
            }
        }
    }
    private static void color(AnnotationHolder holder,int start,int end,com.intellij.openapi.editor.colors.TextAttributesKey key) {
        if(end>start) holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(new TextRange(start,end)).textAttributes(key).create();
    }
    private static void report(AnnotationHolder holder,int length,int start,int end,String message,HighlightSeverity severity) {
        if(length==0) return;
        start=Math.max(0,Math.min(start,length-1)); end=Math.min(length,Math.max(start+1,end));
        holder.newAnnotation(severity,message).range(new TextRange(start,end)).create();
    }
}
