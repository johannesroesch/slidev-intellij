# Validation, updated 2026-11-05

## Version 0.1.3, HTTP readiness

The screenshot shows CLI server URLs while the editor remains in Starting
Slidev, followed by the user's reported startup timeout. The exact failed
HTTP result was hidden by the previous implementation, so its cause is not
yet established. Readiness now connects directly with Proxy.NO_PROXY,
accepts 2xx/3xx responses, allows a longer response timeout and logs status
changes, the requested origin and the final connection error on timeout.

PASS: the actual SlidevServer source was compiled with ECJ for Java 17 and
run against local Python HTTP fixtures returning 200 and 302. Both reached
the ready callback and the processes were closed. This used a minimal
ApplicationManager executor shim, not the IntelliJ SDK or a real Slidev deck.
Full plugin compilation and JCEF/Slidev runtime validation remain unverified.

## Version 0.1.2, evidence from the supplied idea.log

The 10:48:41 stack trace attributes the blank preview to saveDocument called
from SlidevEditor.startPreview without write-intent access on Swing EDT.
Saving before preview startup and the Swing timer callback now run within
WriteIntentReadAction. Preview initialization exceptions are also logged and
displayed in the panel instead of escaping the button listener silently.

The 11:00:44 log confirms version 0.1.1 accepted slides.md but does not contain
the subsequent Creating editor diagnostic. The screenshot confirms only a
TextEditorWithPreview was created. Markdown Pro 2026.2.7 is installed; provider
suppression or selection by another Markdown plugin is a hypothesis requiring
an isolation test, not an established cause. The log ends at 11:01:21 and
contains no explicit-assignment diagnostics corresponding to the screenshot.

Version 0.1.2 has not been compiled or run against the IntelliJ SDK locally.

## Version 0.1.1 editor-opening diagnostics

The user reports that Treat as Slidev is registered but reopening the marked
file does not show the custom editor. The cause is not established.
Version 0.1.1 logs assignment, provider acceptance and editor construction.
The action selects the editor on the next EDT turn and reports its absence
in a dialog with the active editor class names. Exceptions during editor
construction are logged with their stack trace. Process cancellation is
rethrown. The action is also DumbAware, as it does not require indexes.
These diagnostics and API changes have not been compiled against IntelliJ
locally; the IDE SDK and JDK 25 are still unavailable here.

## Editor provider policy correction

The user confirmed a successful check/buildPlugin invocation and installation.
Slidev files open in the custom editor, but preview runtime behavior is not yet
confirmed. The IDE reported that HIDE_DEFAULT_EDITOR requires a DumbAware
FileEditorProvider. SlidevEditorProvider now implements DumbAware; detection
reads VirtualFile contents and project settings and does not query indexes.
This fixes the reported policy violation. It does not establish the cause of
the blank preview, since the supplied stack trace concerns provider selection.

## Gradle test discovery correction

The user's next build successfully ran coreTest (19 checks) and reached the
regular test task, which failed because CoreTest is a standalone Java main
program rather than a JUnit test. The test task now depends on coreTest and
sets failOnNoDiscoveredTests to false. Failed parser checks still fail the
build; only the empty framework-test discovery is permitted.
The full corrected check/buildPlugin invocation remains unverified locally.

## JCEF compile-classpath correction

The user built the original archive against IntelliJ IDEA 2026.2.3. Gradle resolved
the IDE and compiled far enough to report missing org.cef types in SlidevEditor.
The build now explicitly includes intellij.libraries.jcef alongside
intellij.platform.ui.jcef. The former supplies the low-level CefBrowser,
CefFrame and CefLoadHandlerAdapter API; the latter supplies IntelliJ's wrappers.
The plugin.xml runtime dependency com.intellij.modules.jcef stays in place.
JBCefJSQuery.create now explicitly selects the JBCefBrowserBase overload,
avoiding the deprecated JBCefBrowser overload.

Full compilation of this correction is still unverified here: a fresh attempt
to run ./gradlew check buildPlugin failed during the Gradle distribution
download with Network is unreachable, before compilation began.

