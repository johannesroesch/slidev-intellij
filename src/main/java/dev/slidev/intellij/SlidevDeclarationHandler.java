package dev.slidev.intellij;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.*;
import dev.slidev.intellij.core.ProjectReferences;
import java.nio.file.Path;
import org.jetbrains.annotations.Nullable;

/** Resolves host Markdown offsets, including existing YAML/HTML injection. */
public final class SlidevDeclarationHandler implements GotoDeclarationHandler,DumbAware {
    @Override public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement element,int offset,Editor editor) {
        if(element==null) return null;
        var original=element.getContainingFile();
        if(original==null) return null;
        var injection=InjectedLanguageManager.getInstance(original.getProject());
        var host=injection.getTopLevelFile(original);
        if(!SlidevLanguageSupport.accepts(host)) return null;
        int hostOffset=original==host?offset:injection.injectedToHost(original,offset);
        Path userRoot=Path.of(SlidevDeckSupport.entryFor(host.getProject(),host.getVirtualFile()).getPath()).getParent();
        var resolution=ProjectReferences.at(Path.of(host.getVirtualFile().getPath()),host.getText(),hostOffset,userRoot,ProgressManager::checkCanceled);
        if(resolution==null && hostOffset>0) resolution=ProjectReferences.at(Path.of(host.getVirtualFile().getPath()),host.getText(),hostOffset-1,userRoot,ProgressManager::checkCanceled);
        if(resolution==null || resolution.target()==null) return null;
        var target=LocalFileSystem.getInstance().findFileByPath(resolution.target().toString().replace('\\','/'));
        if(target==null) return null;
        var psi=PsiManager.getInstance(host.getProject()).findFile(target);
        return psi==null?null:new PsiElement[]{psi};
    }
}
