package dev.slidev.intellij;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import dev.slidev.intellij.core.QuickFixes;
import org.jetbrains.annotations.NotNull;

final class SlidevTextFix extends BaseIntentionAction {
    private final SmartPsiElementPointer<PsiFile> pointer;private final String snapshot;private final QuickFixes.Edit edit;
    SlidevTextFix(PsiFile file,String snapshot,QuickFixes.Edit edit) {pointer=SmartPointerManager.getInstance(file.getProject()).createSmartPsiElementPointer(file);this.snapshot=snapshot;this.edit=edit;}
    @Override public @NotNull String getText(){return edit.text();}
    @Override public @NotNull String getFamilyName(){return "Slidev quick fixes";}
    @Override public boolean startInWriteAction(){return true;}
    @Override public boolean isAvailable(@NotNull Project project,Editor editor,PsiFile file) {
        var host=pointer.getElement();if(host==null || !host.isWritable())return false;
        var document=host.getViewProvider().getDocument();return document!=null && document.getText().equals(snapshot);
    }
    @Override public void invoke(@NotNull Project project,Editor editor,PsiFile file) {
        if(!isAvailable(project,editor,file))return;var document=pointer.getElement().getViewProvider().getDocument();
        document.replaceString(edit.start(),edit.end(),edit.replacement());
    }
}
