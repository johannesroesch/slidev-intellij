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
import dev.slidev.intellij.core.QuickFixes;
import dev.slidev.intellij.core.ProjectCompletions;
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
        Path userRoot=Path.of(SlidevDeckSupport.entryFor(file.getProject(),file.getVirtualFile()).getPath()).getParent();
        for(var resolution:ProjectReferences.analyze(Path.of(file.getVirtualFile().getPath()),source,userRoot,ProgressManager::checkCanceled)) {
            if(resolution.warning()==null)continue;
            var reference=resolution.reference();
            var annotation=holder.newAnnotation(reference.kind()==ProjectReferences.Kind.LAYOUT?HighlightSeverity.WEAK_WARNING:HighlightSeverity.WARNING,resolution.warning()).range(new TextRange(reference.start(),reference.end()));
            if(reference.kind()==ProjectReferences.Kind.LAYOUT) {
                var available=new java.util.LinkedHashSet<String>(SlidevSchema.find("layout",false).values());available.add("fact");available.add("full");
                available.addAll(ProjectCompletions.discover(Path.of(file.getVirtualFile().getPath()),source,ProgressManager::checkCanceled).layouts().keySet());
                for(var fix:QuickFixes.layouts(source,reference,available))annotation.withFix(new SlidevTextFix(file,source,fix));
            }
            if(reference.kind()==ProjectReferences.Kind.IMPORT)ProjectReferences.missingSourceTarget(Path.of(file.getVirtualFile().getPath()),reference,userRoot).ifPresent(target -> annotation.withFix(new SlidevCreateSourceFix(file,source,target)));
            annotation.create();
        }
        for(var problem:scan.problems()) {
            var annotation=holder.newAnnotation(HighlightSeverity.WARNING,problem.message()).range(new TextRange(problem.start(),problem.end()));
            QuickFixes.closing(source,problem).ifPresent(edit -> annotation.withFix(new SlidevTextFix(file,source,edit)));annotation.create();
        }
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
                        if(problem!=null) {
                            int valueStart=block.start()+value.getTextOffset(),valueEnd=block.start()+value.getTextRange().getEndOffset();
                            var annotation=holder.newAnnotation(HighlightSeverity.WARNING,name+": "+problem).range(new TextRange(valueStart,valueEnd));
                            for(var edit:QuickFixes.values(source,valueStart,valueEnd,field))annotation.withFix(new SlidevTextFix(file,source,edit));annotation.create();
                        }
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
