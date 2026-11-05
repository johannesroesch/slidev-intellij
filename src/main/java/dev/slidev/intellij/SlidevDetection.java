package dev.slidev.intellij;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

final class SlidevDetection {
    private static final Pattern SIGNAL = Pattern.compile("(?m)^\\s*(theme|slidev|drawings|presenter|transition)\\s*:");
    static boolean isMarkdown(VirtualFile file) {
        String ext = file.getExtension();
        return ext != null && (ext.toLowerCase(Locale.ROOT).equals("md") || ext.toLowerCase(Locale.ROOT).equals("markdown"));
    }
    static boolean accepts(Project project, VirtualFile file) {
        if (!file.isInLocalFileSystem() || !isMarkdown(file)) return false;
        if (SlidevSettings.get(project).getState().entryFiles.contains(file.getUrl())) return true;
        String text = prefix(file);
        if (text.contains("<!-- slidev -->")) return true;
        if (text.replace("\ufeff", "").startsWith("---")) {
            int close = text.indexOf("\n---", 3);
            if (close > 0 && SIGNAL.matcher(text.substring(3, close)).find()) return true;
        }
        if (file.getName().equalsIgnoreCase("slides.md")) {
            for (VirtualFile dir=file.getParent(); dir != null; dir=dir.getParent()) {
                VirtualFile pkg=dir.findChild("package.json");
                if (pkg != null && prefix(pkg).contains("\"@slidev/cli\"")) return true;
            }
        }
        return false;
    }
    private static String prefix(VirtualFile file) {
        try(var in=file.getInputStream()) {
            return new String(in.readNBytes(65536), StandardCharsets.UTF_8);
        } catch(IOException ignored) { return ""; }
    }
}
