package dev.slidev.intellij;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.psi.PsiFile;

final class SlidevLanguageSupport {
    static boolean accepts(PsiFile file) {
        var virtual=file.getVirtualFile();
        return virtual!=null && SlidevDetection.accepts(file.getProject(),virtual);
    }
    static FileType yamlType() {
        return FileTypeManager.getInstance().getFileTypeByExtension("yaml");
    }
    private SlidevLanguageSupport() {}
}
