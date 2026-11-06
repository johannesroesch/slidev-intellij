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


## 0.1.14: Background Vue expression checking

* Added a Markdown ExternalAnnotator. collectInformation snapshots source, entry
  path and the configured Node executable under the IDE read lock; doAnnotate
  runs the subprocess outside it. apply rejects changed source/settings and
  maps native UTF-16 ranges into Markdown. No build/save/server is involved.
* The bundled helper resolves the locally installed @vue/compiler-dom directly
  or through the real Slidev CLI path, including isolated pnpm dependencies.
  It uses Vue's compile API with TypeScript expression support; expressions and
  generated render code are never evaluated. Only selected expression-related
  error codes are emitted, using Vue's exported enum rather than numeric constants.
* Markdown-only ranges are blanked while preserving line endings and UTF-16
  offsets. Template literals in interpolations and bound attribute strings are
  preserved, as is indented template content within HTML. This masking remains
  lexical and is not equivalent to the complete Slidev Markdown transform.
* Process limits: five seconds, 500,000 UTF-16 source characters, 2 MB output,
  200 diagnostics; cancellation kills the process and temporary input/output
  files are removed in finally. Output uses a strict labeled/base64 line protocol.
* Check Vue expressions while typing defaults to true and is configurable in
  Settings > Slidev. Settings application restarts daemon analysis. Missing
  runtime/compiler, timeout or protocol failures are shown as weak warnings,
  rather than silently claiming successful validation.

Executed:
* PASS: ECJ/Java 17 core compilation and all existing core/Swing checks.
* PASS: 33 pure Java expression masking/protocol checks (included in expressionTest
  and the standalone core script), plus six additional real Java->Node subprocess
  checks for diagnostics, offset mapping, missing executable, cancellation and timeout.
* PASS: scripts/test-expressions.cjs against a real @vue/compiler-dom 3.5.22 npm
  installation in a temporary fixture. Sixteen valid/invalid expression cases cover
  TypeScript, events, v-for, slot parameters, v-pre, bound attributes and incomplete
  interpolation. Also validates the -e/stdin protocol, missing compiler response,
  and resolution through a simulated pnpm CLI symlink to its actual dependency store.
* PASS: navigation and fullscreen JavaScript fixtures.
* NOT RUN: SDK-facing classes compiled with JDK 25, Plugin Verifier and native
  ExternalAnnotator/Settings UI runtime in IntelliJ. Gradle remains blocked before
  startup by Java network access to its distribution; local runtime is Java 17.
  Pure Java and real Node tests do not validate the IntelliJ integration.

Manual acceptance:
1. Build and install with JDK 25; enable the live checkbox in Settings > Slidev.
2. In a recognized deck, type {{ 1 + }} and :class="{ broken: }" on an element.
   After the background analysis, expect Vue error annotations on original source.
   Fix the expression and confirm the error disappears without saving/building.
3. Confirm valid @click="count++; go()", v-for and TypeScript expressions pass;
   code examples and notes must not create live expression errors.
4. Disable the checkbox and verify errors disappear. Set an invalid Node path and
   verify the actionable weak warning; restore it and verify analysis recovers.
5. Type rapidly during analysis, close the document/project and check that stale
   annotations and compiler subprocesses do not remain. Check preview behavior.

References:
* https://plugins.jetbrains.com/docs/intellij/syntax-highlighting-and-error-highlighting.html
* https://github.com/vuejs/core/blob/main/packages/compiler-core/src/errors.ts
* https://github.com/vuejs/core/blob/main/packages/compiler-core/src/compile.ts
* https://github.com/vuejs/core/blob/main/packages/compiler-dom/src/index.ts


## 0.1.15: Static project references and declaration targets

* ProjectReferences extracts conventional metadata scalar references, inline
  Markdown images, quoted static HTML image/SVG attributes and video posters.
  Masks examples, notes and interpolations; avoids treating image-like text
  inside attributes as Markdown. Supports spaces/parentheses, basic HTML entities,
  URL decoding, query/fragment suffixes and import slide ranges.
* Resolves imports/assets relative to the current Markdown file. URL-root images
  map to public/ with a containment check; they are not OS absolute image paths.
  Layout lookup prefers entry/project directories, then theme and installed client.
  Client lookup follows the real CLI path for isolated pnpm layouts. Known
  built-ins remain valid without resolved source. Unknown layout warnings explicitly
  qualify the lack of addon/Vite resolver support.
