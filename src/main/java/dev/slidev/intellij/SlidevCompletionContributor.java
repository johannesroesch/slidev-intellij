package dev.slidev.intellij;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.DumbAware;
import com.intellij.lang.injection.InjectedLanguageManager;
import dev.slidev.intellij.core.MetadataRegions;
import dev.slidev.intellij.core.SlidevSchema;
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
        if(context==null) return;
        var matches=result.withPrefixMatcher(context.prefix());
        if(context.key()==null) {
            for(var field:SlidevSchema.fields(context.block().head())) {
                // The edited key may already have a colon/value. Offer it, preserving the value.
                matches.addElement(LookupElementBuilder.create(field.name())
                    .withTypeText(field.headOnly()?"Slidev headmatter":"Slidev frontmatter")
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
            var field=SlidevSchema.find(context.key(),context.block().head());
            if(field!=null) for(String value:field.values()) matches.addElement(LookupElementBuilder.create(value).withTypeText("Slidev "+field.name()));
        }
    }
}
