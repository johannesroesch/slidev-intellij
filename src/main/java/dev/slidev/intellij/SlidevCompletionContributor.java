package dev.slidev.intellij;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.DumbAware;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.progress.ProgressManager;
import dev.slidev.intellij.core.MetadataRegions;
import dev.slidev.intellij.core.SlidevSchema;
import dev.slidev.intellij.core.MarkupCompletion;
import dev.slidev.intellij.core.ProjectCompletions;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/** Completion in the original Markdown document, independent of Ultimate/Vue services. */
public final class SlidevCompletionContributor extends CompletionContributor implements DumbAware {
    @Override public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        if(parameters.getCompletionType()!=CompletionType.BASIC) return;
        var original=parameters.getOriginalFile();
        var injection=InjectedLanguageManager.getInstance(original.getProject());
        var host=injection.getTopLevelFile(original);
        if(!SlidevLanguageSupport.accepts(host)) return;
        var hostDocument=host.getViewProvider().getDocument();
        if(hostDocument==null) return;
        String source=hostDocument.getText();
        int offset=original==host ? parameters.getOffset() : injection.injectedToHost(original,parameters.getOffset());
        var context=MetadataRegions.completion(source,offset);
        if(context==null) {
            var markup=MarkupCompletion.context(source,offset);
            if(markup!=null) completeMarkup(result,markup,host.getVirtualFile().getPath(),source);
            return;
        }
        var matches=result.withPrefixMatcher(context.prefix());
        if(context.key()==null) {
            for(var field:SlidevSchema.fields(context.parent(),context.block().head())) {
                // The edited key may already have a colon/value. Offer it, preserving the value.
                matches.addElement(LookupElementBuilder.create(field.name())
                    .withTypeText(context.parent().isEmpty()?(field.headOnly()?"Slidev headmatter":"Slidev frontmatter"):"Slidev "+context.parent())
                    .withTailText("  "+field.description(),true)
                    .withInsertHandler((insertion,item) -> {
                        if(insertion.getCompletionChar()==':') insertion.setAddCompletionChar(false);
                        int tail=insertion.getTailOffset(), next=tail;
                        var document=insertion.getDocument();
                        var text=document.getCharsSequence();
                        while(next<text.length() && (text.charAt(next)==' ' || text.charAt(next)=='\t')) next++;
                        if(next>=text.length() || text.charAt(next)!=':') {
                            document.insertString(tail,": ");
                            insertion.getEditor().getCaretModel().moveToOffset(tail+2);
                        }
                    }));
            }
        } else {
            var field=SlidevSchema.find(context.parent(),context.key(),context.block().head());
            Map<String,String> values=new LinkedHashMap<>();
            if(field!=null) for(String value:field.values()) values.put(value,"Slidev "+field.name());
            if(context.key().equals("layout") && (context.parent().isEmpty() || context.parent().equals("defaults"))) {
                values.putAll(ProjectCompletions.discover(Path.of(host.getVirtualFile().getPath()),source,ProgressManager::checkCanceled).layouts());
            }
            values.forEach((value,origin) -> matches.addElement(LookupElementBuilder.create(value).withTypeText(origin)));
        }
    }
    private void completeMarkup(CompletionResultSet result,MarkupCompletion.Context context,String path,String source) {
        var matches=result.withPrefixMatcher(context.prefix());
        if(context.kind()==MarkupCompletion.Kind.ATTRIBUTE) {
            for(var attribute:MarkupCompletion.attributes(context.tag())) {
                String name=attribute.name();
                if(context.attributes().stream().anyMatch(existing -> existing.replaceFirst("^:", "").equals(name.replaceFirst("^:", "")))) continue;
                matches.addElement(LookupElementBuilder.create(name).withLookupString(name.replaceFirst("^:", "")).withTypeText("Slidev attribute")
                    .withTailText("  "+attribute.description(),true)
                    .withInsertHandler((insertion,item) -> insertMarkup(insertion,context.kind(),name,attribute.value())));
            }
            return;
        }
        Map<String,String> tags=new LinkedHashMap<>();
        MarkupCompletion.COMPONENTS.forEach(component -> tags.put(component.name(),component.description()));
        tags.putAll(ProjectCompletions.discover(Path.of(path),source,ProgressManager::checkCanceled).components());
        tags.forEach((name,description) -> matches.addElement(LookupElementBuilder.create(name)
            .withTypeText("Slidev component").withTailText("  "+description,true)
            .withInsertHandler((insertion,item) -> insertMarkup(insertion,context.kind(),name,false))));
    }
    private static void insertMarkup(InsertionContext insertion,MarkupCompletion.Kind kind,String name,boolean value) {
        int tail=insertion.getTailOffset();
        var document=insertion.getDocument();
        String after=document.getText().substring(tail);
        if(kind==MarkupCompletion.Kind.TAG && insertion.getCompletionChar()==' ') {
            insertion.setAddCompletionChar(false);
            if(!after.startsWith(" ")) document.insertString(tail," ");
            insertion.getEditor().getCaretModel().moveToOffset(tail+1);
            return;
        }
        var edit=MarkupCompletion.insertion(after,kind,name,value);
        if(insertion.getCompletionChar()=='>') insertion.setAddCompletionChar(false);
        if(value && insertion.getCompletionChar()=='=') insertion.setAddCompletionChar(false);
        if(value && insertion.getCompletionChar()==' ') insertion.setAddCompletionChar(false);
        if(!edit.text().isEmpty()) {
            document.insertString(tail,edit.text());
            insertion.getEditor().getCaretModel().moveToOffset(tail+edit.caret());
        } else if(kind!=MarkupCompletion.Kind.ATTRIBUTE && insertion.getCompletionChar()=='>' && after.startsWith(">")) {
            insertion.getEditor().getCaretModel().moveToOffset(tail+1);
        }
    }
}
