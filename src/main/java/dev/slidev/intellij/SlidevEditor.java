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
import dev.slidev.intellij.core.ImportedDeck;
import dev.slidev.intellij.core.PreviewDiagnostics;
import dev.slidev.intellij.core.DiagnosticSources;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
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
    private TextEditor delegate;
    private final java.util.Map<String,TextEditor> sourceEditors=new java.util.LinkedHashMap<>();
    private final java.beans.PropertyChangeSupport propertyChanges=new java.beans.PropertyChangeSupport(this);
    private final JPanel sourceHost=new JPanel(new BorderLayout());
    private final VirtualFile entryFile;
    private volatile VirtualFile currentSource;
    private volatile ImportedDeck.Snapshot deck;
    private volatile java.util.Set<Path> watchedSources=java.util.Set.of();
    private java.util.concurrent.Future<?> deckTask;
    private long deckRequest;
    private boolean deckLoading;
    private String deckNode;
    private final JLabel deckStatus=new JLabel("Resolving deck…");
    private final VirtualFile file;
    private final Project project;
    private final SlidevRuntime runtime;
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
    private final JComboBox<ImportedDeck.Slide> outline=new JComboBox<>();
    private boolean syncNavigation;
    private boolean inspectEnabled,runningInspect;
    private final JButton openInspect=new JButton("Open Inspect");
    private final JButton checkBuild=new JButton("Check build");
    private final JLabel buildStatus=new JLabel("Build not checked");
    private final DefaultListModel<BuildIssue> issues=new DefaultListModel<>();
    private final JList<BuildIssue> issueList=new JList<>(issues);
    private final List<BuildIssue> buildIssues=new ArrayList<>(),previewIssues=new ArrayList<>();
    private PreviewDiagnostics previewDiagnostics;
    private final java.util.Map<Path,DiagnosticSources.Source> vueSources=new java.util.HashMap<>();
    private final java.util.Map<Path,dev.slidev.intellij.core.ExpressionChecker.Result> vueChecks=new java.util.HashMap<>();
    private final java.util.Map<Path,java.util.concurrent.Future<?>> vueTasks=new java.util.HashMap<>();
    private final java.util.Map<Path,Long> vueRequests=new java.util.HashMap<>();
    private final java.util.Set<Path> vueUnavailable=new java.util.HashSet<>();
    private volatile java.util.Set<Path> vueWatches=java.util.Set.of();
    private final java.util.concurrent.atomic.AtomicLong vueRevision=new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.ConcurrentMap<Path,Long> vueDiscoveries=new java.util.concurrent.ConcurrentHashMap<>();
    private long vueRequest;
    private List<BuildDiagnostics.Diagnostic> completedBuildDiagnostics=List.of();
    private PreviewDiagnostics completedBuildDetails;
    private ImportedDeck.Snapshot completedBuildSnapshot;
    private String completedBuildFailure;
    private final java.util.concurrent.atomic.AtomicLong previewGeneration=new java.util.concurrent.atomic.AtomicLong();
    private volatile boolean previewInputsDirty;
    private String previewFailure;
    private final JLabel previewHealth=new JLabel("Preview: no current diagnostics");
    private final JPanel recoveryActions=new JPanel(new FlowLayout(FlowLayout.RIGHT,4,0));
    private final List<RangeHighlighter> buildHighlights=new ArrayList<>();
    private SlidevBuild build;
    private record BuildIssue(String label, Path path, int offset, String snapshot) {
        BuildIssue(String label,Path path,int offset){this(label,path,offset,null);}
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
    private JDialog logDialog;
    private boolean restartPending;
    private volatile boolean disposed;
    private boolean rebuilding;
    private long editRevision;

    public SlidevEditor(Project project,VirtualFile file) {
        this.runtime=SlidevRuntime.get(project);
        this.file=file;this.project=project;this.entryFile=SlidevDeckSupport.entryFor(project,file);this.currentSource=file;
        delegate=(TextEditor)TextEditorProvider.getInstance().createEditor(project,file);
        sourceEditors.put(file.getPath(),delegate);
        delegate.addPropertyChangeListener(event -> propertyChanges.firePropertyChange(event.getPropertyName(),event.getOldValue(),event.getNewValue()));
        sourceHost.add(delegate.getComponent(),BorderLayout.CENTER);
        syncNavigation=SlidevSettings.get(project).getState().syncNavigation;
        debugConsole.setVisible(SlidevSettings.get(project).getState().showDebugConsole);
        inspectEnabled=SlidevSettings.get(project).getState().enableInspect;openInspect.setEnabled(false);
        project.getMessageBus().connect(this).subscribe(SlidevSettings.TOPIC,
            (debug,sync,inspect) -> onEdt(() -> applyEditorOptions(debug,sync,inspect)));
        sourceComponent=sourceHost;previewComponent=previewPanel();
        viewHost=new SlidevView(sourceComponent,previewComponent);
        panel.add(viewHost,BorderLayout.CENTER);
        panel.add(viewToolbar(),BorderLayout.NORTH);
        panel.add(diagnosticsPanel(),BorderLayout.SOUTH);
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
        attachCaret(delegate);
        EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new DocumentListener() {
            @Override public void documentChanged(@NotNull DocumentEvent event) {
                var changed=FileDocumentManager.getInstance().getFile(event.getDocument());
                if(changed==null)return;
                if(isVueSource(changed.getPath())) {
                    Path path=ImportedDeck.normalize(Path.of(changed.getPath()));vueRevision.incrementAndGet();
                    if(vueWatches.contains(path))previewGeneration.incrementAndGet();
                    onEdt(() -> vueChanged(path));return;
                }
                if(!belongs(changed.getPath()))return;
                previewGeneration.incrementAndGet();previewInputsDirty=true;onEdt(SlidevEditor.this::deckChanged);
            }
        },this);
        EditorFactory.getInstance().addEditorFactoryListener(new EditorFactoryListener() {
            @Override public void editorCreated(@NotNull EditorFactoryEvent event){if(event.getEditor().getProject()==project)onEdt(SlidevEditor.this::refreshBuildHighlights);}
            @Override public void editorReleased(@NotNull EditorFactoryEvent event){if(event.getEditor().getProject()==project)onEdt(SlidevEditor.this::refreshBuildHighlights);}
        },this);
        project.getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES,new BulkFileListener() {
            @Override public void after(@NotNull List<? extends VFileEvent> events) {
                var vueChanges=new java.util.HashSet<Path>();boolean deckChange=false;
                for(var event:events) {
                    if(event instanceof com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent property && !VirtualFile.PROP_NAME.equals(property.getPropertyName()))continue;
                    Path affected=ImportedDeck.normalize(Path.of(event.getPath()));var previous=new java.util.ArrayList<Path>();previous.add(affected);
                    if(event instanceof com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent property && VirtualFile.PROP_NAME.equals(property.getPropertyName()) && affected.getParent()!=null)
                        previous.add(affected.getParent().resolve(String.valueOf(property.getOldValue())));
                    if(event instanceof com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent moved)
                        previous.add(Path.of(moved.getOldParent().getPath()).resolve(moved.getFile().getName()));
                    for(Path old:previous)for(Path tracked:vueWatches)if(tracked.startsWith(old)) {
                        if(event instanceof com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent && vueDiscoveries.containsKey(tracked))continue;
                        vueChanges.add(tracked);
                    }
                    if(isVueSource(event.getPath())) {
                        boolean creation=event instanceof com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
                        if(!(creation && vueDiscoveries.containsKey(affected)) && (!creation || vueWatches.contains(affected) || build!=null))vueChanges.add(affected);
                        continue;
                    }
                    if(!belongs(event.getPath()))continue;
                    var changed=event.getFile();var document=changed==null?null:FileDocumentManager.getInstance().getCachedDocument(changed);
                    String saved=deck==null?null:deck.sources().get(affected);
                    if(event instanceof com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent && document!=null && saved!=null && document.getText().equals(saved))continue;
                    deckChange=true;
                }
                if(!vueChanges.isEmpty()) {
                    vueRevision.incrementAndGet();if(vueChanges.stream().anyMatch(vueWatches::contains))previewGeneration.incrementAndGet();
                    onEdt(() -> vueChanges.forEach(SlidevEditor.this::vueChanged));
                }
                if(deckChange){previewGeneration.incrementAndGet();previewInputsDirty=true;onEdt(SlidevEditor.this::deckChanged);}
            }
        });
        checkBuild.addActionListener(event -> onEdt(() -> { if(build!=null) cancelBuild();else checkBuild(project); }));
        issueList.addListSelectionListener(event -> {
            if(event.getValueIsAdjusting()) return;
            var issue=issueList.getSelectedValue();
            if(issue==null || issue.path()==null)return;
            if(issue.path().getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".vue")){onEdt(() -> openVueIssue(issue));return;}
            if(issue.offset()>=0)showSource(issue.path(),issue.offset(),true);
        });
        outline.addActionListener(event -> {
            if(rebuilding || deck==null || !deck.reliable()) return;
            var slide=(ImportedDeck.Slide)outline.getSelectedItem();
            if(slide==null) return;
            applyingPreview=true;
            try {showSource(slide.path(),SlideNavigation.caretOffset(slide.source()),true);}
            finally {applyingPreview=false;}
            requestPreviewSlide(slide.number());
        });
        runtime.add(this);
        updateStructure();
    }
    private JComponent viewToolbar() {
        JPanel toolbar=new JPanel(new BorderLayout());
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT,4,2));actions.add(checkBuild);
        JButton showLog=new JButton("Show Slidev log");showLog.addActionListener(event -> showLog());actions.add(showLog);
        JButton restart=new JButton("Restart preview");restart.addActionListener(event -> onEdt(this::restartPreview));actions.add(restart);
        openInspect.setVisible(inspectEnabled);
        openInspect.setToolTipText("Open Vite's Inspect UI in your browser. Inspect v12 needs devtools: true and @vitejs/devtools in the project.");
        openInspect.addActionListener(event -> {
            if(!runningInspect || baseUrl==null || server==null || !server.isAlive())return;
            com.intellij.ide.BrowserUtil.browse(baseUrl+"__inspect/");
        });actions.add(openInspect);
        JButton entry=new JButton("Entry source");entry.setToolTipText("Show the presentation entry file");entry.addActionListener(event -> onEdt(() -> {
            applyingPreview=true;try{showSource(Path.of(entryFile.getPath()),0,true);}finally{applyingPreview=false;}
        }));actions.add(entry);
        JPanel modes=new JPanel(new FlowLayout(FlowLayout.RIGHT,2,2));
        ButtonGroup group=new ButtonGroup();
        for(ViewMode mode:ViewMode.values()) {
            JToggleButton button=new JToggleButton(viewIcon(mode));
            String title=switch(mode) { case CODE -> "Code";case SPLIT -> "Split";case PREVIEW -> "Preview"; };
            button.setToolTipText(title);button.getAccessibleContext().setAccessibleName(title);
            button.setSelected(mode==viewMode);group.add(button);modes.add(button);
            button.addActionListener(event -> onEdt(() -> setViewMode(mode)));
        }
        toolbar.add(actions,BorderLayout.WEST);toolbar.add(deckStatus,BorderLayout.CENTER);toolbar.add(modes,BorderLayout.EAST);
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
    private void applyEditorOptions(boolean debug, boolean sync, boolean inspect) {
        boolean inspectChanged=inspectEnabled!=inspect;inspectEnabled=inspect;openInspect.setVisible(inspect);
        debugConsole.setVisible(debug);
        boolean changed=syncNavigation!=sync;
        syncNavigation=sync;
        if(changed) {
            caretTimer.stop();navigationTimeout.stop();caretNavigationPending=false;requestedSlide=0;navigationRequest++;
            if(syncNavigation) syncPreviewToCaret();
        }
        panel.revalidate();panel.repaint();
        if(!SlidevSettings.get(project).getState().nodeExecutable.equals(deckNode)) {
            vueRevision.incrementAndGet();vueTasks.values().forEach(task->task.cancel(true));vueTasks.clear();vueRequests.clear();vueDiscoveries.clear();vueSources.clear();vueChecks.clear();vueUnavailable.clear();
            rebuildBuildIssues();refreshPreviewIssues();refreshDeck(null);
        }
        if(inspectChanged && server!=null)restartPreview();
        else if(server==null) startVisiblePreview();
    }
    private JComponent previewPanel() {
        JPanel preview=new JPanel(new BorderLayout(6,6));
        JPanel top=new JPanel(new BorderLayout());top.add(outline,BorderLayout.CENTER);top.add(status,BorderLayout.SOUTH);
        preview.add(top,BorderLayout.NORTH);
        browserHost.add(new JLabel("Slidev preview starts automatically.",SwingConstants.CENTER));
        preview.add(browserHost,BorderLayout.CENTER);
        return preview;
    }
    private JComponent diagnosticsPanel() {
        log.setEditable(false);log.setLineWrap(true);
        JPanel bottom=new JPanel(new BorderLayout());
        JPanel diagnostics=new JPanel(new BorderLayout());
        JPanel heading=new JPanel(new BorderLayout());
        JPanel labels=new JPanel(new GridLayout(0,1));labels.add(previewHealth);labels.add(buildStatus);heading.add(labels,BorderLayout.CENTER);
        JButton openLog=new JButton("Open log");openLog.addActionListener(event -> showLog());recoveryActions.add(openLog);
        JButton restart=new JButton("Restart preview");restart.addActionListener(event -> onEdt(this::restartPreview));recoveryActions.add(restart);
        recoveryActions.setVisible(false);heading.add(recoveryActions,BorderLayout.EAST);diagnostics.add(heading,BorderLayout.NORTH);
        issueList.setVisibleRowCount(3);
        diagnostics.add(new JScrollPane(issueList),BorderLayout.CENTER);
        bottom.add(diagnostics,BorderLayout.NORTH);bottom.add(debugConsole,BorderLayout.CENTER);
        return bottom;
    }
    private void handleCaretTimer() {
        if(changeTimer.isRunning() || deckLoading) { caretTimer.restart();return; }
        caretNavigationPending=false;syncPreviewToCaret();
    }
    private void syncPreviewToCaret() {
        if(!syncNavigation || presentation!=null || browser==null || baseUrl==null) return;
        if(deck==null || !deck.reliable() || !SlidevDeckSupport.matches(deck))return;
        deck.at(Path.of(currentSource.getPath()),getEditor().getCaretModel().getOffset(),previewSlide).ifPresent(slide -> {
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
        if(deck==null || !deck.reliable() || !SlidevDeckSupport.matches(deck))return;
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
            navigationReady=true;if(previewFailure==null && previewIssues.isEmpty())status.setText("Preview running");
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
        boolean acknowledged=requestedSlide==number;
        navigationTimeout.stop();requestedSlide=0;navigationRequest++;
        boolean changed=previewSlide!=number;
        previewSlide=number;
        if(changed && !acknowledged && !caretNavigationPending && syncNavigation && presentation==null) syncEditorToPreview(number);
    }
    private void syncEditorToPreview(int number) {
        if(deck==null || !deck.reliable() || !SlidevDeckSupport.matches(deck))return;
        deck.byNumber(number).ifPresent(slide -> {
            caretTimer.stop();caretNavigationPending=false;applyingPreview=true;
            try {
                selectOutline(number);
                var current=deck.at(Path.of(currentSource.getPath()),getEditor().getCaretModel().getOffset(),number);
                if(!ImportedDeck.normalize(Path.of(currentSource.getPath())).equals(slide.path()) || current.isEmpty() || current.get().number()!=number)showSource(slide.path(),SlideNavigation.caretOffset(slide.source()),false);
            } finally {applyingPreview=false;}
        });
    }
    private String bridgeScript(String resource, JBCefJSQuery query) {
        try(var input=SlidevEditor.class.getResourceAsStream("/dev/slidev/intellij/"+resource)) {
            if(input==null) throw new IOException("Slidev bridge resource missing: "+resource);
            return new String(input.readAllBytes(),StandardCharsets.UTF_8).replace("/*REPORT*/",query.inject("message"));
        } catch(IOException ex) { throw new IllegalStateException(ex); }
    }
    private void clearBuildIssues() {
        completedBuildDiagnostics=List.of();completedBuildDetails=null;completedBuildSnapshot=null;completedBuildFailure=null;
        buildIssues.clear();rebuildIssues();
    }
    private void cancelBuild() {
        if(build==null) return;
        build.close();build=null;checkBuild.setText("Check build");buildStatus.setText("Build check cancelled");
    }
    private void checkBuild(Project project) {
        if(disposed)return;
        buildStatus.setText("Resolving imported sources…");refreshDeck(() -> checkResolvedBuild(project));
    }
    private void checkResolvedBuild(Project project) {
        if(disposed || deck==null)return;
        saveDeckDocuments();
        ImportedDeck.Snapshot snapshot=deck;long revision=editRevision,componentRevision=vueRevision.get();
        clearBuildIssues();buildStatus.setText("Checking build…");checkBuild.setText("Cancel check");
        log.append("\n[Build check] Starting local Slidev build\n");
        SlidevBuild session=new SlidevBuild(runtime.tasks);build=session;
        session.start(Path.of(entryFile.getPath()),SlidevSettings.get(project).getState().nodeExecutable,
            line -> onEdt(() -> {if(build!=session)return;if(log.getDocument().getLength()>100_000)log.setText(log.getText().substring(50_000));log.append("[Build] "+line+"\n");}),
            result -> onEdt(() -> {
                if(build!=session)return;build=null;checkBuild.setText("Check build");
                if(revision!=editRevision || componentRevision!=vueRevision.get() || !SlidevDeckSupport.matches(snapshot)){buildStatus.setText("Deck changed. Check build again.");return;}
                buildStatus.setText(result.message());
                completedBuildDiagnostics=result.diagnostics();completedBuildDetails=result.details();completedBuildSnapshot=snapshot;completedBuildFailure=result.success()?null:result.message();
                rebuildBuildIssues();
                refreshPreviewIssues();
            }));
    }
    private void rebuildBuildIssues() {
        if(completedBuildSnapshot==null)return;
        buildIssues.clear();Path entry=Path.of(entryFile.getPath());
        for(var diagnostic:completedBuildDiagnostics) {
            var path=DiagnosticSources.vuePath(entry,diagnostic);
            if(path.isPresent()){buildIssues.add(vueIssue("[Build] ",diagnostic,completedBuildDetails,path.get()));continue;}
            var mapped=completedBuildSnapshot.locate(diagnostic);
            String position=mapped.map(value -> value.path().getFileName()+": line "+value.location().line()+", column "+value.location().column()+" (slide-relative mapping)")
                .orElse(diagnostic.path()+", slide "+diagnostic.slide()+", generated "+diagnostic.line()+":"+diagnostic.column());
            buildIssues.add(new BuildIssue("[Build] "+position+": "+diagnostic.message(),mapped.map(ImportedDeck.MappedDiagnostic::path).orElse(null),mapped.map(value->value.location().offset()).orElse(-1)));
        }
        if(buildIssues.isEmpty() && completedBuildFailure!=null)buildIssues.add(new BuildIssue("[Build] "+completedBuildFailure,null,-1));
    }
    private void rebuildIssues() {
        issues.clear();previewIssues.forEach(issues::addElement);buildIssues.forEach(issues::addElement);refreshBuildHighlights();
    }
    private void refreshPreviewIssues() {
        previewIssues.clear();scheduleVueSources();
        boolean canMap=deck!=null && !previewInputsDirty && SlidevDeckSupport.matches(deck);
        if(previewDiagnostics!=null)for(var diagnostic:previewDiagnostics.diagnostics()) {
            var vue=DiagnosticSources.vuePath(Path.of(entryFile.getPath()),diagnostic);
            if(vue.isPresent()){previewIssues.add(vueIssue("[Preview] ",diagnostic,previewDiagnostics,vue.get()));continue;}
            var mapped=canMap?previewDiagnostics.locateCurrent(deck,diagnostic):java.util.Optional.<ImportedDeck.MappedDiagnostic>empty();
            String position=mapped.map(value -> value.path().getFileName()+": line "+value.location().line()+", column "+value.location().column()+(diagnostic.slide()>0?" (slide-relative mapping)":""))
                .orElse(diagnostic.path().isEmpty()?"No source position":diagnostic.path()+(diagnostic.slide()>0?", slide "+diagnostic.slide():"")+(diagnostic.line()>0?", reported "+diagnostic.line()+":"+diagnostic.column():""));
            previewIssues.add(new BuildIssue("[Preview] "+position+": "+diagnostic.message(),mapped.map(ImportedDeck.MappedDiagnostic::path).orElse(null),mapped.map(value -> value.location().offset()).orElse(-1)));
        }
        if(previewFailure!=null && previewIssues.isEmpty())previewIssues.add(new BuildIssue("[Preview] "+previewFailure,null,-1));
        previewHealth.setText(previewFailure!=null?"Preview unavailable: "+previewFailure:previewIssues.isEmpty()?"Preview: no current diagnostics":"Preview: "+previewIssues.size()+" error(s) reported");
        recoveryActions.setVisible(previewFailure!=null || !previewIssues.isEmpty());
        rebuildIssues();panel.revalidate();
    }
    private boolean isVueSource(String path) {
        if(!path.toLowerCase(java.util.Locale.ROOT).endsWith(".vue"))return false;
        Path file=ImportedDeck.normalize(Path.of(path));
        return vueWatches.contains(file) || file.startsWith(Path.of(entryFile.getPath()).getParent()) ||
            (project.getBasePath()!=null && file.startsWith(Path.of(project.getBasePath())));
    }
    private void vueChanged(Path path) {
        vueSources.remove(path);vueChecks.remove(path);vueUnavailable.remove(path);vueRequests.remove(path);
        var task=vueTasks.remove(path);if(task!=null)task.cancel(true);
        if(previewDiagnostics!=null)previewDiagnostics.invalidateVue(Path.of(entryFile.getPath()),path);
        cancelBuild();clearBuildIssues();buildStatus.setText("Vue source changed. Check build again.");refreshPreviewIssues();
    }
    private BuildIssue vueIssue(String prefix,BuildDiagnostics.Diagnostic diagnostic,PreviewDiagnostics details,Path path) {
        var source=vueSources.get(path);
        var mapped=source==null || details==null?java.util.Optional.<ImportedDeck.MappedDiagnostic>empty():details.locateVue(Path.of(entryFile.getPath()),diagnostic,source,vueChecks.get(path));
        String position=path+(mapped.isPresent()?": line "+mapped.get().location().line()+", column "+mapped.get().location().column():" (reported "+diagnostic.line()+":"+diagnostic.column()+", position not verified)");
        return new BuildIssue(prefix+position+": "+diagnostic.message(),path,mapped.map(value->value.location().offset()).orElse(-1),source==null?null:source.text());
    }
    private void scheduleVueSources() {
        var candidates=new java.util.LinkedHashSet<Path>();Path entry=Path.of(entryFile.getPath());
        if(previewDiagnostics!=null)for(var d:previewDiagnostics.diagnostics())DiagnosticSources.vuePath(entry,d).ifPresent(candidates::add);
        for(var d:completedBuildDiagnostics)DiagnosticSources.vuePath(entry,d).ifPresent(candidates::add);
        vueWatches=java.util.Set.copyOf(candidates);vueSources.keySet().retainAll(candidates);vueChecks.keySet().retainAll(candidates);vueUnavailable.retainAll(candidates);
        int count=0;
        for(Path path:candidates) {
            if(++count>32 || vueTasks.size()>=4)break;
            if(vueSources.containsKey(path) || vueTasks.containsKey(path) || vueUnavailable.contains(path))continue;
            var virtual=LocalFileSystem.getInstance().findFileByPath(path.toString().replace('\\','/'));
            var document=virtual==null?null:FileDocumentManager.getInstance().getCachedDocument(virtual);
            if(document!=null && FileDocumentManager.getInstance().isDocumentUnsaved(document))continue;
            long request=++vueRequest,revision=vueRevision.get();vueRequests.put(path,request);
            String node=SlidevSettings.get(project).getState().nodeExecutable;
            vueTasks.put(path,runtime.tasks.submit(() -> {
                try {
                var source=DiagnosticSources.read(path);
                if(source.isPresent())vueDiscoveries.put(path,request);
                var file=LocalFileSystem.getInstance().refreshAndFindFileByPath(path.toString().replace('\\','/'));
                var checked=source.isPresent()?DiagnosticSources.analyze(source.get(),entry,node,()->{ProgressManager.checkCanceled();if(Thread.currentThread().isInterrupted() || disposed || project.isDisposed())throw new java.util.concurrent.CancellationException();}):null;
                onEdt(() -> {
                    vueDiscoveries.remove(path,request);
                    if(!java.util.Objects.equals(vueRequests.get(path),request))return;
                    vueRequests.remove(path);vueTasks.remove(path);
                    if(revision==vueRevision.get()) {
                        var cached=file==null?null:FileDocumentManager.getInstance().getCachedDocument(file);
                        if(source.isPresent() && (cached==null || (!FileDocumentManager.getInstance().isDocumentUnsaved(cached) && source.get().matches(cached.getText())))){vueSources.put(path,source.get());vueChecks.put(path,checked);}
                        else vueUnavailable.add(path);
                    }
                    rebuildBuildIssues();refreshPreviewIssues();
                });
                } catch(java.util.concurrent.CancellationException canceled) {onEdt(() -> vueLoadFailed(path,request));}
                catch(RuntimeException ex){onEdt(() -> {log.append("[Vue source] "+ex+"\n");vueLoadFailed(path,request);});}
            }));
        }
    }
    private void vueLoadFailed(Path path,long request) {
        vueDiscoveries.remove(path,request);if(!java.util.Objects.equals(vueRequests.get(path),request))return;
        vueRequests.remove(path);vueTasks.remove(path);vueUnavailable.add(path);rebuildBuildIssues();refreshPreviewIssues();
    }
    private void openVueIssue(BuildIssue issue) {
        var file=LocalFileSystem.getInstance().findFileByPath(issue.path().toString().replace('\\','/'));
        if(file!=null){openVueIssue(issue,file);return;}
        status.setText("Loading source: "+issue.path());
        runtime.tasks.submit(() -> {
            var found=LocalFileSystem.getInstance().refreshAndFindFileByPath(issue.path().toString().replace('\\','/'));
            onEdt(() -> openVueIssue(issue,found));
        });
    }
    private void openVueIssue(BuildIssue issue,VirtualFile file) {
        if(file==null || !file.isValid()){status.setText("Source file unavailable: "+issue.path());return;}
        var document=FileDocumentManager.getInstance().getDocument(file);
        if(issue.offset()>=0 && (document==null || issue.snapshot()==null || !issue.snapshot().equals(document.getText()))){vueChanged(issue.path());return;}
        if(issue.offset()>=0)FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project,file,issue.offset()),true);
        else FileEditorManager.getInstance(project).openFile(file,true);
        refreshBuildHighlights();
    }
    private void previewFailed(String message) {
        previewFailure=message;refreshPreviewIssues();
    }
    private void refreshBuildHighlights() {
        buildHighlights.forEach(highlighter->{if(highlighter.isValid())highlighter.dispose();});buildHighlights.clear();
        TextAttributes error=new TextAttributes(null,null,JBColor.RED,EffectType.WAVE_UNDERSCORE,Font.PLAIN);
        for(var editor:EditorFactory.getInstance().getAllEditors()) {
            if(editor.isDisposed() || editor.getProject()!=project)continue;
            var file=FileDocumentManager.getInstance().getFile(editor.getDocument());if(file==null)continue;
            Path path=ImportedDeck.normalize(Path.of(file.getPath()));
            for(int i=0;i<issues.size();i++) {
                var issue=issues.get(i);if(issue.path()==null || !issue.path().equals(path) || issue.offset()<0 || issue.offset()>=editor.getDocument().getTextLength())continue;
                if(issue.snapshot()!=null && !issue.snapshot().equals(editor.getDocument().getText()))continue;
                var highlighter=editor.getMarkupModel().addRangeHighlighter(issue.offset(),issue.offset()+1,HighlighterLayer.ERROR,error,HighlighterTargetArea.EXACT_RANGE);
                highlighter.setErrorStripeMarkColor(JBColor.RED);highlighter.setErrorStripeTooltip(issue.label());buildHighlights.add(highlighter);
            }
        }
    }
    private void startVisiblePreview() {
        if(!selected || !panel.isShowing() || project.isDisposed()) return;
        viewHost.ensureLayout();
        startPreview(project);
    }
    private void restartPreview() {
        if(disposed)return;
        previewGeneration.incrementAndGet();previewDiagnostics=null;previewFailure=null;refreshPreviewIssues();
        log.append("\n[Preview] Restart requested\n");stopPreview();restartPending=true;status.setText("Restarting Slidev…");saveDeckDocuments();
        refreshDeck(null);
    }
    private void showLog() {
        if(logDialog!=null && logDialog.isDisplayable()){logDialog.setVisible(true);logDialog.toFront();return;}
        logDialog=new JDialog(SwingUtilities.getWindowAncestor(panel),"Slidev log",Dialog.ModalityType.MODELESS);
        JTextArea output=new JTextArea(log.getDocument());output.setEditable(false);output.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));
        output.setCaretPosition(output.getDocument().getLength());
        JPanel content=new JPanel(new BorderLayout(6,6));content.add(new JScrollPane(output),BorderLayout.CENTER);
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton restart=new JButton("Restart preview");restart.addActionListener(event -> onEdt(this::restartPreview));buttons.add(restart);
        JButton close=new JButton("Close");close.addActionListener(event -> logDialog.dispose());buttons.add(close);
        content.add(buttons,BorderLayout.SOUTH);logDialog.setContentPane(content);logDialog.setSize(900,500);
        logDialog.setLocationRelativeTo(panel);logDialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);logDialog.setVisible(true);
    }
    private void startPreview(Project project) {
        try { startPreviewInternal(project); }
        catch(com.intellij.openapi.progress.ProcessCanceledException ex) { throw ex; }
        catch(RuntimeException | LinkageError ex) {
            LOG.warn("Slidev preview initialization failed",ex);
            stopPreview();
            status.setText("Preview failed: "+ex.getClass().getSimpleName()+". Open Show Slidev log.");
            var trace=new java.io.StringWriter();ex.printStackTrace(new java.io.PrintWriter(trace));
            log.append(trace.toString());previewFailed("Preview initialization failed: "+ex.getClass().getSimpleName());
        }
    }
    private void startPreviewInternal(Project project) {
        if(server!=null || disposed) return;
        if(!JBCefApp.isSupported()) { status.setText("JCEF unavailable. Use JetBrains Runtime with JCEF.");return; }
        WriteIntentReadAction.run(this::saveDeckDocuments);
        previewGeneration.incrementAndGet();previewFailure=null;
        PreviewDiagnostics diagnostics=new PreviewDiagnostics();previewDiagnostics=diagnostics;refreshPreviewIssues();
        log.append("\n[Preview] Starting Slidev at "+java.time.LocalTime.now()+"\n");status.setText("Starting Slidev…");
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
                    if(code>=200 && code<400) {previewFailure=null;refreshPreviewIssues();if(previewIssues.isEmpty())status.setText("Preview loaded");}
                    else if(code>=400) {status.setText("Preview failed: HTTP "+code+". Open Show Slidev log.");previewFailed("HTTP "+code);}

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
                    log.append("[Browser] Load error "+code+": "+text+" "+address+"\n");previewFailed("Browser load failed: "+text);
                });
            }
        },browser.getCefBrowser());
        browserHost.removeAll();browserHost.add(browser.getComponent(),BorderLayout.CENTER);browserHost.revalidate();browserHost.repaint();
        SlidevServer session=new SlidevServer(runtime.tasks,line -> {
            long generation=previewGeneration.get();
            onEdt(() -> {
                if(log.getDocument().getLength()>100_000) log.setText(log.getText().substring(50_000));
                log.append(line+"\n");log.setCaretPosition(log.getDocument().getLength());
                if(previewDiagnostics!=diagnostics || generation!=previewGeneration.get() || previewInputsDirty)return;
                if(diagnostics.accept(line))refreshPreviewIssues();
            });
        });
        server=session;runningInspect=inspectEnabled;
        session.start(Path.of(entryFile.getPath()),SlidevSettings.get(project).getState().nodeExecutable,runningInspect,
            url -> onEdt(() -> {
                if(server!=session) return;
                baseUrl=url;openInspect.setEnabled(runningInspect);
                if(runningInspect)log.append("[Inspect] Open "+url+"__inspect/ (Inspect v12 requires Vite DevTools).\n");
                int number=deck!=null && deck.reliable()?deck.at(Path.of(currentSource.getPath()),getEditor().getCaretModel().getOffset(),previewSlide).map(ImportedDeck.Slide::number).orElse(1):1;
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
                log.append("[Preview] "+message+"\n");stopPreview();status.setText(message+" Use Show Slidev log.");previewFailed(message);
            }));
    }
    private void stopPreview() {
        exitPresentation();
        if(server!=null) { server.close();server=null; }
        caretTimer.stop();navigationTimeout.stop();caretNavigationPending=false;navigationReady=false;previewSlide=0;requestedSlide=0;navigationRequest++;
        if(navigationQuery!=null) { Disposer.dispose(navigationQuery);navigationQuery=null; }
        if(escapeQuery!=null) { Disposer.dispose(escapeQuery);escapeQuery=null; }
        if(browser!=null) { Disposer.dispose(browser);browser=null; }
        baseUrl=null;runningInspect=false;openInspect.setEnabled(false);
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
    private void updateStructure() {updateLocalStructure();refreshDeck(null);}
    private boolean belongs(String path) {
        Path normalized=ImportedDeck.normalize(Path.of(path));
        return normalized.equals(ImportedDeck.normalize(Path.of(entryFile.getPath()))) || normalized.equals(ImportedDeck.normalize(Path.of(currentSource.getPath()))) || watchedSources.contains(normalized);
    }
    private void deckChanged() {
        editRevision++;changeTimer.restart();
        if(previewDiagnostics!=null)previewDiagnostics.clear();refreshPreviewIssues();
        if(syncNavigation && presentation==null){caretNavigationPending=true;caretTimer.restart();}
        cancelBuild();clearBuildIssues();buildStatus.setText("Deck changed. Check build again.");
    }
    private void attachCaret(TextEditor source) {
        source.getEditor().getCaretModel().addCaretListener(new CaretListener() {
            @Override public void caretPositionChanged(@NotNull CaretEvent event) {
                if(event.getEditor()==getEditor() && !applyingPreview && syncNavigation && presentation==null){caretNavigationPending=true;caretTimer.restart();}
            }
        });
    }
    private void refreshDeck(Runnable ready) {
        if(disposed)return;
        long request=++deckRequest,revision=editRevision;
        if(deckTask!=null)deckTask.cancel(true);deckLoading=true;
        Path entry=ImportedDeck.normalize(Path.of(entryFile.getPath()));
        var overrides=SlidevDeckSupport.overrides(project,entry,watchedSources);String node=SlidevSettings.get(project).getState().nodeExecutable;
        deckNode=node;
        deckTask=runtime.tasks.submit(() -> {
            ImportedDeck.Snapshot snapshot;
            try {
                snapshot=SlidevDeckSupport.load(entry,overrides,node,() -> {
                    ProgressManager.checkCanceled();if(Thread.currentThread().isInterrupted() || disposed || project.isDisposed())throw new java.util.concurrent.CancellationException();
                });
            } catch(java.util.concurrent.CancellationException canceled){return;}
            catch(RuntimeException ex){onEdt(() -> {if(request!=deckRequest)return;deckLoading=false;deckTask=null;deckStatus.setText("Deck mapping unavailable");log.append("[Deck] "+ex+"\n");if(restartPending){restartPending=false;startPreview(project);}});return;}
            onEdt(() -> {
                if(request!=deckRequest || revision!=editRevision)return;
                if(!node.equals(SlidevSettings.get(project).getState().nodeExecutable) || (snapshot.reliable() && !SlidevDeckSupport.matches(snapshot))){
                    var known=new java.util.HashSet<>(watchedSources);known.addAll(snapshot.watched());watchedSources=java.util.Set.copyOf(known);refreshDeck(ready);return;
                }
                deckLoading=false;deckTask=null;
                var watched=new java.util.HashSet<Path>(snapshot.watched());if(!snapshot.reliable())watched.addAll(watchedSources);
                watchedSources=java.util.Set.copyOf(watched);deck=snapshot;SlidevDeckSupport.remember(project,snapshot);
                rebuilding=true;
                try {
                    outline.removeAllItems();snapshot.slides().forEach(outline::addItem);
                    var current=snapshot.at(Path.of(currentSource.getPath()),getEditor().getCaretModel().getOffset(),previewSlide);
                    outline.setSelectedIndex(current.map(slide -> slide.number()-1).orElse(-1));
                } finally {rebuilding=false;}
                deckStatus.setText(snapshot.reliable()?"Deck: "+snapshot.slides().size()+" slides ("+currentSource.getName()+")":"Deck mapping unavailable");
                String detail=snapshot.issues().isEmpty()?entry.toString():snapshot.issues().get(0).message();
                if(!snapshot.issues().isEmpty() && !detail.equals(deckStatus.getToolTipText()))log.append("[Deck] "+detail+"\n");
                deckStatus.setToolTipText(detail);
                if(server!=null && server.isAlive())saveDeckDocuments();
                refreshPreviewIssues();
                if(restartPending){restartPending=false;startPreview(project);}
                if(ready!=null)ready.run();
                if(syncNavigation && presentation==null)syncPreviewToCaret();
            });
        });
    }
    private void saveDeckDocuments() {
        var manager=FileDocumentManager.getInstance();
        boolean saving=manager.isDocumentUnsaved(getEditor().getDocument());
        if(deck!=null)for(Path path:deck.sources().keySet()) {
            var source=LocalFileSystem.getInstance().findFileByPath(path.toString().replace('\\','/'));
            var document=source==null?null:manager.getCachedDocument(source);
            saving|=document!=null && manager.isDocumentUnsaved(document);
        }
        // Never carry pre-save diagnostics across an initially unknown dirty import.
        if(saving){previewGeneration.incrementAndGet();if(previewDiagnostics!=null)previewDiagnostics.clear();}
        manager.saveDocument(getEditor().getDocument());
        if(deck!=null)for(Path path:deck.sources().keySet()) {
            var source=LocalFileSystem.getInstance().findFileByPath(path.toString().replace('\\','/'));
            var document=source==null?null:manager.getCachedDocument(source);
            if(document!=null && manager.isDocumentUnsaved(document))manager.saveDocument(document);
        }
        previewInputsDirty=deck!=null && !SlidevDeckSupport.matches(deck);
        if(saving)refreshPreviewIssues();
    }
    private void showSource(Path path,int offset,boolean focus) {
        if(path==null || disposed)return;
        var target=LocalFileSystem.getInstance().findFileByPath(path.toString().replace('\\','/'));
        if(target==null || !target.isValid()){status.setText("Source file unavailable: "+path);return;}
        if(!target.equals(currentSource)) {
            // The browser and its ancestor containers stay attached while only the code child changes.
            var old=getEditor();decorations.forEach(RangeHighlighter::dispose);decorations.clear();
            old.getFoldingModel().runBatchFoldingOperation(() -> {for(var region:folds)if(region.isValid())old.getFoldingModel().removeFoldRegion(region);folds.clear();});
            var next=sourceEditors.get(target.getPath());
            if(next==null) {
                next=(TextEditor)TextEditorProvider.getInstance().createEditor(project,target);sourceEditors.put(target.getPath(),next);attachCaret(next);
                next.addPropertyChangeListener(event -> propertyChanges.firePropertyChange(event.getPropertyName(),event.getOldValue(),event.getNewValue()));
            }
            sourceHost.removeAll();delegate=next;currentSource=target;sourceHost.add(next.getComponent(),BorderLayout.CENTER);sourceHost.revalidate();sourceHost.repaint();
            updateLocalStructure();refreshBuildHighlights();
            deckStatus.setText("Deck: "+(deck==null?"?":deck.slides().size())+" slides ("+target.getName()+")");
        }
        getEditor().getCaretModel().moveToOffset(Math.max(0,Math.min(offset,getEditor().getDocument().getTextLength())));
        getEditor().getScrollingModel().scrollToCaret(ScrollType.CENTER);
        if(focus)getEditor().getContentComponent().requestFocusInWindow();
    }
    private void updateLocalStructure() {
        if(disposed) return;
        var document=getEditor().getDocument();
        var deck=DeckParser.parse(document.getText());
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
            if(!disposed && !runtime.isClosing() && !project.isDisposed()) WriteIntentReadAction.run(action);
        },ModalityState.nonModal(),ignored -> disposed || runtime.isClosing() || project.isDisposed());
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
    @Override public boolean isModified() { return sourceEditors.values().stream().anyMatch(TextEditor::isModified); }
    @Override public com.intellij.codeHighlighting.BackgroundEditorHighlighter getBackgroundHighlighter() {return delegate.getBackgroundHighlighter();}
    @Override public boolean isValid() { return !disposed && delegate.isValid(); }
    @Override public void selectNotify() {
        selected=true;delegate.selectNotify();
        onEdt(this::startVisiblePreview);
    }
    @Override public void deselectNotify() { selected=false;delegate.deselectNotify(); }
    @Override public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) { propertyChanges.addPropertyChangeListener(listener); }
    @Override public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) { propertyChanges.removePropertyChangeListener(listener); }
    @Override public VirtualFile getFile() { return file; }
    @Override public void dispose() {
        if(disposed) return;
        disposed=true;runtime.remove(this);
        changeTimer.stop();caretTimer.stop();cancelBuild();previewIssues.clear();clearBuildIssues();stopPreview();
        deckRequest++;if(deckTask!=null)deckTask.cancel(true);
        vueTasks.values().forEach(task->task.cancel(true));vueTasks.clear();vueRequests.clear();vueDiscoveries.clear();vueSources.clear();vueChecks.clear();vueWatches=java.util.Set.of();
        sourceEditors.values().forEach(Disposer::dispose);sourceEditors.clear();
        if(logDialog!=null){logDialog.dispose();logDialog=null;}
    }
}