* Added metadata/body reference warnings to the Markdown annotator and a scoped
  GotoDeclarationHandler with injection-to-host offset mapping. Declaration
  navigation returns the target PSI file; it does not mutate files or use indexes.
  Import fragments open the file, not an exact slide range.
* New referencesTest task is required by check/test and the standalone core script.

Executed:
* PASS: 113 reference/target checks with actual temporary files: relative/public
  paths, imports with ranges, Unicode/BOM/CRLF, spaces, plus/percent/hash handling,
  entities, CSS URLs, dynamic/remote exclusions, missing files, root containment,
  layout precedence, local/npm themes, a pnpm-style CLI symlink and cancellation.
  The symlink-only fixture reports SKIP where the host cannot create symlinks.
* PASS: ECJ/Java 17 core and Swing compilation; all prior 43 parser/navigation,
  49 metadata, 68 completion and 33 expression checks; Swing and JS bridge tests.
* NOT RUN: JDK 25 SDK compilation, Plugin Verifier and native warning/Go to
  Declaration behavior in IntelliJ. Gradle remains blocked while fetching its
  distribution from the local Java process. This validation does not establish
  the new SDK-facing navigation handler's runtime behavior.

Manual IDE acceptance:
1. Build/install 0.1.15 with JDK 25. In a recognized Slidev file add a missing
   src reference and missing inline/HTML image. Expect local reference warnings.
2. Create the target files, refresh and re-run highlighting; warnings should clear.
   Put the caret in each path and invoke Go to Declaration (including an existing
   injected YAML headmatter region). Confirm the correct Markdown/image file opens.
3. Test /logo.svg against project/public/logo.svg and layout: my-layout against
   layouts/my-layout.vue. Test project overrides and an installed/local theme.
4. Test a misspelled layout: weak warning with addon/Vite qualification; test
   default/full: no false warning merely because source discovery failed.
5. Confirm URLs, :src bindings, YAML block strings, code examples, notes and CSS
   colors do not produce local-file warnings. Ordinary Markdown is unaffected.

References:
* https://sli.dev/features/importing-slides
* https://sli.dev/features/bundle-remote-assets
* https://sli.dev/builtin/layouts
* https://plugins.jetbrains.com/docs/intellij/intellij-platform-extension-point-list.html

## 0.1.16: Quick fixes, imported decks and preview recovery

* Added guarded native intentions for known metadata values, similar layout names,
  unambiguous fence/frontmatter closure and explicit missing Markdown source creation.
  Existing files are never overwritten; stale text snapshots invalidate fixes.
* Global deck mapping uses the installed @slidev/parser/fs in a limited Node process.
  Supports both the 52.14.1 string-root API and 52.20.1 options-root API. Actual CLI
  source confirms virtual IDs contain the source filename and global slide number.
  Import ranges are selected before recursive expansion; hidden/disabled slides and
  repeated imports follow the native parser. Root imports use the entry directory.
* Unsaved source overrides, missing-source watches, cancellation, generation checks,
  workspace associations and source-editor caching connect the model to the editor.
  Code switches sources inside a stable Swing host, without replacing the browser.
  Error-list targets include the actual source file. Build line mapping remains a
  slide-relative heuristic without transform source maps.
* Mapping has timeout/size/depth limits and reports unavailable dependencies, cycles,
  missing sources and detected entry-level custom preparsers. Theme/addon preparsers
  and dynamic Vite resolvers are not executed and can still cause differing mappings.
* Show Slidev log opens a modeless window sharing the recent session output, even
  while the inline console is hidden. Restart preview is reachable in the toolbar
  and log window. It closes the previous session, saves participating documents and
  starts a new session after mapping, retaining earlier output with start markers.
  Initialization exceptions also include their stack trace in this visible log.
  There is no automatic crash restart loop and no persisted log archive.

Executed:
* PASS: ECJ/Java 17 compilation of all core tests and the real Swing view.
* PASS: existing 43 parser/navigation, 49 metadata, 68 completion and 113 reference
  checks; 39 expression checks including six actual Node subprocess cases.
* PASS: 21 pure quick-fix/graph tests plus seven actual Java-to-Node parser tests
  (28 total), separately against installed parser 52.14.1 and 52.20.1.