* PASS: 19 parser checks, compiled with Eclipse ECJ 3.42.0 for Java 17 and executed on OpenJDK 17.0.20.
* PASS: plugin.xml parses as XML.
* PASS: example package.json parses as JSON.
* PASS: official Gradle 9.1.0 wrapper JAR archive integrity and entry point checked.
* PASS: shell syntax checks for gradlew and scripts/test-core.sh.
* BLOCKED: ./gradlew buildPlugin --no-daemon, Gradle distribution download fails with java.net.SocketException: Network is unreachable.
* MISSING: JDK 25 and locally installed IntelliJ SDK/runtime in this environment.
* NOT RUN: full IntelliJ compilation, Plugin Verifier, IDE startup, embedded browser, real Slidev preview, fullscreen state/focus behavior and free-mode compatibility.

The parser test result does not establish IDE API compatibility. This source archive is an initial prototype, not an installable binary or production-ready plugin.

## 0.1.4: Build diagnostics

* PASS: 31 parser/diagnostic checks compiled with ECJ 3.42.0 for Java 17 and executed on OpenJDK 17.0.20. Includes both user-supplied CLI formats, duplicate messages, source position 58:7, CRLF, leading blank lines, unknown slides, invalid positions and paths with spaces.
* PASS: actual subprocess integration with a fixture CLI and an ApplicationManager executor shim: successful/failed exits, output diagnostics, cancellation and worker termination. This is not a real Slidev or IntelliJ runtime test.
* The generated slide line is mapped to the first nonblank content line after normal YAML frontmatter. This is explicitly a heuristic, not a source-map implementation. src imports disable editor-position mapping.
* NOT RUN: full plugin compilation or IntelliJ UI test. This environment only has Java 17 and no IntelliJ SDK. User must run check/buildPlugin on JDK 25.

## 0.1.5: Preview startup route

The user can open localhost:61085/1 in an external browser while the plugin
reports a startup timeout. The user confirmed that the root route / returns
an HTTP error. Readiness now uses the CLI's published loopback URL directly;
Java HTTP is a fallback probing /1 on IPv4, localhost and IPv6. Remote URLs
and invalid ports are not accepted as CLI startup addresses.

* PASS: subprocess fixture test for a published localhost address with a changed port and no JVM HTTP listener. Readiness is delivered without waiting for Java HTTP success.
* PASS: HTTP fixture whose root returns 404 but /1 returns 200 on IPv4, with no published CLI address. Readiness selects the successful IPv4 URL.
* PASS: IPv6 URL extraction, rejection of remote addresses/port 0, and shutdown without leaked workers. ECJ compilation uses an ApplicationManager executor shim; this does not verify IntelliJ runtime compatibility.
* NOT RUN: real Slidev/JCEF/IntelliJ runtime or full plugin build; JDK 25 and the SDK remain unavailable here.

## 0.1.6: Write-safe autosave scheduling

The user's actual IntelliJ stack trace reports TransactionGuard.assertWriteSafeEnvironment
from saveDocument invoked directly in javax.swing.Timer. WriteIntentReadAction alone
acquires the lock but does not establish a write-safe modality context.

Timer work now uses Application.invokeLater with explicit ModalityState.nonModal()
and WriteIntentReadAction. Start-preview and check-build entry actions are scheduled
through the same helper, as are asynchronous editor callbacks. Disposed editors are
ignored; a revision counter suppresses stale timer saves after subsequent edits.

* REVIEWED: all three saveDocument call paths now execute through the explicit non-modal scheduling helper; no direct Timer save remains.
* NOT RUN: TransactionGuard behavior in a real IDE or full plugin compilation. This fix follows the official IntelliJ threading/modality guidance and the provided stack trace, but requires user runtime validation.

## 0.1.7: Bidirectional slide navigation

* PASS: 43 standalone Java checks including caret positions in content/frontmatter, slide boundaries, document EOF, invalid pages/offsets, src imports, and empty-slide reverse mapping. Compiled with ECJ 3.42.0 / Java 17.
* PASS: scripts/test-navigation.cjs with both the contract fixture and actual useEmbeddedCtrl.ts handlers extracted from the published @slidev/client 52.14.1 and 52.20.1 packages. Covers command format, same-slide click preservation, page-only reverse reports, repeated initialization, HMR context replacement, unavailable API and disposal. The real handler's TypeScript casts/imports are removed for execution in a Node VM.
* SOURCE CHECK: setup/root.ts in 52.20.1 exposes window.__slidev__.nav in development mode; useEmbeddedControl handles postMessage navigation without requiring embedded=true. The bridge uses that interface, with no DOM clicks, injected keyboard events or page reloads for slide changes.
* REVIEWED: reverse caret moves suppress feedback; intermediate browser page reports are ignored while an editor target is pending. The 150 ms caret timer waits for the existing document-save timer. Reverse sync does not request focus; fullscreen pauses automatic navigation and return adopts the current preview slide.
* NOT RUN: full IntelliJ compilation or live JCEF behavior. These tests exercise the adapter protocol and mapping, not the complete editor lifecycle, threading, keyboard focus or Slidev/Vue rendering.

