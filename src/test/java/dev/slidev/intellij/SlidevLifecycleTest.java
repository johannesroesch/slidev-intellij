package dev.slidev.intellij;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/** Actual SDK lifecycle checks, without a native JCEF window. */
public final class SlidevLifecycleTest extends BasePlatformTestCase {
    public void testAllRegisteredExtensionPointsSupportDynamicUnloading() {
        for(String name:new String[]{"annotator","externalAnnotator","gotoDeclarationHandler","completion.contributor","fileEditorProvider","projectConfigurable"}) {
            var point=com.intellij.openapi.application.ApplicationManager.getApplication().getExtensionArea().getExtensionPoint("com.intellij."+name);
            assertTrue("Non-dynamic extension point: "+name,point.isDynamic());
        }
    }
    public void testUnloadDisposesDetachedEditorAndPreservesDocument() {
        var file=myFixture.configureByText("slides.md","---\ntheme: default\n---\n# Unsaved deck\n").getVirtualFile();
        var editor=new SlidevEditor(getProject(),file);
        var runtime=SlidevRuntime.get(getProject());
        var document=editor.getEditor().getDocument();
        assertTrue(runtime.editors().contains(editor));
        var descriptor=PluginManagerCore.getPlugin(PluginId.getId("dev.slidev.editor"));
        assertNotNull("Plugin must be loaded in SDK test sandbox",descriptor);
        new SlidevPluginListener().beforePluginUnload(descriptor,false);
        assertTrue(runtime.isClosing());
        assertTrue(runtime.editors().isEmpty());
        assertTrue(Disposer.isDisposed(editor));
        assertEquals("---\ntheme: default\n---\n# Unsaved deck\n",document.getText());
        assertSame(document,FileDocumentManager.getInstance().getCachedDocument(file));
    }
}