* PASS: scripts/test-imports.cjs against both parser versions, including nested/ranged
  imports, repeats, hidden slides, dirty overrides, missing files, cycles, custom
  preparser detection and the actual subprocess protocol.
* PASS: actual Vue compiler fixture, navigation/fullscreen fixtures and headless
  Swing checks including source-child replacement with stable browser ancestry.
* BLOCKED: bash gradlew check buildPlugin fails downloading Gradle 9.1.0 with
  java.net.SocketException: Network is unreachable. Local Java is 17, not JDK 25.
  No SDK compilation, Plugin Verifier or native IntelliJ/JCEF lifecycle test ran.
  One concurrent JVM launch failed in PerfMemory initialization; rerunning that
  parser check with -XX:-UsePerfData passed. This was before test code execution.

Target-machine acceptance:
1. Build/install with JDK 25 and run verifyPlugin for the configured IDE versions.
2. Use Alt+Enter on invalid metadata, a misspelled layout and unclosed blocks. Check
   comments/CRLF, single undo and absence of speculative closure for ambiguous text.
3. Create a missing static src Markdown file with Alt+Enter, including directories;
   check undo and no replacement of an existing target. Verify mapping recovers.
4. Import a child twice, include nested imports and #2,1,2 ranges, hide a slide.
   Confirm global outline order, source filenames and bidirectional navigation.
   Reverse navigation must retain browser focus and not reload the preview.
5. Edit an imported source unsaved, switch back with Entry source and run Check build.
   Verify HMR, cross-file error navigation and stale diagnostic removal. Save/reopen
   a child to check its stored entry association; test independent Treat as Slidev.
6. Keep Show debug console off. Open Show Slidev log in Code/Split/Preview modes;
   trigger a missing CLI and a syntax-related server exit. Verify stdout/stderr and
   an actionable status remain available after failure. Fix the error, select
   Restart preview in both the toolbar and log window, and confirm a new session
   starts while earlier output remains. Check repeated restart and close cleanup.
7. Verify source edits, rapid typing during mapping/restart, modal dialogs, file
   deletion/recreation, Node setting changes and project disposal leave no stale
   results, process leaks or write-unsafe operations.

## 0.1.17: Live preview diagnostics and recovery actions

* Added an incremental, bounded PreviewDiagnostics parser for actual Vite dev-server
  Internal server error / Pre-transform error blocks and recognized startup formats.
  A header is immediately visible without inventing coordinates; a following File
  record replaces it with the reported position. Repeated reports at the same source
  location are consolidated. Stack frames and code frames are never source locations.
* Virtual IDs map through the existing authoritative deck snapshot, matching global
  slide number plus source filename. Direct Markdown coordinates map only into known
  deck sources. Unknown files, components, missing/mismatched mappings and positionless
  errors stay visible without guessed highlights. Mapping remains slide-relative,
  without transform source maps, and retains the reported column convention.
* Preview/build issue collections are independent and merged for display, so one
  producer does not erase the other's results. The diagnostics footer is outside
  the preview-only component and remains reachable in Code/Split/Preview modes.
  Reported errors and session/browser failures expose Open log / Restart preview.
* Markdown document/VFS changes invalidate old preview diagnoses. Queued lines have
  generation/session guards; lines received while inputs are dirty are log-only.
  Saving a previously unknown dirty import also invalidates pre-save diagnostics.
  New mappings require matching document snapshots. The CLI text stream has no
  compile-cycle identifier, so this is not a guaranteed structured event protocol.
* Restart invalidates the old diagnostic stream and retains the session log. Fatal
  failures remain actionable after editor changes; successful page loading clears
  a browser-level failure, while compilation errors remain independently visible.
  No automatic restart loop, new server implementation or library adapter was added.

Executed:
* PASS: ECJ/Java 17 compilation of all core classes/tests and the Swing view.
* PASS: 24 pure live diagnostic tests: incremental messages, repeated errors, startup
  formats, ANSI, Windows/spaced paths, Vite query suffixes, ignored stack frames,
  invalid/overflow coordinates, bounded history and imported CRLF/Unicode mapping.
* PASS: scripts/test-preview-diagnostics.cjs using installed Vite 7.3.1 / 8.3.3,
  @vitejs/plugin-vue 6 and Vue 3.5.22. Real HTTP module requests produce a 500 and
  Vite/Vue diagnostic. After a watched source correction the same server returns 200.
  Both captured logs then pass four additional Java assertions (28 total).
  These are Vite/Vue fixtures, not full Slidev or IntelliJ/JCEF end-to-end tests.