## 0.1.8: Editor preferences

The user confirmed that 0.1.7 bidirectional navigation works. Debug console
visibility is now a persisted project setting, default false. The configurable
supports apply/reset/isModified; a project message-bus event updates existing
editors after the settings dialog closes. Editor-disposable connections prevent
retaining closed editors. The hidden JScrollPane is excluded from BorderLayout
sizing while status and the build-issue list stay visible. Logging is unchanged.

* REVIEWED: new and existing editor visibility, default/missing setting false,
  checkbox apply/reset and disposal; console history survives hide/show.
* Sync editor and preview moved out of the toolbar into Settings > Slidev. The
  same project event updates live editors, cancels pending sync work on toggle,
  and adopts the cursor slide when enabled again. Existing persisted preference
  and default true are preserved.
* NOT RUN: IntelliJ UI runtime or full plugin compilation. This reversible UI
  change adds no tests duplicating the implementation; existing navigation and
  diagnostic code is unchanged.

## 0.1.9: Autostart, fullscreen and layout modes

* Automatic preview start is scheduled by selectNotify through the existing
  write-safe non-modal helper. Repeated selection reuses an active session;
  deselection leaves it running; disposal closes it. Failed starts can retry
  on reselect or corrected Node settings. No background retry loop or installer
  was added.
* Start preview, Stop and Present toolbar buttons are removed. Check build
  remains available in the common toolbar. Three vector icons with tooltips and
  accessible names select Code/Split/Preview, reparenting the existing Swing
  components and retaining the split ratio. No browser reload is requested by
  a mode change.
* The fullscreen bridge listens to the browser's real fullscreenchange event
  and Escape. Entry opens the existing presentation JFrame; exit restores the
  same Chromium component and requests document.exitFullscreen if necessary.
  No CefDisplayHandler fullscreen method was assumed: the examined JetBrains
  JCEF Java interface does not expose the C++ fullscreen callback.
* PASS: presentation.js protocol tests with a DOM fixture cover native event
  entry/exit, Escape, unrelated navigation keys, duplicate initialization and
  handler cleanup. The navigation bridge contract tests also pass after sharing
  resource loading with the new presentation bridge.
* NOT RUN: full IntelliJ compilation, automatic tab lifecycle, live Web-Fullscreen,
  component reparenting and keyboard focus. These require JDK 25 plus IntelliJ/
  JCEF on the target machine. DOM-fixture tests do not establish native fullscreen
  support or window behavior.

## 0.1.10: Visible browser layout and startup

The user reports "Preview running" but no visible preview after 0.1.9. Two
regression risks were identified in the changed implementation: browser ancestors
were detached/reparented on view switches, and automatic startup did not wait
for the editor to be showing. The precise target-machine cause remains unconfirmed.

* The new standalone SlidevView retains the same JSplitPane/component parents
  in all modes. It collapses the unused side, restores the divider ratio, and
  explicitly reserves 50 percent for the initial preview despite asymmetric
  preferred sizes. The automatic session waits for both selection and isShowing;
  a hierarchy notification handles delayed visibility. Browser loading is posted
  after attachment/layout. Session identity guards ignore stale callbacks.
* PASS: the actual SlidevView compiled with ECJ/Java 17 and tested on the Swing
  EDT in headless mode: initial preferred sizes 1600/100, five complete mode
  cycles without changed preview/browser parent identity, usable bounds in every
  mode, divider ratio preservation and preview-only resizing.
* Added viewTest to Gradle check and test. Server readiness no longer directly
  claims rendering success. Main-frame load status and Chromium errors are
  reported independently, with URL and host dimensions in the debug console.
* NOT RUN: complete plugin build, real Chromium native peer lifecycle or actual
  visibility on the user's Mac. The Swing regression test validates layout, not
  JCEF paint output or the presentation's client-side rendering.


