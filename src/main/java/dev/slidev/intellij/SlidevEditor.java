package dev.slidev.intellij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.jcef.*;
import dev.slidev.intellij.core.DeckParser;
import dev.slidev.intellij.core.BuildDiagnostics;
import dev.slidev.intellij.core.SlideNavigation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeListener;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class SlidevEditor extends UserDataHolderBase implements TextEditor {
    private static final Logger LOG=Logger.getInstance(SlidevEditor.class);
    private final TextEditor delegate;
    private final VirtualFile file;
    private final Project project;
    private boolean selected;
    private final JPanel panel=new JPanel(new BorderLayout());
    private enum ViewMode { CODE, SPLIT, PREVIEW }
    private ViewMode viewMode=ViewMode.SPLIT;
    private final SlidevView viewHost;
    private final JComponent sourceComponent, previewComponent;
    private final JPanel browserHost=new JPanel(new BorderLayout());
    private final JLabel status=new JLabel("Preview starts when this tab is selected");
    private final JTextArea log=new JTextArea(8,50);
    private final JScrollPane debugConsole=new JScrollPane(log);
    private final JComboBox<DeckParser.Slide> outline=new JComboBox<>();
    private boolean syncNavigation;
    private final JButton checkBuild=new JButton("Check build");
    private final JLabel buildStatus=new JLabel("Build not checked");
    private final DefaultListModel<BuildIssue> issues=new DefaultListModel<>();
    private final JList<BuildIssue> issueList=new JList<>(issues);
    private final List<RangeHighlighter> buildHighlights=new ArrayList<>();
    private SlidevBuild build;
    private record BuildIssue(String label, int offset) {
        @Override public String toString() { return label; }
    }
    private final List<RangeHighlighter> decorations=new ArrayList<>();
    private final List<FoldRegion> folds=new ArrayList<>();
    private final Timer changeTimer;
    private final Timer caretTimer;
    private final Timer navigationTimeout;
    private JBCefJSQuery navigationQuery;
    private boolean navigationReady, applyingPreview, caretNavigationPending;
    private int previewSlide, requestedSlide;
    private long navigationRequest;
    private JBCefBrowser browser;
    private JBCefJSQuery escapeQuery;
    private SlidevServer server;
    private String baseUrl;
    private JFrame presentation;
    private volatile boolean disposed;
    private boolean rebuilding;
    private long editRevision;

    public SlidevEditor(Project project,VirtualFile file) {
        this.file=file;this.project=project;
        delegate=(TextEditor)TextEditorProvider.getInstance().createEditor(project,file);
        syncNavigation=SlidevSettings.get(project).getState().syncNavigation;
        debugConsole.setVisible(SlidevSettings.get(project).getState().showDebugConsole);
        project.getMessageBus().connect(this).subscribe(SlidevSettings.TOPIC,
            (debug,sync) -> onEdt(() -> applyEditorOptions(debug,sync)));
        sourceComponent=delegate.getComponent();previewComponent=previewPanel();
        viewHost=new SlidevView(sourceComponent,previewComponent);
        panel.add(viewHost,BorderLayout.CENTER);
        panel.add(viewToolbar(),BorderLayout.NORTH);
        panel.addHierarchyListener(event -> {
            if((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED)!=0) onEdt(this::startVisiblePreview);
        });
        changeTimer=new Timer(700,event -> {
            long revision=editRevision;
            // Swing Timer callbacks are not write-safe IntelliJ transactions.
            // Re-enter via Application.invokeLater with explicit non-modal state.
            onEdt(() -> {
                if(revision!=editRevision) return;
                updateStructure();
                if(server!=null && server.isAlive()) FileDocumentManager.getInstance().saveDocument(getEditor().getDocument());
            });
        });
        changeTimer.setRepeats(false);
        caretTimer=new Timer(150,event -> onEdt(this::handleCaretTimer));
        caretTimer.setRepeats(false);
        navigationTimeout=new Timer(3000,event -> {
            long request=navigationRequest;
            onEdt(() -> {
                if(request!=navigationRequest || requestedSlide==0) return;
                int number=requestedSlide;requestedSlide=0;
                status.setText("Navigation to slide "+number+" was not confirmed. See log.");
                log.append("[Navigation] No confirmation for slide "+number+"\n");
            });
        });
        navigationTimeout.setRepeats(false);
        getEditor().getCaretModel().addCaretListener(new CaretListener() {
            @Override public void caretPositionChanged(@NotNull CaretEvent event) {
                if(!applyingPreview && syncNavigation && presentation==null) {
                    caretNavigationPending=true;caretTimer.restart();
                }
            }
        });
        getEditor().getDocument().addDocumentListener(new DocumentListener() {
            @Override public void documentChanged(@NotNull DocumentEvent event) {
                editRevision++;
                changeTimer.restart();
                if(syncNavigation && presentation==null) { caretNavigationPending=true;caretTimer.restart(); }
                onEdt(() -> { cancelBuild();clearBuildIssues();buildStatus.setText("Document changed. Check build again."); });
            }
        },this);
        checkBuild.addActionListener(event -> onEdt(() -> { if(build!=null) cancelBuild();else checkBuild(project); }));
        issueList.addListSelectionListener(event -> {
            if(event.getValueIsAdjusting()) return;
            var issue=issueList.getSelectedValue();
            if(issue==null || issue.offset()<0) return;
            getEditor().getCaretModel().moveToOffset(issue.offset());
            getEditor().getScrollingModel().scrollToCaret(ScrollType.CENTER);
            getEditor().getContentComponent().requestFocusInWindow();
        });
        outline.addActionListener(event -> {
            if(rebuilding) return;
            var slide=(DeckParser.Slide)outline.getSelectedItem();
            if(slide==null) return;
            getEditor().getCaretModel().moveToOffset(slide.start());
            getEditor().getScrollingModel().scrollToCaret(ScrollType.CENTER);
            requestPreviewSlide(slide.number());
        });
        updateStructure();
    }
    private JComponent viewToolbar() {
        JPanel toolbar=new JPanel(new BorderLayout());
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT,4,2));actions.add(checkBuild);
        JPanel modes=new JPanel(new FlowLayout(FlowLayout.RIGHT,2,2));
        ButtonGroup group=new ButtonGroup();
        for(ViewMode mode:ViewMode.values()) {
            JToggleButton button=new JToggleButton(viewIcon(mode));
            String title=switch(mode) { case CODE -> "Code";case SPLIT -> "Split";case PREVIEW -> "Preview"; };
            button.setToolTipText(title);button.getAccessibleContext().setAccessibleName(title);
            button.setSelected(mode==viewMode);group.add(button);modes.add(button);
            button.addActionListener(event -> onEdt(() -> setViewMode(mode)));
        }
        toolbar.add(actions,BorderLayout.WEST);toolbar.add(modes,BorderLayout.EAST);
        return toolbar;
    }
    private static Icon viewIcon(ViewMode mode) {
        return new Icon() {
            @Override public int getIconWidth() { return 20; }
            @Override public int getIconHeight() { return 20; }
            @Override public void paintIcon(Component component,Graphics graphics,int x,int y) {
                Graphics2D g=(Graphics2D)graphics.create();
                try {
                    g.translate(x,y);g.setColor(JBColor.foreground());
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setStroke(new BasicStroke(1.4f));
                    if(mode!=ViewMode.PREVIEW) {
                        int end=mode==ViewMode.SPLIT?8:17;
                        for(int row=4;row<=16;row+=4) g.drawLine(2,row,end,row);
                    }
                    if(mode!=ViewMode.CODE) {
                        int left=mode==ViewMode.SPLIT?11:3;
                        g.drawRoundRect(left,2,17-left,16,2,2);
                        g.drawLine(left+2,13,14,8);g.drawLine(14,8,16,11);
                    }
                } finally { g.dispose(); }
            }
        };
    }
    private void setViewMode(ViewMode mode) {
        if(mode==viewMode) return;
        viewMode=mode;viewHost.setMode(SlidevView.Mode.valueOf(mode.name()));
    }
    private void applyEditorOptions(boolean debug, boolean sync) {
        debugConsole.setVisible(debug);
        boolean changed=syncNavigation!=sync;
        syncNavigation=sync;
        if(changed) {
            caretTimer.stop();navigationTimeout.stop();caretNavigationPending=false;requestedSlide=0;navigationRequest++;
            if(syncNavigation) syncPreviewToCaret();
        }
        panel.revalidate();panel.repaint();
        if(server==null) startVisiblePreview();
    }
    private JComponent previewPanel() {
        JPanel preview=new JPanel(new BorderLayout(6,6));
        JPanel top=new JPanel(new BorderLayout());top.add(outline,BorderLayout.CENTER);top.add(status,BorderLayout.SOUTH);
        preview.add(top,BorderLayout.NORTH);
        browserHost.add(new JLabel("Slidev preview starts automatically.",SwingConstants.CENTER));
        preview.add(browserHost,BorderLayout.CENTER);
        log.setEditable(false);log.setLineWrap(true);
        JPanel bottom=new JPanel(new BorderLayout());
        JPanel diagnostics=new JPanel(new BorderLayout());
        diagnostics.add(buildStatus,BorderLayout.NORTH);
        issueList.setVisibleRowCount(3);
        diagnostics.add(new JScrollPane(issueList),BorderLayout.CENTER);
        bottom.add(diagnostics,BorderLayout.NORTH);
        bottom.add(debugConsole,BorderLayout.CENTER);
        preview.add(bottom,BorderLayout.SOUTH);
        return preview;
    }
    private void handleCaretTimer() {
        if(changeTimer.isRunning()) { caretTimer.restart();return; }
        caretNavigationPending=false;syncPreviewToCaret();
    }
    private void syncPreviewToCaret() {
        if(!syncNavigation || presentation!=null || browser==null || baseUrl==null) return;
        String source=getEditor().getDocument().getText();
        if(!SlideNavigation.supports(source)) return;
        SlideNavigation.atOffset(source,getEditor().getCaretModel().getOffset()).ifPresent(slide -> {
            selectOutline(slide.number());requestPreviewSlide(slide.number());
        });
    }
    private void selectOutline(int number) {
        boolean previous=rebuilding;rebuilding=true;
        try { if(number>0 && number<=outline.getItemCount()) outline.setSelectedIndex(number-1); }
        finally { rebuilding=previous; }
    }
    private void requestPreviewSlide(int number) {
        if(browser==null || baseUrl==null || number<1) return;
        if(!SlideNavigation.supports(getEditor().getDocument().getText())) {
            status.setText("Navigation sync unavailable for src imports");return;
        }
        if(requestedSlide==number || (requestedSlide==0 && previewSlide==number)) return;
        requestedSlide=number;navigationRequest++;
        if(navigationReady) sendNavigationRequest();
    }
    private void sendNavigationRequest() {
        if(browser==null || !navigationReady || requestedSlide==0) return;
        int number=requestedSlide;
        browser.getCefBrowser().executeJavaScript("window.__slidevIdeNavigation && window.__slidevIdeNavigation.navigate("+number+");",baseUrl,0);
        navigationTimeout.restart();
    }
    private void receiveNavigation(String message) {
        if(message.equals("ready")) {
            navigationReady=true;status.setText("Preview running");
            if(requestedSlide!=0) sendNavigationRequest();
            return;
        }
        if(message.startsWith("error:")) {
            navigationTimeout.stop();requestedSlide=0;navigationRequest++;
            status.setText("Navigation sync unavailable. See log.");log.append("[Navigation] "+message.substring(6)+"\n");return;
        }
        if(!message.startsWith("page:")) return;
        int number;
        try { number=Integer.parseInt(message.substring(5)); }
        catch(NumberFormatException ex) { return; }
        if(number<1) return;
        // Ignore an older browser state while a newer editor command is pending.
        if(requestedSlide!=0 && requestedSlide!=number) return;
        navigationTimeout.stop();requestedSlide=0;navigationRequest++;
        boolean changed=previewSlide!=number;
        previewSlide=number;
        if(changed && !caretNavigationPending && syncNavigation && presentation==null) syncEditorToPreview(number);
    }
    private void syncEditorToPreview(int number) {
        String source=getEditor().getDocument().getText();
        if(!SlideNavigation.supports(source)) return;
        SlideNavigation.byNumber(source,number).ifPresent(slide -> {
            caretTimer.stop();caretNavigationPending=false;
            applyingPreview=true;
            try {
                selectOutline(number);
                var current=SlideNavigation.atOffset(source,getEditor().getCaretModel().getOffset());
                if(current.isEmpty() || current.get().number()!=number) {
                    getEditor().getCaretModel().moveToOffset(SlideNavigation.caretOffset(slide));
                    getEditor().getScrollingModel().scrollToCaret(ScrollType.CENTER);
                }
            } finally { applyingPreview=false; }
        });
    }
    private String bridgeScript(String resource, JBCefJSQuery query) {
        try(var input=SlidevEditor.class.getResourceAsStream("/dev/slidev/intellij/"+resource)) {
            if(input==null) throw new IOException("Slidev bridge resource missing: "+resource);
            return new String(input.readAllBytes(),StandardCharsets.UTF_8).replace("/*REPORT*/",query.inject("message"));
        } catch(IOException ex) { throw new IllegalStateException(ex); }
    }
    private void clearBuildIssues() {
        issues.clear();
        var markup=getEditor().getMarkupModel();
        buildHighlights.forEach(markup::removeHighlighter);buildHighlights.clear();
    }
    private void cancelBuild() {
        if(build==null) return;
        build.close();build=null;checkBuild.setText("Check build");buildStatus.setText("Build check cancelled");
    }
    private void checkBuild(Project project) {
        if(disposed) return;
        WriteIntentReadAction.run(() -> FileDocumentManager.getInstance().saveDocument(getEditor().getDocument()));
        String source=getEditor().getDocument().getText();
        long stamp=getEditor().getDocument().getModificationStamp();
        clearBuildIssues();buildStatus.setText("Checking build…");checkBuild.setText("Cancel check");
        log.append("\n[Build check] Starting local Slidev build\n");
        SlidevBuild session=new SlidevBuild();build=session;
        session.start(Path.of(file.getPath()),SlidevSettings.get(project).getState().nodeExecutable,
            line -> onEdt(() -> {
                if(build!=session) return;
                if(log.getDocument().getLength()>100_000) log.setText(log.getText().substring(50_000));
                log.append("[Build] "+line+"\n");
            }),result -> onEdt(() -> {
                if(build!=session) return;
                build=null;checkBuild.setText("Check build");
                if(getEditor().getDocument().getModificationStamp()!=stamp) {
                    buildStatus.setText("Document changed. Check build again.");return;
                }
                buildStatus.setText(result.message());
                TextAttributes error=new TextAttributes(null,null,JBColor.RED,EffectType.WAVE_UNDERSCORE,Font.PLAIN);
                for(var diagnostic:result.diagnostics()) {
                    boolean imports=DeckParser.parse(source).slides().stream().anyMatch(slide -> slide.frontmatter()!=null &&
                        java.util.regex.Pattern.compile("(?m)^\\s*src\\s*:").matcher(source.substring(slide.frontmatter().start(),slide.frontmatter().end())).find());
                    boolean sameFile;
                    try {
                        Path reported=Path.of(diagnostic.path());
                        sameFile=reported.isAbsolute()?reported.normalize().equals(Path.of(file.getPath()).normalize()):reported.toString().equals(file.getName());
                    } catch(RuntimeException ex) { sameFile=false; }
                    var location=sameFile && !imports?BuildDiagnostics.locate(source,diagnostic):java.util.Optional.<BuildDiagnostics.Location>empty();
                    int offset=location.map(BuildDiagnostics.Location::offset).orElse(-1);
                    String position=location.map(loc -> "Line "+loc.line()+", column "+loc.column()+" (slide-relative mapping)")
                        .orElse(diagnostic.path()+", slide "+diagnostic.slide()+", generated "+diagnostic.line()+":"+diagnostic.column());
                    issues.addElement(new BuildIssue(position+": "+diagnostic.message(),offset));
                    if(offset>=0 && offset<source.length()) {
                        var highlighter=getEditor().getMarkupModel().addRangeHighlighter(offset,offset+1,HighlighterLayer.ERROR,error,HighlighterTargetArea.EXACT_RANGE);
                        highlighter.setErrorStripeMarkColor(JBColor.RED);
                        highlighter.setErrorStripeTooltip(position+": "+diagnostic.message());
                        buildHighlights.add(highlighter);
                    }
                }
                if(!result.success() && issues.isEmpty()) issues.addElement(new BuildIssue(result.message(),-1));
            }));
    }
    private void startVisiblePreview() {
        if(!selected || !panel.isShowing() || project.isDisposed()) return;
        viewHost.ensureLayout();
        startPreview(project);
    }
    private void startPreview(Project project) {
        try { startPreviewInternal(project); }
        catch(com.intellij.openapi.progress.ProcessCanceledException ex) { throw ex; }
        catch(RuntimeException | LinkageError ex) {
            LOG.warn("Slidev preview initialization failed",ex);
            stopPreview();
            status.setText("Preview failed: "+ex.getClass().getSimpleName());
            log.append(ex.toString()+"\nSee idea.log for the complete stack trace.\n");
        }
    }
    private void startPreviewInternal(Project project) {
        if(server!=null || disposed) return;
        if(!JBCefApp.isSupported()) { status.setText("JCEF unavailable. Use JetBrains Runtime with JCEF.");return; }
        WriteIntentReadAction.run(() -> FileDocumentManager.getInstance().saveDocument(getEditor().getDocument()));
        log.setText("");status.setText("Starting Slidev…");
        browser=new JBCefBrowser();
        navigationQuery=JBCefJSQuery.create((JBCefBrowserBase)browser);
        JBCefJSQuery query=navigationQuery;
        query.addHandler(message -> {
            onEdt(() -> { if(navigationQuery==query) receiveNavigation(message); });return null;
        });
        String navigationScript=bridgeScript("navigation.js",query);
        escapeQuery=JBCefJSQuery.create((JBCefBrowserBase)browser);
        JBCefJSQuery fullscreenQuery=escapeQuery;
        fullscreenQuery.addHandler(message -> {
            onEdt(() -> {
                if(escapeQuery!=fullscreenQuery) return;
                if(message.equals("fullscreen:1")) enterPresentation();
                else if(message.equals("fullscreen:0") || message.equals("escape")) exitPresentation();
            });return null;
        });
        String presentationScript=bridgeScript("presentation.js",fullscreenQuery);
        JBCefBrowser activeBrowser=browser;
        browser.getJBCefClient().addLoadHandler(new CefLoadHandlerAdapter() {
            @Override public void onLoadEnd(CefBrowser cef,CefFrame frame,int code) {
                if(!frame.isMain() || disposed || escapeQuery==null) return;
                String address=cef.getURL();
                if(address.equals("about:blank")) return;
                onEdt(() -> {
                    if(browser!=activeBrowser) return;
                    log.append("[Browser] Main frame loaded: HTTP "+code+" "+address+"\n");
                    if(code>=200 && code<400) status.setText("Preview loaded");
                    else if(code>=400) status.setText("Preview failed: HTTP "+code+". See debug console.");
                });
                if(code>=200 && code<400) {
                    cef.executeJavaScript(navigationScript,address,0);
                    cef.executeJavaScript(presentationScript,address,0);
                }
            }
            @Override public void onLoadError(CefBrowser cef,CefFrame frame,org.cef.handler.CefLoadHandler.ErrorCode code,String text,String address) {
                if(!frame.isMain() || code==org.cef.handler.CefLoadHandler.ErrorCode.ERR_ABORTED) return;
                onEdt(() -> {
                    if(browser!=activeBrowser) return;
                    status.setText("Preview failed: "+text);
                    log.append("[Browser] Load error "+code+": "+text+" "+address+"\n");
                });
            }
        },browser.getCefBrowser());
        browserHost.removeAll();browserHost.add(browser.getComponent(),BorderLayout.CENTER);browserHost.revalidate();browserHost.repaint();
        SlidevServer session=new SlidevServer(line -> onEdt(() -> {
            if(log.getDocument().getLength()>100_000) log.setText(log.getText().substring(50_000));
            log.append(line+"\n");log.setCaretPosition(log.getDocument().getLength());
        }));
        server=session;
        session.start(Path.of(file.getPath()),SlidevSettings.get(project).getState().nodeExecutable,
            url -> onEdt(() -> {
                if(server!=session) return;
                baseUrl=url;
                String source=getEditor().getDocument().getText();
                int number=SlideNavigation.supports(source)?SlideNavigation.atOffset(source,getEditor().getCaretModel().getOffset()).map(DeckParser.Slide::number).orElse(1):1;
                requestedSlide=number;navigationRequest++;
                status.setText("Slidev server ready. Loading preview…");
                // Let Swing finish attaching and sizing the browser before loading.
                onEdt(() -> {
                    if(server!=session || browser==null) return;
                    viewHost.ensureLayout();
                    log.append("[Browser] Loading "+url+number+", mode="+viewMode+", host="+browserHost.getWidth()+"x"+browserHost.getHeight()+", showing="+browserHost.isShowing()+"\n");
                    browser.loadURL(url+number);
                });
            }),message -> onEdt(() -> {
                if(server!=session) return;
                stopPreview();status.setText(message);
            }));
    }
    private void stopPreview() {
        exitPresentation();
        if(server!=null) { server.close();server=null; }
        caretTimer.stop();navigationTimeout.stop();caretNavigationPending=false;navigationReady=false;previewSlide=0;requestedSlide=0;navigationRequest++;
        if(navigationQuery!=null) { navigationQuery.dispose();navigationQuery=null; }
        if(escapeQuery!=null) { escapeQuery.dispose();escapeQuery=null; }
        if(browser!=null) { browser.dispose();browser=null; }
        baseUrl=null;
        browserHost.removeAll();browserHost.add(new JLabel("Preview stopped",SwingConstants.CENTER));browserHost.revalidate();browserHost.repaint();
        status.setText("Preview stopped");
    }
    private void enterPresentation() {
        if(presentation!=null) return;
        if(browser==null || baseUrl==null) return;
        // Move the same Chromium component, preserving slide, click step and animations.
        caretTimer.stop();navigationTimeout.stop();caretNavigationPending=false;requestedSlide=0;navigationRequest++;
        GraphicsConfiguration screen=panel.getGraphicsConfiguration();
        presentation=new JFrame("Slidev presentation",screen);
        presentation.setUndecorated(true);
        presentation.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        presentation.addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent event) { exitPresentation(); } });
        presentation.getRootPane().registerKeyboardAction(event -> exitPresentation(),KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),JComponent.WHEN_IN_FOCUSED_WINDOW);
        browserHost.remove(browser.getComponent());
        browserHost.add(new JLabel("Presentation open. Press Esc to return.",SwingConstants.CENTER));
        presentation.add(browser.getComponent(),BorderLayout.CENTER);
        presentation.setBounds(screen.getBounds());
        presentation.setVisible(true);
        browser.getComponent().requestFocusInWindow();
        browserHost.revalidate();browserHost.repaint();
    }
    private void exitPresentation() {
        if(presentation==null) return;
        JFrame frame=presentation;presentation=null;
        if(browser!=null) {
            browser.getCefBrowser().executeJavaScript("if(document.fullscreenElement && document.exitFullscreen){document.exitFullscreen().catch(function(){});}",browser.getCefBrowser().getURL(),0);
            frame.remove(browser.getComponent());
        }
        frame.dispose();browserHost.removeAll();
        if(browser!=null && !disposed) browserHost.add(browser.getComponent(),BorderLayout.CENTER);
        browserHost.revalidate();browserHost.repaint();
        if(syncNavigation && previewSlide>0 && !disposed) syncEditorToPreview(previewSlide);
    }
    private void updateStructure() {
        if(disposed) return;
        var document=getEditor().getDocument();
        var deck=DeckParser.parse(document.getText());
        int selected=outline.getSelectedIndex();
        rebuilding=true;
        try {
            outline.removeAllItems();deck.slides().forEach(outline::addItem);
            if(outline.getItemCount()>0) outline.setSelectedIndex(Math.max(0,Math.min(selected,outline.getItemCount()-1)));
        } finally { rebuilding=false; }
        var markup=getEditor().getMarkupModel();
        decorations.forEach(markup::removeHighlighter);decorations.clear();
        TextAttributes separator=new TextAttributes(JBColor.CYAN,null,null,null,Font.BOLD);
        for(var span:deck.separators()) if(span.end()>span.start()) decorations.add(markup.addRangeHighlighter(span.start(),span.end(),HighlighterLayer.ADDITIONAL_SYNTAX,separator,HighlighterTargetArea.EXACT_RANGE));
        var folding=getEditor().getFoldingModel();
        folding.runBatchFoldingOperation(() -> {
            java.util.Map<Integer,Boolean> expanded=new java.util.HashMap<>();
            for(var region:folds) if(region.isValid()) { expanded.put(region.getStartOffset(),region.isExpanded());folding.removeFoldRegion(region); }
            folds.clear();
            for(var slide:deck.slides()) if(slide.end()>slide.start()+1) {
                var region=folding.addFoldRegion(slide.start(),slide.end(),"Slide "+slide.number()+": "+slide.title());
                if(region!=null) { region.setExpanded(expanded.getOrDefault(slide.start(),true));folds.add(region); }
            }
        });
    }
    private void onEdt(Runnable action) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if(!disposed) WriteIntentReadAction.run(action);
        },ModalityState.nonModal());
    }
    @Override public boolean canNavigateTo(@NotNull com.intellij.pom.Navigatable target) { return delegate.canNavigateTo(target); }
    @Override public void navigateTo(@NotNull com.intellij.pom.Navigatable target) { delegate.navigateTo(target); }
    @Override public @NotNull Editor getEditor() { return delegate.getEditor(); }
    @Override public @NotNull JComponent getComponent() { return panel; }
    @Override public JComponent getPreferredFocusedComponent() {
        return viewMode==ViewMode.PREVIEW && browser!=null?browser.getComponent():delegate.getPreferredFocusedComponent();
    }
    @Override public @NotNull String getName() { return "Slidev"; }
    @Override public @NotNull FileEditorState getState(@NotNull FileEditorStateLevel level) { return delegate.getState(level); }
    @Override public void setState(@NotNull FileEditorState state) { delegate.setState(state); }
    @Override public boolean isModified() { return delegate.isModified(); }
    @Override public boolean isValid() { return !disposed && delegate.isValid(); }
    @Override public void selectNotify() {
        selected=true;delegate.selectNotify();
        onEdt(this::startVisiblePreview);
    }
    @Override public void deselectNotify() { selected=false;delegate.deselectNotify(); }
    @Override public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) { delegate.addPropertyChangeListener(listener); }
    @Override public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) { delegate.removePropertyChangeListener(listener); }
    @Override public VirtualFile getFile() { return file; }
    @Override public void dispose() {
        if(disposed) return;
        changeTimer.stop();caretTimer.stop();cancelBuild();clearBuildIssues();stopPreview();disposed=true;
        Disposer.dispose(delegate);
    }
}