* PASS: existing 43 parser, 49 metadata, 68 completion, 33 expression, 113 reference
  and 21 quick-fix/deck checks; Swing layout and navigation/fullscreen JS fixtures.
* BLOCKED: bash gradlew check buildPlugin fails before compilation while downloading
  Gradle 9.1.0 with java.net.SocketException: Network is unreachable. Only Java 17 is
  installed here. SDK compilation, Plugin Verifier and native IDE runtime are NOT RUN.

Target-machine acceptance:
1. Build/install with JDK 25, run verifyPlugin, open a real Slidev project.
2. In a visible slide type {{ 1 + }}. Wait for autosave/HMR and verify a [Preview]
   issue, direct recovery buttons and a matching underline. Click the issue.
3. Repeat in an imported Markdown source and with repeated imports. Verify the
   correct source opens and positions retain the slide-relative qualification.
4. Correct the expression. Old markers must disappear on the source edit, with
   no claim that all slides have passed. Reintroduce the error and verify fresh
   diagnostics. Type quickly while output is queued and before an initial mapping.
5. Run Check build while live errors exist. Verify [Build] and [Preview] entries
   remain independent, including cancellation and subsequent edits.
6. Repeat with Code/Split/Preview and debug console disabled. Trigger a CLI/startup
   failure and a browser HTTP error; Open log and Restart preview remain reachable.
   Restart repeatedly and ensure older-session output never recreates current marks.
7. Test errors without locations and errors in .vue components: preserve reported
   details/logs without falsely assigning a Markdown offset. Confirm cleanup on
   editor/project close and stable preview hierarchy during file/ansicht changes.

Optional real-server checks (requires separately installed fixture dependencies):
  node scripts/test-preview-diagnostics.cjs /path/to/vite-project /tmp/vite-errors.log
  java -ea -cp <test-runtime-classpath> dev.slidev.intellij.core.PreviewDiagnosticsTest /tmp/vite-errors.log

## 0.1.18: Optional Slidev inspect

* Added a persisted Enable Slidev inspect (preview) setting, false by default.
  Settings events include the option and restart existing preview sessions when
  it changes, using the existing non-modal restart path and keeping session logs.
* SlidevServer adds --inspect as a distinct ProcessBuilder argument only when
  enabled. No shell command interpolation, package installation or configuration
  mutation is added. The explicit Check build workflow is unchanged.
* Open Inspect is visible only while the option is enabled and becomes enabled
  after the corresponding server reports readiness. It opens that server's
  /__inspect/ route via IntelliJ BrowserUtil in the external browser, preserving
  the embedded presentation view. Server shutdown disables the action.
* Reviewed shipped CLI 52.14.1 / 52.20.1 packages: both expose --inspect and use
  vite-plugin-inspect; dependencies are ^11.3.3 and ^12.0.2 respectively.
  The latter requires Vite DevTools. UI hints and README document the required
  project dependency/configuration and preserve the upstream authentication gate.

Executed:
* PASS: scripts/test-inspect.cjs with actual vite-plugin-inspect 11.4.1 / Vite 7.3.1:
  module transform and HTTP 200 inspector HTML at /__inspect/, distinct from the
  presentation fallback page.
* PASS: same fixture with actual inspect 12.0.2 / Vite 8.3.3 / @vitejs/devtools 0.7.2
  and devtools:true: /__inspect/ redirects to the inspect client; inspector HTML
  and the DevTools connection-metadata endpoint are served. Inspect fixtures use a
  temporary outputDir: with build:true (as used by Slidev), server close may also
  write report assets. The README documents upstream .vite-inspect output. Authentication is not
  disabled. This validates endpoint contracts, not interactive browser pairing.
* BLOCKED: bash gradlew check buildPlugin still fails while downloading Gradle
  with java.net.SocketException: Network is unreachable. JDK 25 / IntelliJ SDK
  compilation, Plugin Verifier and native Settings/BrowserUtil runtime NOT RUN.
  The fixtures use real Vite/Inspect plugins, not a full Slidev IDE integration.

Manual acceptance:
1. Build/install with JDK 25. Open a deck. Inspect defaults to off, no Open Inspect
   button is shown and the CLI command does not include --inspect.