## 0.1.11: IntelliJ 2026.3 compatibility declaration

* Extended the generated descriptor range from 262..262.* to 262..263.*.
  Kept the default compile baseline at IDEA 2026.2.3 and Java 25. Added
  platformVersion override for compiling/running against a specific 263 build.
* Added the explicit com.intellij.modules.jcef bundled plugin build dependency,
  matching the existing runtime dependency. Retained both JCEF module dependencies.
* Added Plugin Verifier dependency and explicit verification targets: current
  build platform plus IDEA 2026.3 RC 263.7307.112. verificationVersion can override
  the second target. verifyPlugin remains a separate, explicitly invoked task.
* Reviewed JetBrains' published 2026.3 incompatible API changes against imports:
  no direct use of the extracted debugger, external-system, remote-server, webide,
  VCS or JNA APIs, ApplicationLoadListener or RUN_CONTENT_DESCRIPTOR.
  This review is not binary verification or a guarantee for internal APIs.
* PASS: 43 core parser/diagnostic/navigation checks, headless Swing view checks
  (ECJ 3.42.0 targeting Java 17), navigation bridge and fullscreen DOM fixtures.
* BLOCKED: bash gradlew check buildPlugin failed before Gradle startup because
  the Java process could not reach services.gradle.org (Network is unreachable).
  Only Java 17 runtime is installed locally; Java 25 and the IntelliJ SDK are
  unavailable. Full compilation, verifyPlugin and real 2026.3 runtime tests
  were not performed. No installable plugin binary is included.

References reviewed on 2026-11-05:
* https://plugins.jetbrains.com/docs/intellij/api-changes-list-2026.html
* https://plugins.jetbrains.com/docs/intellij/embedded-browser-jcef.html
* https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
* https://youtrack.jetbrains.com/articles/IDEA-A-2100662766


## 0.1.12: First Slidev language support

Implementation:
* Required YAML bundled-plugin dependency, alongside Markdown and JCEF.
* Markdown annotator lexes each conventional metadata block with IntelliJ's YAML
  syntax highlighter and parses it as a nonphysical YAML PSI file. Maps native
  PsiErrorElement ranges to the original Markdown. Checks mapping roots,
  duplicate keys (including nested mappings), and selected closed field types.
* Root key/value completion on Markdown plus native injected YAML, with host
  offset mapping for existing Markdown headmatter injection. Adds colon/space
  when absent and preserves existing values. Deck-only fields are scoped to
  headmatter. No schema rejection of custom fields, themes or layouts.
* Separate recovery scanner includes missing-colon known fields, partial known
  keys and unclosed blocks, with fence/comment exclusions and UTF-16 ranges.
  This does not replace the DeckParser grammar or authoritative Slidev parser.
* Removed the editor's uniform blue metadata overlay to expose token coloring.
* SyntaxTest is a standalone main harness, required by check and test, and is
  included in scripts/test-core.sh. The source archive contains IDE test fixtures.

Validation:
* PASS: 43 existing core checks and 49 metadata/context/schema checks compiled
  with ECJ 3.42.0 targeting Java 17 and executed on Java 17.
* PASS: Swing view test, navigation contract fixture and fullscreen DOM fixture.
* BLOCKED: bash gradlew check buildPlugin failed before Gradle startup with
  Network is unreachable while fetching Gradle 9.1.0. Only a Java 17 runtime is
  installed locally. Full SDK compilation, YAML PSI/annotator execution,
  completion insertion in normal and injected editors, Plugin Verifier and
  runtime tests on IntelliJ 2026.2/2026.3 are NOT RUN.
* Native lexer and parser use public IntelliJ APIs, but the new SDK-facing
  classes have not been compiled here. Pure-core tests do not establish their
  runtime correctness. No installable plugin binary is included.

Manual IDE acceptance (JDK 25):
1. Run ./gradlew check buildPlugin and ./gradlew verifyPlugin; install 0.1.12.
   Ensure the bundled YAML and Markdown plugins are enabled.
2. Open src/test/resources/syntax/valid.md and choose Treat as Slidev if needed.
   Expect two metadata blocks, YAML token colors and no Slidev syntax warnings.
   Custom fields/layout are allowed. Code/notes examples are excluded.
3. In headmatter type col and invoke Basic Completion; select colorSchema and
   verify insertion of ': '. After colorSchema: invoke completion for its values.
   In the second block, lay offers layout; theme is not offered there.
