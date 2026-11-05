package dev.slidev.intellij;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.*;
import java.nio.file.*;
import org.jetbrains.annotations.NotNull;

final class SlidevCreateSourceFix extends BaseIntentionAction {
    private final SmartPsiElementPointer<PsiFile> pointer;private final String snapshot;private final Path target;
    SlidevCreateSourceFix(PsiFile file,String snapshot,Path target){pointer=SmartPointerManager.getInstance(file.getProject()).createSmartPsiElementPointer(file);this.snapshot=snapshot;this.target=target;}
    @Override public @NotNull String getText(){return "Create slide file '"+target+"'";}
    @Override public @NotNull String getFamilyName(){return "Slidev quick fixes";}
    @Override public boolean startInWriteAction(){return true;}
    @Override public boolean isAvailable(@NotNull Project project,Editor editor,PsiFile file) {
        var host=pointer.getElement();return host!=null && host.isWritable() && host.getText().equals(snapshot) && !Files.exists(target);
    }
    @Override public void invoke(@NotNull Project project,Editor editor,PsiFile file) {
        if(!isAvailable(project,editor,file))return;
        try {
            var parent=VfsUtil.createDirectories(target.getParent().toString());
            if(parent.findChild(target.getFileName().toString())!=null)return;
            var created=parent.createChildData(this,target.getFileName().toString());VfsUtil.saveText(created,"# New slide\n");
        } catch(java.io.IOException ex){throw new com.intellij.util.IncorrectOperationException(ex);}
    }
}