2. Enable the checkbox under Settings > Slidev and close settings. Verify a running
   preview restarts once and preserves its earlier log. Open Inspect is disabled
   while starting, then opens the same port's /__inspect/ in the external browser.
3. Check Code/Split/Preview, a hidden running tab, session failure and manual restart.
   The embedded presentation must not navigate to the inspector. Stop/close cleanup
   disables the action, and a restarted session follows the current setting.
4. With Inspect 12, install a compatible @vitejs/devtools and set devtools:true in
   the project's existing Vite config. Complete any upstream browser pairing using
   Show Slidev log. No authentication bypass is introduced by the plugin.
5. Disable the checkbox: a running session restarts without --inspect and the action
   disappears. Reopen the project to verify persisted state. Check that ordinary
   Check build does not generate a new inspect report merely from this setting.

Optional fixture commands:
  node scripts/test-inspect.cjs /path/to/vite7-inspect11-project
  node scripts/test-inspect.cjs /path/to/vite8-inspect12-devtools-project devtools

## 0.1.19: Process lifecycle and full Slidev acceptance fixture

* Moved the process implementation into core.PreviewServer with an injected parallel
  executor. SlidevServer is a thin IntelliJ thread-pool adapter; production and the
  standalone process tests use the same process/HTTP/log/termination code.
* Found and fixed a race: server exit could notify the UI before its reader had
  consumed the final stdout/stderr. Exit/startup-failure paths now wait up to five
  seconds for the stream to drain. Repeated close requests are idempotent.
* Real Slidev 52.20.1 revealed a gap missed by the earlier Vite-only fixtures:
  the imported Markdown expression on original line 3 is reported on generated
  line 4 with an added <p> wrapper. Live diagnostics now retain the compiler code
  excerpt and match it to one source line in the validated target slide. Column
  offsets account for generated HTML wrappers and original indentation. Changed,
  ambiguous or non-wrapper partial matches stay unmapped. This is not Source Maps.
* Added a GitHub Actions workflow with JDK 25, Node 24, SDK builds/checks for both
  configured IDE targets, full Slidev fixtures and Plugin Verifier. It uploads
  plugin ZIPs/reports when present and has read-only repository permissions.
  Workflow configuration alone does not establish successful CI results.

Executed locally:
* PASS: ECJ/Java 17 compilation of all core classes and standalone tests.
* PASS: 31 live diagnostic checks, including incremental compiler excerpts, generated
  line/HTML-column shifts, repeated text, changed text and rejected partial matches.
* PASS: 30 real subprocess/HTTP/lifecycle checks, separately with complete installed
  Slidev 52.14.1 and 52.20.1 CLI/theme: inspect argument propagation through a fake CLI fixture, paths
  with spaces, /1 vs root, startup/session crashes, complete stderr/stdout tail,
  restart retaining prior log, repeated close, descendant cleanup, cancellation,
  dependency errors, and actual imported-slide transform failure + correction in
  the same real Slidev server. The actual module is fetched through Slidev's facade,
  using its emitted source import rather than an invented virtual request path.
* PASS: existing 43 parser, 49 metadata, 68 completion, 33 expression, 113 reference
  and 21 quick-fix/deck checks, plus headless Swing and browser bridge fixtures.
* BLOCKED locally: only Java 17, no JDK 25/IDE SDK or visible native IDE. Correcting
  the Java proxy options still gives Connection refused. A curl HEAD can reach the
  distribution but does not establish that Gradle dependency resolution works.
  No native IDE installation, highlighting click, focus, modeless log-window or
  Inspect browser-opening test has been performed here.

The CI workflow and its concrete run results must be inspected separately. Native
IntelliJ/JCEF acceptance cases from 0.1.16-0.1.18 remain open even if CI passes.

CI run 42187798334 on commit 957e0c931959636b7e21ed1973939e3b086f0de7:
* PASS: actual JDK 25 SDK compilation, check and buildPlugin for IntelliJ 2026.2.3
  and 263.7307.112. Both jobs include all core tests and real Slidev fixtures.
* FAILED before compatibility analysis: Plugin Verifier rejects the word intellij
  in dev.slidev.intellij and IntelliJ in Slidev for IntelliJ. Scheduled API
  verifications: zero. This was a descriptor validation failure, not API proof.