4. On an existing layout: center line, complete the key and confirm the colon
   and value are preserved. Repeat headmatter completion where Markdown uses
   its own YAML injection. Check an ordinary Markdown/YAML file is unaffected.
5. Open invalid.md and mark it as Slidev. Expect warnings for colorSchema,
   lineNumbers, duplicate nested sans, hide, clicks and the unclosed code fence.
   Change hide: maybe to hide: false; the corresponding warning must disappear.
6. Set a metadata value to an unfinished YAML collection, e.g. fonts: [one, two.
   Confirm a native YAML syntax error is mapped into Markdown. Remove a closing
   --- and confirm the structural warning. Recheck all three editor view modes.

References:
* https://sli.dev/guide/syntax
* https://sli.dev/custom/
* https://github.com/slidevjs/slidev/blob/main/packages/types/src/frontmatter.ts
* https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html


## 0.1.13: Extended completion

* Added built-in component/directive/prop suggestions, tag-pair and quoted-value
  insertion plans, including selected click/marker modifiers. v-clicks remains
  component-only. Registered completion for HTML as well as Markdown and YAML
  to handle native Markdown HTML injection without a Vue plugin dependency.
* Added mapping-aware completion for fonts, drawings, export and defaults,
  including selected nested values. Scalar/sequence bodies are excluded.
* Added bounded local project/theme discovery for layouts/*.vue and components
  (up to five levels and 1000 directory entries per scan). Entry-directory and
  nearest-package project files take precedence over theme files. Supports
  installed @slidev/theme-* and slidev-theme-* packages, explicit package names,
  ancestor node_modules directories and local theme paths. No npm/Node execution
  or package installation takes place. Arbitrary addon/Vite resolvers are not run.
* Completion context masks metadata, code fences, blockquoted code fences,
  indented and inline code, HTML notes, interpolations and script/style bodies.
  Quoted attribute values are excluded; existing attributes before the cursor
  are filtered. This is a lexical helper, not a complete Markdown/Vue parser.
* Metadata type validation remains scoped as in 0.1.12. Adding nested completion
  does not imply a full nested validation schema or dynamic Vue prop inference.

Executed checks:
* PASS: ECJ 3.42.0 / Java 17 compilation of standalone core and Swing code.
* PASS: 43 existing core + 49 metadata/schema + 68 completion/discovery checks.
  Discovery tests use actual temporary directories with project/nested components,
  package and local themes, name collisions, missing themes and cancellation.
  Insertion tests validate pure edit plans; they do not instantiate IDE editors.
* PASS: Swing view, navigation contract fixture and fullscreen DOM fixture tests.
* BLOCKED: bash gradlew check buildPlugin cannot download Gradle 9.1.0:
  Java network request fails with Network is unreachable. Local Java is 17;
  JDK 25 and the IntelliJ SDK are absent. Full SDK compilation, Plugin Verifier,
  popup invocation and actual insertion in injected/normal editors are NOT RUN.

Target-machine acceptance:
1. Build with JDK 25: ./gradlew check buildPlugin and ./gradlew verifyPlugin.
2. In a recognized Slidev deck, invoke Basic Completion after <v-cl and choose
   v-click with Enter/Tab. Verify one pair of tags and the caret between them.
   Repeat before an existing > and before />; preserve existing tag syntax.
3. Confirm a tag using Space and complete its attributes. In <div v- verify
   click/after/mark directives; do not offer v-clicks as an attribute. Verify
   v-clicks offers depth and SlidevVideo offers controls/src. At src, complete
   into src="" with the caret inside, preserving any already existing value.
4. Add layouts/demo.vue and components/MyChart.vue in the presentation project.
   Reinvoke layout value and component completion. Check project provenance and
   discovery from an installed/local theme. No restart should be required.
5. Under fonts:, drawings:, export: and defaults:, indent and invoke completion.
   Check field/value scope, dedent, and absence of root suggestions in text blocks.
6. Repeat in an ordinary Markdown/YAML file, inline/fenced code, speaker notes,
   attribute strings and Vue interpolation: no additional Slidev proposals.

Reference contracts reviewed:
* https://sli.dev/builtin/components
* https://sli.dev/guide/animations
* https://sli.dev/features/rough-marker
* https://sli.dev/custom/config-fonts
* https://sli.dev/custom/