* Fixed descriptor/Gradle display name to Slidev and plugin ID to dev.slidev.editor,
  without muting the verifier findings. README documents removal of the previous
  plugin before installing this new identity and the remaining workspace migration
  acceptance test. Compatibility verification must be rerun on this corrected ZIP.

Corrected CI run 42189051108 on code commit
663f09fc8e935ce5e39a00a195f3b5179e768d0c:
https://github.com/johannesroesch/slidev-intellij/actions/runs/42189051108
* PASS: check/buildPlugin for IntelliJ 2026.2.3 and 263.7307.112 with JDK 25.
* PASS: all core/Swing/real-process tasks in both jobs; full Slidev fixtures use
  52.14.1 with the baseline and 52.20.1 with the 263 build.
* PASS: verifyPlugin scheduled and completed both actual API verifications:
  IU-262.10968.63: Compatible, 3 deprecated API usages, 4 experimental usages.
  IU-263.7307.112: Compatible, 4 deprecated API usages, 4 experimental usages.
  Experimental usages include WriteIntentReadAction. These warnings were not
  muted and remain maintenance work for future IDE versions.
* Artifacts contain installable 0.1.19 ZIPs and reports. The baseline artifact
  slidev-intellij-2026.2.3 includes both verification reports. Extract the inner
  distributions/slidev-intellij-0.1.19.zip before installing.
* NOT RUN: visible IntelliJ/JCEF acceptance (focus, live highlights, modeless log
  window, Inspect browser opening and workspace migration from the previous ID).
  Headless Swing, subprocess tests and Verifier do not establish these behaviors.

The following documentation-only commit records these results without changing
the verified production/test/workflow code or rebuilding the same artifact.

## 0.1.20: Vue components, layouts and theme source navigation

* Local Vue targets are resolved separately from deck numbering. Added bounded
  normalized UTF-8 snapshots (BOM/CRLF to native document text), strict offsets,
  file URI/Vite fs paths and a conservative compiler-message correlation.
* Full Slidev revealed another offset trap: context injection prepends/inserts
  code into .vue files as a plain string without a Source Map. A template error
  on original line 2 is logged on generated line 7 without a code frame. Therefore
  bare .vue filenames/coordinates are NOT assumed to be original coordinates.
* The helper uses the presentation's installed vue/compiler-sfc, consistent with
  Vite's root compiler lookup and real-CLI fallback. It parses original templates
  and isolated JS/TS script blocks, never executing component expressions. Babel
  parser offsets are translated from block content to original UTF-16 offsets.
  Only a single matching original compiler message or unique excerpt is mapped.
  Ambiguous errors and missing evidence retain file navigation without a marker.
* Vue build output is parsed along with the existing Markdown build diagnostics.
  Build results retain their detailed compiler excerpts for later asynchronous
  source resolution. Preview/build Vue issues open via FileEditorManager and
  OpenFileDescriptor, using the normal editor rather than the Slidev code host.
* Native document text must match the complete snapshot before applying markers
  or navigating. Document/VFS edits invalidate tracked Vue diagnostics and build
  results. Editor-created/released events update markers across native editors.
  Background loads are revision/request guarded, canceled on disposal/settings
  changes, limited to 32 active sources and four tasks. Initial VFS discovery is
  distinguished from actual source creation/modification to avoid discarding a
  fresh build result merely because a source had not been indexed yet.

Executed locally:
* PASS: ECJ/Java 17 compilation of all core classes/tests and 25 pure Vue source
  checks (normalization, stale snapshots, path forms, strict positions, bounds,
  direct/generated excerpts, ambiguity and compiler-message matching).
* PASS: 49 combined checks separately with full Slidev 52.14.1 and 52.20.1. Real
  HTTP requests trigger template/directive errors in components/layouts and TS
  script errors both before and after a template with Unicode. The original
  Vue compiler recovers original line positions; corrections compile in the
  same server. Full CLI builds also produce a Vue error that is mapped correctly.
* Observed: failed Vue transforms return no current inline Source Map/error map
  in the standard HTTP/CLI diagnostic channel. No stale success map is reused.
  Arbitrary transform Source Maps are not implemented by this change.
* PASS: all existing core suites, 30 real preview lifecycle/Slidev checks, Swing
  layout and navigation/fullscreen browser fixtures.
* SDK compilation/Verifier and native Vue editor navigation/highlighting must be
  validated separately. Local Java remains 17; SDK validation runs on GitHub.

Manual IDE acceptance:
1. Build/install 0.1.20. Use the existing dev.slidev.editor identity. No extra ID
   migration is required from 0.1.19; older identities still need removal.
2. Put {{ 1 + }} into components/Bad.vue used by a slide. Wait for a preview
   error, select its issue, and verify the normal Vue editor opens at the actual
   source line with a marker. Return to the Slidev tab without restarting it.
3. Repeat for a custom layout and an installed/local theme Vue source, including
   TS syntax errors and paths with spaces. Check build must navigate likewise.
4. Edit an affected Vue document without saving: old markers and warnings clear;
   no older saved-source coordinates reappear in the dirty document. Save, trigger
   compilation and check fresh diagnostics. Rename/delete/recreate the file.
5. Open a Vue editor before and after the diagnostic arrives; verify markers,
   split editor views, close cleanup and unchanged unrelated editor markup.
6. Verify ambiguous/generated errors, unavailable compilers, read-only theme
   files, rapid changes, Node settings changes and project/editor disposal. File
   navigation must remain useful without a guessed offset or stale highlight.

CI confirmation for 0.1.20:
https://github.com/johannesroesch/slidev-intellij/actions/runs/42196470297
Verified code commit: 504b389a1b9871faa485217ff6715609e63f98e8.
* PASS: check/buildPlugin with JDK 25 on IntelliJ 2026.2.3 and 263.7307.112.
* PASS: 49 Vue checks in each matrix job, with actual CLI 52.14.1 / 52.20.1, plus
  all existing parser, metadata, completion, references, preview and Swing suites.
* PASS: both actual Plugin Verifier analyses completed:
  IU-262.10968.63: Compatible, 3 deprecated and 4 experimental API usages.
  IU-263.7307.112: Compatible, 6 deprecated and 4 experimental API usages.
* NOT RUN: interactive native editor navigation/highlights/focus and JCEF UI.
  SDK compilation, headless checks and Verifier do not replace those tests.

The documentation-only follow-up records the successful run without changing the
verified production/test/workflow code.

## 0.1.21 dynamic lifecycle

Verified code/test/workflow commit: 683582e674f721290106238cf6816ee4cc45fb03.
CI: https://github.com/johannesroesch/slidev-intellij/actions/runs/42204804441

* PASS: check/buildPlugin on IntelliJ 2026.2.3 and 263.7307.112 with JDK 25.
* PASS: two actual SDK lifecycle tests on each target, zero failures/errors/skips.
  They check dynamic flags for all registered extension points in the appropriate
  application/project area and editor disposal with document retention.
* PASS: 9 TaskScope checks, including interruption, rejected late work, cleanup
  draining, context classloader and inherited thread-local isolation.
* PASS: 35 process checks per CI matrix job, including actual Slidev and an unload
  sequence that drains workers and terminates parent/descendant Node processes.
* PASS: 49 Vue checks per matrix job plus all existing core/Swing suites.
* PASS: Plugin Verifier reports Compatible for both targets:
  IU-262.10968.63: 4 deprecated and 4 experimental API usages.
  IU-263.7307.112: 8 deprecated and 4 experimental API usages.
* NOT RUN: interactive native JCEF update/unload/reload and actual collection of
  the old plugin classloader. The SDK tests exercise our lifecycle callback, not
  a complete platform plugin replacement. Dynamic flags and compatibility do not
  prove that native unload is successful.

Local pure suites and 27 standalone real-process checks also pass. Earlier
shutdown attempts failed process cleanup and were corrected before acceptance.
The final executor cancels analysis Futures while draining session/termination
operations. JCEF/browser/query disposal now goes through the platform Disposer.

Test environment: the 262 product's unrestricted bundled plugins failed during
startup in an unrelated obfuscated Ultimate extension with an invalid constructor.
The 262 test JVM therefore explicitly selects Slidev, platform, Markdown, YAML and
JCEF dependencies. The 263 product keeps its default module selection because
that filter excluded required plugin services there. No assertion/error logging
is muted. Full exception output is enabled for future SDK test failures.

A first update from 0.1.20 or older can still require restart because the old
version performs its own unload. Runtime transfer stores file URL strings in a
platform-owned PropertiesComponent, not plugin objects or editor instances.
The documentation-only follow-up records this successful run without changing
verified production/test/workflow code.
