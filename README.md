# Slidev for IntelliJ, Prototyp 0.1.21

Dieses Projekt ergänzt IntelliJ um einen Slidev-Editor mit Folienübersicht, einer echten Slidev-Vorschau und einem Präsentationsfenster.

## Status

Dies ist ein Quellcode-Prototyp. Für Version 0.1.20 sind die SDK-Builds mit JDK 25, `check`, `buildPlugin` und Plugin Verifier auf beiden Zielplattformen bestätigt: [erfolgreicher CI-Lauf](https://github.com/johannesroesch/slidev-intellij/actions/runs/42196470297). Der Verifier bewertet beide Ziele als **Compatible**, mit Hinweisen auf veraltete und experimentelle APIs. Die Vue-Prüfungen bestehen gegen vollständige Slidev-Installationen 52.14.1 und 52.20.1. Ein sichtbarer IntelliJ-/JCEF-Akzeptanztest, einschließlich nativer Vue-Navigation und Markierungen, steht weiterhin aus.

Der Quellcode im Repository ist **kein installierbares IntelliJ-Plugin**. Erst `buildPlugin` erzeugt das installierbare Paket.

## Updates ohne IDE-Neustart

Version 0.1.21 ergänzt die Vorbereitung für dynamische Pluginupdates. Vor dem Entladen werden die Slidev-Tabs geschlossen, Präsentations- und Logfenster sowie JCEF und Listener freigegeben, Preview-/Build-Prozesse beendet und eigene Hintergrundaufgaben abgebrochen. Dokumente werden dabei nicht verworfen. Bei einem Update werden die betroffenen Dateien anschließend erneut geöffnet. Beim Deaktivieren oder Deinstallieren werden sie nicht automatisch wieder geöffnet.

Die alte Version führt das Entladen aus. Beim ersten Wechsel von 0.1.20 oder älter auf 0.1.21 kann deshalb weiterhin ein Neustart erforderlich sein. Erst spätere Updates können die neue Aufräumlogik nutzen. Ein Neustart bleibt außerdem möglich, wenn IntelliJ das Entladen aus anderen Gründen ablehnt. Die genaue Ursache steht im IDE-Log unter `com.intellij.ide.plugins.DynamicPlugins`; unsere Vorbereitung wird zusätzlich als `Slidev unload` protokolliert. Der komplette sichtbare Updatezyklus mit aktivem JCEF muss noch in einer laufenden IDE geprüft werden. Die bisherigen grünen CI-Ergebnisse unten beziehen sich auf 0.1.20, bis ein neuer Lauf bestätigt ist.

## Zielplattform und Voraussetzungen

* IntelliJ IDEA 2026.2 oder 2026.3, lokal auf dem Desktop, mit JetBrains Runtime und JCEF.
* Die mitgelieferten Plugins Markdown und YAML müssen aktiviert sein.
* Keine Abhängigkeit von Ultimate-, JavaScript- oder Vue-Plugins. Der Betrieb im kostenlosen IntelliJ-Modus ist das Entwicklungsziel und muss noch praktisch geprüft werden.
* JDK 25 für den Plugin-Build. In IntelliJ unter den Gradle-Einstellungen als Gradle JVM auswählen.
* Node.js passend zur verwendeten Slidev-Version.
* `@slidev/cli` und die Theme-Abhängigkeiten müssen im Präsentationsprojekt installiert sein.

Der deklarierte Kompatibilitätsbereich umfasst Build 262 bis 263.*. Die Standard-Build-Basis bleibt IntelliJ IDEA 2026.2.3, damit das Plugin keine neueren APIs voraussetzt. Als zusätzliches Prüfziel ist der 2026.3-RC mit Build 263.7307.112 hinterlegt. Build und Plugin Verifier sind für diese Ziele im verlinkten CI-Lauf erfolgreich. Der sichtbare Laufzeittest auf 2026.3 steht weiterhin aus. Ältere separate Community-Versionen, Remote Development, WSL und JetBrains Gateway sind noch kein unterstütztes Testziel.

## Wechsel von einer älteren Plugin-Version auf 0.1.19

Der Plugin Verifier lehnt `IntelliJ` als Wort im Plugin-Namen und in der Plugin-ID ab. Ab 0.1.19 heißt das Plugin deshalb **Slidev** und verwendet die ID `dev.slidev.editor` anstelle von `dev.slidev.intellij`.

Vor dem Installieren der neuen ZIP die bisherige Version **Slidev for IntelliJ** unter **Settings > Plugins** entfernen und den erforderlichen IDE-Neustart durchführen. Danach die neue ZIP über **Install Plugin from Disk** installieren. Wegen der geänderten ID behandelt IntelliJ sie als eigenes Plugin. Beide Versionen dürfen nicht gleichzeitig aktiviert sein, da sie dieselben Editoren und Erweiterungen registrieren. Die Paketnamen und das Format der Projekteinstellungen bleiben gleich; die Übernahme bestehender Workspace-Einstellungen ist noch nativ zu prüfen.

## Automatische Validierung ab 0.1.19

Der Workflow [Validate plugin](https://github.com/johannesroesch/slidev-intellij/actions/workflows/validate.yml) ist für Pushes auf `main`, Pull Requests und manuelle Starts eingerichtet. Er baut mit JDK 25 gegen die konfigurierte IntelliJ-2026.2-Basis und das hinterlegte 2026.3-Prüfziel. `check buildPlugin` sowie Plugin Verifier laufen ohne Veröffentlichung in einem Plugin-Marktplatz. Plugin-ZIPs und Prüfberichte werden als Workflow-Artefakte bereitgestellt, soweit sie erzeugt wurden. Für das reguläre Paket das Artefakt **slidev-intellij-2026.2.3** herunterladen und daraus `distributions/slidev-intellij-0.1.20.zip` entnehmen. Die äußere Workflow-Artefakt-ZIP enthält zusätzlich Berichte und ist nicht direkt als Plugin installierbar. Der oben verlinkte Lauf für 0.1.20 ist erfolgreich; für spätere Änderungen jeweils den konkreten Lauf und seine Ergebnisse prüfen.

Die Prozessprüfungen starten echte Node-Unterprozesse und lokale HTTP-Server. Sie prüfen Startfehler, stdout/stderr, vollständige letzte Fehlermeldungen vor einem Abbruch, Neustart, deaktiviertes Inspect sowie das Beenden von Kindprozessen. Dafür muss Node.js im Build-PATH stehen. Mit `-PslidevTestNode=/absoluter/pfad/node` lässt sich ein anderer Pfad verwenden. Für den optionalen Test gegen ein installiertes vollständiges Slidev-Projekt:

```bash
./gradlew previewServerTest -PslidevTestProject=/pfad/zum/slidev-projekt
```

Der Workflow installiert dafür isolierte Testumgebungen mit Slidev 52.14.1 beziehungsweise 52.20.1. Dieser Test ruft die echten Module einer importierten Folie ab, erzeugt einen Vue-Syntaxfehler, ordnet den Compiler-Ausschnitt der Originaldatei zu und prüft die Korrektur im selben Serverprozess. Das ersetzt keinen sichtbaren IntelliJ-/JCEF-Test von Fokus, Markierungen, Logfenster und Browseröffnung.

## Projekt bauen und testen

Das entpackte Verzeichnis `slidev-intellij` als Gradle-Projekt öffnen. Ein JDK 25 auswählen und den Gradle-Import abschließen. Die erste Ausführung benötigt Internetzugang für Gradle und IntelliJ-Abhängigkeiten.

```bash
./gradlew check buildPlugin
```

Windows:

```powershell
.\gradlew.bat check buildPlugin
```

Das installierbare Plugin liegt anschließend in `build/distributions/`. In IntelliJ unter **Settings > Plugins > Zahnrad > Install Plugin from Disk** die dort erzeugte ZIP auswählen.

Für eine separate Testinstanz der IDE:

```bash
./gradlew runIde
```

Die binäre Kompatibilität gegen die Build-Basis und IntelliJ 2026.3 prüfen:

```bash
./gradlew verifyPlugin
```

Eine Testinstanz des hinterlegten 2026.3-RC starten oder direkt dagegen bauen:

```bash
./gradlew -PplatformVersion=263.7307.112 runIde
./gradlew -PplatformVersion=263.7307.112 check buildPlugin
```

Für einen neueren 2026.3-Build können `platformVersion` und das Verifier-Ziel `verificationVersion` überschrieben werden. `verifyPlugin` benötigt zusätzliche IDE-Downloads und ersetzt keinen praktischen Test der JCEF-Vorschau.

Die Parser-Checks können unabhängig von IntelliJ mit einem JDK ab Version 17 laufen:

```bash
bash scripts/test-core.sh
```

Zusätzlich führt `viewTest` eine Prüfung der tatsächlichen Swing-Aufteilung ohne grafische Oberfläche aus: initiale Vorschaugröße, alle drei Modi, stabile Browser-Hierarchie, Split-Breite und Größenänderungen.

Die Parser- und Diagnosechecks laufen als eigenständiges Java-Programm über `coreTest`, ohne JUnit. Sowohl `check` als auch `test` führen diese Prüfungen sowie `syntaxTest` für Metadatenbereiche, Completion-Kontext und das offene Slidev-Schema aus. Die reguläre `test`-Task erlaubt deshalb eine leere Testframework-Erkennung; fehlschlagende Parser-Checks brechen den Build weiterhin ab.

## Eine Präsentation öffnen

Eine Datei wird als Slidev-Kandidat erkannt, wenn mindestens eines dieser Merkmale vorhanden ist:

* Sie wurde über **Treat as Slidev** ausdrücklich zugeordnet.
* Sie enthält den Kommentar `<!-- slidev -->`.
* Sie besitzt einen ersten YAML-Block mit einem der Felder `theme`, `slidev`, `drawings`, `presenter` oder `transition`.
* Sie heißt `slides.md` und in einer übergeordneten `package.json` steht `@slidev/cli`.

Diese automatische Erkennung ist eine Heuristik. Bei anders benannten Einstiegsdateien ohne eindeutiges Merkmal die Datei im Editor oder Projektbaum mit der rechten Maustaste anklicken und **Treat as Slidev** auswählen. Die Zuordnung wird im Workspace gespeichert. Bereits geöffnete Dateien nach der Installation oder bei Änderungen der Erkennungsmerkmale schließen und neu öffnen.

Das Plugin übernimmt nicht pauschal alle Markdown-Dateien. Nach dem Auflösen eines Hauptdecks werden seine importierten Markdown-Dateien im Workspace diesem Einstieg zugeordnet. Eine ausdrückliche Zuordnung über **Treat as Slidev** behandelt eine Datei weiterhin als unabhängigen Einstieg. Wird dieselbe Datei von mehreren Decks importiert, gilt beim separaten Öffnen die zuletzt gespeicherte Zuordnung.

## Präsentation öffnen und Vorschau verwenden

1. Das Präsentationsprojekt mit seinen vorhandenen Abhängigkeiten vorbereiten, zum Beispiel mit `npm install` im Verzeichnis seiner `package.json`.
2. Die Slidev-Einstiegsdatei öffnen.
3. Den Slidev-Editor-Tab auswählen. Die Vorschau startet automatisch.
4. Die Vorschau erscheint neben dem Quelltext. Status und Build-Fehlerliste bleiben sichtbar; die Debug-Konsole ist standardmäßig ausgeblendet.

Der Prototyp sucht vom Verzeichnis der Einstiegsdatei aus aufwärts nach `node_modules/@slidev/cli/bin/slidev.mjs`. Er führt diese CLI direkt mit Node.js aus. Das funktioniert auch mit einem üblichen pnpm-Verzeichnis mit Symlinks. Yarn Plug'n'Play, globales Slidev und benutzerdefinierte Startkommandos sind noch nicht unterstützt.

Unter **Settings > Slidev** lässt sich der Node.js-Pfad einstellen. Scheitert der automatische Start, **Show Slidev log** öffnen, den Fehler beheben und **Restart preview** auswählen. Eine Änderung des Node.js-Pfads in den Einstellungen startet eine fehlgeschlagene Sitzung nach Schließen des Einstellungsdialogs ebenfalls erneut. Auf macOS empfiehlt sich ein absoluter Pfad, etwa `/opt/homebrew/bin/node`, sofern Node dort installiert ist. Eine aus dem Dock gestartete IDE hat häufig einen anderen PATH als das Terminal. `node` darf verwendet werden, wenn der IDE-Prozess ihn auflösen kann.

Die CLI erhält die Einstiegsdatei und einen frei gewählten Port. Slidev verwendet seine normale Projektkonfiguration einschließlich des Themes. Es wird kein Remote-Modus aktiviert. Der Server startet automatisch, sobald der ausgewählte Slidev-Editor-Tab tatsächlich sichtbar ist. Serverbereitschaft wird als `Slidev server ready. Loading preview…` angezeigt. Erst nach dem Laden folgen `Preview loaded` beziehungsweise `Preview running`; Chromium-Ladefehler erscheinen als eigene Statusmeldung. Die Buttons Start preview, Stop und Present sind entfernt. Der erste Serveraufbau kann bis zu 90 Sekunden benötigen.

Ab Version 0.1.6 werden Timer-basierte Modellaktualisierungen und Speicheroperationen über `Application.invokeLater` mit explizitem `ModalityState.nonModal()` und Write-Intent-Zugriff ausgeführt. Während eines modalen Dialogs wird das Speichern verschoben. Bereits eingeplante Timer-Aktualisierungen werden übersprungen, wenn inzwischen weitere Eingaben erfolgt sind. Der ursprüngliche direkte Swing-Timer-Aufruf konnte den Fehler `Write-unsafe context` verursachen und Hot Reload verhindern.

Beim laufenden Server speichert der Editor das aktuelle Dokument etwa 700 ms nach Änderungen. Slidevs Hot Reload übernimmt anschließend die Aktualisierung. Es werden nicht pauschal alle offenen Dokumente gespeichert. Das Schließen des Slidev-Editors beendet seinen Server und dessen Kindprozesse. Beim Wechsel auf einen anderen Editor-Tab läuft die Sitzung weiter, damit ihre Folien- und Animationszustände erhalten bleiben. Mehrere geöffnete Decks besitzen getrennte Server.

## Code, Split und Preview

Die drei Symbolbuttons oben rechts wechseln zwischen:

* **Code:** ausschließlich Quelltext.
* **Split:** Quelltext und Vorschau nebeneinander (Standard beim Öffnen).
* **Preview:** ausschließlich Vorschau.

Die Buttons besitzen Tooltips und zugängliche Namen. Die Umschaltung bleibt in allen Ansichten erreichbar. Editor und Browser werden dabei weiterverwendet; es gibt keinen Neustart und kein Neuladen. Ab Version 0.1.10 behalten beide Bereiche beim Moduswechsel denselben Swing-Elterncontainer. Die erste Split-Ansicht reserviert explizit etwa die Hälfte des Platzes für die Vorschau, unabhängig von den bevorzugten Größen des Texteditors und Browsers. Die Split-Breite bleibt während der geöffneten Sitzung erhalten. Die Vorschau läuft auch im Code-Modus weiter. **Check build**, **Show Slidev log**, **Restart preview** und **Entry source** stehen unabhängig vom Ansichtsmodus oben links zur Verfügung.

## Slidev Inspect ab 0.1.18

Unter **Settings > Slidev > Enable Slidev inspect (preview)** wird der Startparameter `--inspect` für die Vorschau aktiviert. Standardmäßig ist die Option ausgeschaltet und im Projekt-Workspace gespeichert. Änderungen starten bereits laufende Vorschauen nach dem Schließen des Einstellungsdialogs neu. Das Sitzungslog bleibt erhalten; Folie und Klickschritt können beim Neustart zurückgesetzt werden.

Bei aktivierter Option erscheint **Open Inspect** in der Toolbar. Sobald der Server bereit ist, öffnet die Aktion dessen `/__inspect/`-Oberfläche im normalen Browser. Sie steht in Code/Split/Preview zur Verfügung und verwendet denselben Server wie die Präsentation. Die eingebettete Vorschau bleibt auf ihrer Präsentationsseite. Bei einem Serverabbruch ist die Aktion deaktiviert, bis die Vorschau wieder gestartet wurde. Das Plugin installiert keine weiteren npm-Pakete und ändert keine Vite-Konfiguration.

Inspect zeigt die Verarbeitungsschritte der Vite-Plugins, erzeugten Modulcode und Transformationsinformationen. So lassen sich etwa die Umwandlung von Markdown zu Vue und nachfolgende Plugin-Schritte untersuchen. Die Oberfläche ersetzt weder die Preview-Fehlerliste noch eine vollständige Build-Prüfung. Es werden nur die vom laufenden Server tatsächlich verarbeiteten Module sichtbar. `Check build` behält seinen bisherigen Prüfablauf; diese Einstellung gilt für die laufende Preview. Slidevs Inspect-Plugin kann dabei zusätzlich `.vite-inspect`-Dateien im Präsentationsprojekt erzeugen, unter anderem beim Beenden des Servers. Dieses Standardverhalten stammt von Slidev; vorhandene generierte Reports an diesem Ziel können ersetzt werden. Ein abweichendes Ziel lässt sich über `slidev.inspect.outputDir` in der eigenen Vite-Konfiguration setzen.

**Versionsabhängige Voraussetzung:** Slidev 52.14.1 verwendet Inspect 11 mit eigenständiger Oberfläche. Slidev 52.20.1 verwendet Inspect 12, dessen Oberfläche zusätzlich Vite DevTools benötigt. Dafür muss im Präsentationsprojekt eine kompatible Version von `@vitejs/devtools` installiert und in der vorhandenen `vite.config.ts` die Option `devtools: true` gesetzt sein. Bereits vorhandene Einstellungen beibehalten, zum Beispiel:

```ts
import { defineConfig } from 'vite'

export default defineConfig({
  devtools: true,
})
```

Vite DevTools kann eine Freigabe im Browser verlangen. Den dabei benötigten Code beziehungsweise die Servermeldungen über **Show Slidev log** ansehen; die Authentifizierung wird vom Plugin nicht abgeschaltet. Inspect 12 leitet `/__inspect/` auf seine Client-Oberfläche weiter. Bei eigener Inspect-Base-URL muss die Adresse im Browser entsprechend angepasst werden. Die getesteten Kombinationen sind Inspect 11.4.1 mit Vite 7.3.1 und Inspect 12.0.2 mit Vite 8.3.3 / DevTools 0.7.2. Native Settings-, Browser- und Neustartintegration stehen noch als IntelliJ-Laufzeittest aus.

## Fehler in Vue-Komponenten, Layouts und Themes ab 0.1.20

Die Fehlerliste erkennt jetzt auch gemeldete lokale `.vue`-Dateien aus der laufenden Preview und aus **Check build**. Ein Klick öffnet die betroffene Datei im normalen IntelliJ-Editor. Bei einer verifizierten Position wird der Cursor dorthin gesetzt und die Stelle in geöffneten Texteditoren markiert. Der Slidev-Server läuft beim Wechsel in den Vue-Editor weiter. Ohne verifizierbare Position lässt sich die Datei öffnen, ohne einen vermeintlichen Fehleroffset vorzugeben. Das benötigt keinen zusätzlichen Vue-Sprachdienst und keine Ultimate-Lizenz; die normale Syntaxunterstützung des geöffneten Vue-Editors hängt weiterhin von den installierten IDE-Plugins ab.

Datei- und Dokumentänderungen an betroffenen Vue-Dateien entfernen deren bisherigen Preview-Diagnosen. Build-Ergebnisse werden bei Vue-Änderungen im Projekt verworfen und laufende Build-Prüfungen abgebrochen. Auch das Löschen oder Umbenennen erkannter Quellen wird berücksichtigt. Vue-Dateien werden über die normalen Speicherfunktionen der IDE gespeichert. Ungespeicherte Inhalte erhalten keine Markierung aus einem älteren Server-/Build-Dokumentstand. Vor dem Sprung wird der vollständige gespeicherte Schnappschuss nochmals mit dem aktuellen Dokument verglichen.

**Positionszuordnung und Source Maps:** Slidev fügt Vue-Dateien unter anderem Kontextcode hinzu. In den getesteten Versionen liefert dieser Schritt keine nutzbare Source Map mit der Compiler-Fehlermeldung; selbst eine Koordinate mit unverändertem `.vue`-Dateinamen kann deshalb verschoben sein. Das Plugin prüft die Originaldatei mit dem lokal installierten `vue/compiler-sfc`, ohne Komponenten auszuführen. Nur eine eindeutige Übereinstimmung mit der Servermeldung oder einem Compiler-Codeausschnitt wird als Position verwendet. Mehrdeutige Treffer, generierter Code ohne passende Evidenz und unbekannte Transformationen bleiben mit **position not verified** sichtbar. Das ist keine vollständige Source-Map-Integration und kein Vue-Typprüfer. Vorherige erfolgreiche Transform-Maps werden nicht für einen inzwischen fehlgeschlagenen Modulstand verwendet.

Die Hintergrundanalyse ist begrenzt: höchstens 32 aktuelle Vue-Schnappschüsse, vier gleichzeitige Ladevorgänge und fünf Sekunden je Compilerprozess. Dateien über 2 MB werden nicht eingelesen; der Compilerhelper verarbeitet maximal 500.000 Zeichen. Änderungen, Node-Konfigurationswechsel und das Schließen des Editors verwerfen ausstehende Ergebnisse. Fehlende Quellen, Compiler oder nicht passende Dokumentstände verhindern eine Markierung, nicht die Anzeige der Fehlermeldung im Log.

## Fehler der laufenden Preview ab 0.1.17

Die Serverausgabe wird während der Vorschau automatisch auf Vite-Fehler untersucht, beispielsweise `Internal server error`, `Pre-transform error` und Fehler beim Start. Eine gesonderte Build-Prüfung ist dafür nicht erforderlich. Erkannte Fehler erscheinen mit dem Präfix **[Preview]** in der Fehlerliste. Bei einer passenden Position im aktuellen Deck öffnet ein Klick die betroffene Markdown-Quelldatei, setzt den Cursor und zeigt eine rote Markierung im Editor. Das funktioniert auch für importierte Folien anhand ihrer globalen Nummer und ihres Quelldateinamens.

Fehlerliste und Status stehen in allen drei Ansichten zur Verfügung, auch im reinen Code-Modus. Bei gemeldeten Preview-Fehlern, Browser-Ladefehlern oder einem Serverabbruch erscheinen direkt daneben **Open log** und **Restart preview**. Die Toolbar-Aktionen bleiben ebenfalls erhalten. Das Logfenster benötigt keine aktivierte Debug-Konsole.

Bei Änderungen an beteiligten Markdown-Dokumenten werden die bisherigen Preview-Diagnosen entfernt. Während ungespeicherter Änderungen werden keine neuen Serverpositionen eingetragen. Bereits eingeplante Ausgaben älterer Dokumentstände und Ausgaben einer ersetzten Sitzung werden nicht erneut als aktuelle Diagnose übernommen. Nach dem Speichern kann Slidev über sein normales Hot Reload neue Fehler melden. Ein fehlender Fehlereintrag bedeutet nicht, dass die gesamte Präsentation erfolgreich kompiliert wurde. Manche Module werden erst beim Aufrufen einer Folie kompiliert. **Check build** bleibt für eine vollständige Build-Prüfung verfügbar und zeigt seine Ergebnisse getrennt mit **[Build]** an.

Die Auswertung verarbeitet die getesteten Vite-7-/Vite-8-Textformate, keine strukturierte Fehler-API. Sie speichert höchstens 100 aktuelle Meldungen. Ab 0.1.19 wird ein vorhandener Compiler-Codeausschnitt zusätzlich mit dem Originaltext der betroffenen Folie verglichen. Bei einer eindeutigen Übereinstimmung werden verschobene Zeilen und zusätzliche HTML-Wrapper, etwa `<p>`, berücksichtigt. Bei mehrfach vorkommendem oder verändertem Ausschnitt wird keine Stelle geraten. Ohne Codeausschnitt bleiben virtuelle Folienpositionen eine folienrelative Heuristik. Das ist weiterhin keine Zuordnung über Transform-Source-Maps. Meldungen ohne passende Position oder bei nicht verfügbarem Deck-Mapping bleiben als Text mit den gemeldeten Koordinaten und im Log sichtbar. Vue-Dateien werden zusätzlich wie oben beschrieben behandelt. Sie erhalten keine erfundene Markdown-Markierung. Browser-JavaScript-Ausnahmen werden nicht als Compilerfehler erfasst. Ein Serverabbruch erfordert weiterhin einen manuellen Neustart.

## Build prüfen und Fehler anzeigen

**Check build** speichert die geöffneten Dokumente des aufgelösten Decks und startet die lokal installierte Slidev-CLI als `slidev build <entry> --out <temporäres Verzeichnis>`. Die Prüfung benötigt keinen laufenden Vorschau-Server und keine Ultimate-Lizenz. `--inspect` ist hierfür nicht erforderlich. Sie berücksichtigt die vorhandene Slidev-Projektkonfiguration; eigene Build-Plugins können ihre üblichen Nebenwirkungen ausführen.

Die Ausgabe wird im Sitzungslog und in der Debug-Konsole gesammelt (standardmäßig ausgeblendet). Erkannte Fehler erscheinen zusätzlich in der gemeinsamen Fehlerliste mit dem Präfix **[Build]**. Ein Klick auf einen zugeordneten Fehler setzt den Cursor auf die Position im Quelltext. Eine rote Wellenlinie und eine Markierung am Editor-Rand zeigen die Stelle. **Cancel check** bricht die Prüfung ab. Nach 120 Sekunden wird ein noch laufender Build beendet. Die temporären Build-Dateien werden anschließend gelöscht; das normale `dist`-Verzeichnis wird nicht als Ausgabeziel verwendet.

Die Auswertung unterstützt die gezeigten Vite-7/Rollup- und Vite-8/Rolldown-Ausgaben. Für virtuelle Dateinamen wie `slides.md__slidev_2.md:5:7` wird die Zeile relativ zum Inhalt der zweiten Folie zugeordnet. Frontmatter und führende Leerzeilen werden übersprungen. Beginnt der Inhalt in Originalzeile 54, ergibt die gemeldete Zeile 5 die Originalzeile 58. Die Spalte wird als einsbasierte Position übernommen und auf das Zeilenende begrenzt.

Diese Zuordnung ist eine Heuristik ohne Source Maps. Eigene Markdown-Transformationen, Parser-Erweiterungen und Block Frontmatter können die Positionen verändern. Die Liste kennzeichnet zugeordnete Positionen deshalb als `slide-relative mapping`. Bei Imports über `src` werden globale Foliennummer und Quelldatei aus dem Slidev-Parser abgeglichen und die Position im importierten Dokument geöffnet. Ist das Deck-Mapping nicht verfügbar oder passt der Dateiname nicht, bleibt die generierte Position erhalten. Nicht zuordenbare Fehler bleiben in der Liste oder im Log sichtbar. Browser-Laufzeitfehler werden durch einen erfolgreichen Build nicht ausgeschlossen.

Bei einer Änderung des Dokuments werden alte Markierungen entfernt und eine laufende Prüfung abgebrochen. Eine neue Prüfung startet erst durch **Check build**, nicht nach jedem Tastendruck. Ein schneller Linter für die Bearbeitung ist weiterhin ein eigener Backlog-Punkt.

## Debug-Konsole anzeigen

Unter **Settings > Slidev** aktiviert **Show debug console** die Konsole unterhalb der Fehlerliste. Standardmäßig ist sie unsichtbar und beansprucht keinen Platz. Die Auswahl wird im Projekt-Workspace gespeichert. Nach Übernahme und Schließen des Einstellungsdialogs aktualisieren sich auch bereits geöffnete Slidev-Editoren; ein Neustart oder erneutes Öffnen der Datei ist nicht nötig.

Server- und Build-Ausgaben werden auch bei ausgeblendeter Konsole weiterhin gesammelt. **Show Slidev log** öffnet unabhängig von dieser Einstellung ein separates, nicht modales Logfenster mit den laufenden Ausgaben. Der Button ist auch im Code-Modus und nach einem Serverabbruch erreichbar. **Restart preview** steht in der Toolbar und im Logfenster zur Verfügung, beendet die alte Sitzung und startet Slidev nach dem Speichern der beteiligten Dokumente neu. Die bisherigen Ausgaben bleiben mit einer neuen Startmarkierung erhalten. Es gibt keine automatische Neustartschleife bei Syntaxfehlern. Das Log enthält die jüngsten Ausgaben der geöffneten Editorsitzung und ist auf ungefähr 100.000 Zeichen begrenzt; es wird nicht dauerhaft archiviert. Statusmeldungen, Build-Fehlerliste und Markierungen bleiben unabhängig von dieser Einstellung sichtbar.

## Folien auswählen und synchronisieren

Die Auswahl oberhalb der Vorschau zeigt globale Foliennummern, Überschriften und Quelldateinamen. Eine manuelle Auswahl setzt den Cursor auf die Folie und sendet einen Navigationsbefehl an die laufende Vorschau. Der Browser wird dafür nicht neu geladen. Auf einer anderen Folie startet Slidev bei deren Anfang; auf derselben Folie bleibt der aktuelle Klickschritt erhalten.

**Settings > Slidev > Sync editor and preview** ist standardmäßig aktiviert. Die Option steht ausschließlich in den Einstellungen und nicht in der Vorschau-Toolbar. Änderungen übernehmen nach Schließen des Einstellungsdialogs auch bereits geöffnete Editoren. Die Auswahl wird im Projekt-Workspace gespeichert und bei neu geöffneten Slidev-Editoren wiederhergestellt.

* **Editor zur Vorschau:** Ein Cursorwechsel auf eine andere Folie wird nach etwa 150 ms gebündelt an Slidev gesendet. Klicks im Quelltext, Suchen, Code-Navigation und Sprünge aus der Fehlerliste werden dadurch berücksichtigt. Cursorbewegungen innerhalb derselben Folie setzen keine Klickschritte zurück. Bei ausstehenden Dokumentänderungen wartet die Navigation auf den verzögerten Speichervorgang.
* **Vorschau zum Editor:** Ein Wechsel der angezeigten Folie setzt den Cursor an den Beginn des Inhalts nach dem Frontmatter und scrollt dorthin. Bei leeren Folien wird die Foliengrenze verwendet. Der Tastaturfokus bleibt in der Vorschau. Reine Klickschrittwechsel auf derselben Folie bewegen den Cursor nicht.
* **Vollbildpräsentation:** Das automatische Folgen ist in beiden Richtungen pausiert. Beim Zurückkehren wird der Editor auf die zuletzt angezeigte Präsentationsfolie gesetzt, wenn die Synchronisierung aktiviert ist.
* **Ausschalten:** Die Checkbox in den Einstellungen beendet die automatische Kopplung. Eine manuelle Auswahl in der Folienliste bleibt möglich. Beim erneuten Einschalten bestimmt zunächst die Cursorposition die Vorschaufolie.

Die Brücke verwendet Slidevs Entwicklungs-API `postMessage({target: 'slidev', type: 'navigate', no: ...})`, die intern `nav.go()` aufruft. Sie liest die aktuelle Foliennummer aus dem im Entwicklungsmodus bereitgestellten `window.__slidev__.nav` alle 100 ms und meldet nur Folienwechsel an IntelliJ. Es werden keine Klicks oder Tastendrücke simuliert. Ein zusätzlicher Netzwerk-Endpunkt und `--remote` sind nicht erforderlich. Der Adapter wurde mit den Nachrichtenhandlern aus Slidev 52.14.1 und 52.20.1 geprüft; diese Entwicklungs-Schnittstelle kann sich in späteren Versionen ändern.

Die Gliederung verwendet ab 0.1.16 den lokal installierten `@slidev/parser/fs`. Verschachtelte `src`-Imports, Folienbereiche, mehrfach importierte Quellen sowie ausgeblendete Folien werden für die globale Reihenfolge berücksichtigt. Beim Auswählen einer importierten Folie oder beim Folgen der Preview wechselt der Codebereich innerhalb desselben Slidev-Tabs zur Quelldatei. Die Browser-Komponente bleibt bestehen. **Entry source** zeigt wieder den Einstieg. Bei mehrfach importiertem Quelltext bleibt möglichst das aktuelle Vorkommen ausgewählt, andernfalls wird das erste verwendet. Auch ungespeicherte Änderungen beteiligter Markdown-Dokumente werden beim Mapping berücksichtigt.

Die Auflösung läuft mit dem konfigurierten Node.js in einem begrenzten Hintergrundprozess (10 Sekunden, 192 MB Heap, höchstens 256 Quellen und 10.000 Folien). Fehlende Quellen, Zyklen, nicht unterstützte Pfade und Parserfehler deaktivieren die Zuordnung und Synchronisierung; die Preview bleibt separat nutzbar. Eine erkannte `setup/preparser`-Datei deaktiviert das Mapping ebenfalls. Eigene Preparser aus Themes/Addons und Vite-Resolver werden nicht ausgeführt und können abweichende Ergebnisse erzeugen. Der Parseradapter ist mit Slidev 52.14.1 und 52.20.1 geprüft, nicht mit beliebigen zukünftigen Versionen.

Standalone-Checks für die Browser-Brücke:

```bash
node scripts/test-navigation.cjs
```

Optional kann als Argument der Pfad zu `node_modules/@slidev/client/composables/useEmbeddedCtrl.ts` angegeben werden. Dann laufen die Kommandotests mit dem dort enthaltenen Slidev-Nachrichtenhandler in einer simulierten Browserumgebung. Das ersetzt keinen IntelliJ-/JCEF-Laufzeittest.

## Präsentieren

Die Vollbildaktion innerhalb der geöffneten Slidev-Vorschau (Vollbildsymbol oder Slidevs `f`-Befehl) öffnet die Presentation View des Plugins. Diese ist das bisherige separate, rahmenlose Präsentationsfenster auf dem Monitor der Editoransicht. Die vorhandene Chromium-Komponente wird in dieses Fenster verschoben; Folie und Klickschritt werden weiterverwendet.

* Mit den normalen Slidev-Tastaturbefehlen navigieren.
* Mit **Esc** oder durch Ausschalten von Slidevs Vollbildmodus in den Editor zurückkehren.
* Während der Präsentation ist die automatische Cursor-/Vorschau-Synchronisierung pausiert. Beim Zurückkehren folgt der Editor wieder der zuletzt angezeigten Folie, sofern die Option aktiviert ist.

Die Brücke wertet das `fullscreenchange`-Ereignis des Browsers aus. Die tatsächlich verwendete JCEF-Laufzeit muss die Web-Fullscreen-API unterstützen; deren Verhalten und der Fensterwechsel sind auf dem Zielsystem noch praktisch zu validieren. Das Fenster ist auf macOS zunächst kein eigener nativer Vollbild-Space. Ein eigener Presenter-Modus mit Notizen und eine separate Monitorwahl sind weiterhin nicht enthalten.

## Syntaxunterstützung

Die Sprachunterstützung gilt für erkannte oder mit **Treat as Slidev** zugeordnete Präsentationen. Sie benötigt keinen laufenden Slidev-Server und keine Ultimate-, JavaScript- oder Vue-Lizenz. Markdown bleibt die Dateisprache.

* YAML-Hervorhebung im ersten Headmatter-Block und in den konventionellen Frontmatter-Blöcken jeder Folie. Die bisherige einheitliche blaue Färbung entfällt.
* Der IntelliJ-YAML-Parser prüft jeden erkannten Block. Syntaxfehler werden mit einer Meldung direkt an der entsprechenden Stelle in der Markdown-Datei markiert.
* Doppelte YAML-Schlüssel werden auch in verschachtelten Mappings gemeldet. Bekannte boolesche Felder, Zahlenfelder und ausgewählte feste Werte werden zusätzlich geprüft, beispielsweise `hide: maybe`, `clicks: -1` oder `colorSchema: sepia`.
* Fehlende schließende Frontmatter-Trenner und offene Codeblöcke werden als Strukturwarnung angezeigt, soweit die lokale Erkennung sie zuordnen kann.
* Die Vervollständigung bietet bekannte Slidev-Felder mit einer kurzen Beschreibung. Deckweite Felder wie `theme` erscheinen nur im Headmatter. Für ausgewählte Felder wie `layout`, `hide`, `presenter` und `colorSchema` gibt es Wertvorschläge.
* Eigene Theme-Felder, Theme-Namen und Layouts sind erlaubt. Die Vorschläge stellen keine abschließende Liste dar.

Die Vervollständigung wird über die IntelliJ-Aktion **Basic Completion** aufgerufen, üblicherweise mit **Ctrl+Space**. Zum Beispiel `lay` in einem Frontmatter-Block tippen und vervollständigen: `layout: ` wird eingefügt. Bei einer vorhandenen Zeile `layout: center` bleibt der Wert erhalten. Nach `layout: ` erneut vervollständigen, um Vorschläge für den Layoutnamen zu sehen. In einem neuen Block zunächst einen Feldanfang eingeben. Die normale Autopopup-Einstellung der IDE gilt weiterhin.

```yaml
---
theme: default
colorSchema: auto
lineNumbers: true
---
# Erste Folie

---
layout: center
hide: false
clicks: 3
---
# Zweite Folie
```

Normales Markdown, YAML-Beispiele in Codeblöcken und Notizen werden von der eigenen Slidev-Completion ausgenommen. Ab 0.1.13 werden verschachtelte Feldnamen und ausgewählte Werte unter `fonts`, `drawings`, `export` und `defaults` angeboten. `defaults` enthält Folieneinstellungen wie `layout`, keine deckweiten Felder wie `theme`. In mehrzeiligen YAML-Texten, Sequenzen und unbekannten verschachtelten Mappings werden keine unpassenden Root-Felder vorgeschlagen. Bestehende native YAML-Injektion im Markdown-Headmatter wird bei der Completion auf die Position im Ursprungsdokument zurückgeführt.

Die zusätzliche Analyse arbeitet mit temporären YAML-PSI-Fragmenten und abgebildeten Offsets. Sie ist keine eigene editierbare YAML-Sprachinjektion für alle Folien und führt nicht automatisch alle YAML-Inspections der IDE aus. Die native YAML-Analyse und die Completion müssen noch praktisch unter IntelliJ 2026.2/2026.3 getestet werden.

Noch nicht enthalten sind vollständige Vue-Typanalyse oder Prop-Erkennung eigener Komponenten, Auflösung beliebiger Addons und Vite-Konfigurationen, Block Frontmatter, vollständige YAML-Varianten im lokalen Strukturparser. Für Kompilierungsfehler aus Vue bleibt **Check build** verfügbar.

Ganze Folien können weiterhin eingeklappt werden. Die Vorschau nutzt unabhängig von der lokalen Analyse die echte Slidev-Laufzeit.

## Quick Fixes ab 0.1.16

Mit **Alt+Enter** bieten passende Slidev-Warnungen Korrekturen für bekannte Metadatenwerte und ähnliche Layoutnamen an. Bei eindeutig erkannten offenen Code- oder Frontmatter-Blöcken kann ein schließender Trenner ergänzt werden. Bei mehrdeutigen Grenzen wird keine automatische Ergänzung angeboten. Fehlende statische Markdown-Quellen aus `src` können über eine ausdrückliche Aktion angelegt werden, einschließlich notwendiger Unterverzeichnisse. Vorhandene Dateien werden nicht überschrieben.

Textkorrekturen prüfen vor der Anwendung, ob das analysierte Dokument noch unverändert ist. Die Aktionen laufen als native IntelliJ-Intentionen. Menüintegration, Undo und Dateierstellung müssen noch in der Ziel-IDE praktisch geprüft werden.

## Komponenten, Direktiven und Projektvorschläge ab 0.1.13

Mit **Basic Completion / Ctrl+Space** stehen zusätzlich bereit:

* Nach `<` oder einem Tag-Anfang wie `<v-cl`: eingebaute Slidev-Komponenten, zum Beispiel `v-click`, `v-clicks`, `SlidevVideo`, `Toc` und `Arrow`. Bei einem neuen Tag wird ein passendes Tag-Paar eingefügt und der Cursor zwischen die Tags gesetzt. Auch schließende Tags werden angeboten.
* Innerhalb eines Tags, zum Beispiel `<div v-`: Slidev-Direktiven einschließlich `v-click`, `v-after`, `v-mark`, ausgewählter Modifier sowie `v-motion` und `v-drag`. `v-clicks` wird korrekt nur als Komponente angeboten.
* Innerhalb bekannter Komponenten: ausgewählte passende Attribute, beispielsweise `depth` für `v-clicks`, `src` und `controls` für `SlidevVideo`. Wertattribute erhalten `=""` mit dem Cursor zwischen den Anführungszeichen. Bereits vorhandene Werte und Tag-Begrenzungen bleiben erhalten. Numerische Bindings wie `:max` bei `AutoFitText` werden auch über den Suchanfang `max` gefunden.
* Eigene `.vue`-Komponenten aus `components/` des Projekts und des gefundenen Themes. Die Vorschläge verwenden PascalCase, zum Beispiel `my-chart.vue` als `MyChart`. Unterverzeichnisse werden ebenfalls berücksichtigt.
* Werte für `layout` aus `layouts/*.vue` des Projekts und des gefundenen Themes, auch unter `defaults.layout`. Im Popup wird die Herkunft aus Projekt oder Theme angezeigt.

Die automatische Popup-Einstellung der IDE gilt weiterhin. Wird ein neuer Komponententag mit der Leertaste bestätigt, bleibt der Tag für die Attribut-Eingabe offen. Bestätigen mit Enter oder Tab erzeugt bei einem neuen Tag das Tag-Paar. Innerhalb von Codebeispielen, Notizen, Vue-Interpolationen, Script-/Style-Inhalten und Attributwerten werden keine zusätzlichen Markup-Vorschläge eingeblendet.

Die Dateisuche berücksichtigt das Verzeichnis der Markdown-Datei und das nächste übergeordnete Verzeichnis mit `package.json`. Für Themes werden die konventionellen lokalen Pfade sowie bereits installierte Pakete unter `node_modules` berücksichtigt, zum Beispiel `@slidev/theme-default`, `slidev-theme-name` oder ein vollständig angegebener Paketname. Ein lokaler Theme-Pfad wird relativ zum gefundenen Projektverzeichnis aufgelöst. Fehlende Themes werden nicht installiert. Die Suche wird bei jedem Aufruf erneut ausgeführt und ist auf 1000 Einträge je Verzeichnis sowie fünf Verzeichnisebenen für Komponenten begrenzt. Eigene Projektdateien haben Vorrang vor Theme-Dateien mit gleichem Namen.

Die Markup-Erkennung ist lexikalisch und ersetzt keinen vollständigen Vue-Sprachdienst. Die Vorschläge für eingebaute Attribute sind bewusst eine Auswahl. JavaScript-Ausdrücke und Props eigener Komponenten werden nicht analysiert. Die Completion erfordert keine Ultimate-Lizenz; die tatsächliche Einfügung im IntelliJ-Editor ist in der Erstellungsumgebung noch nicht praktisch getestet.

## Vue-Ausdrücke während des Tippens prüfen (0.1.14)

Unter **Settings > Slidev > Check Vue expressions while typing** ist die neue Prüfung standardmäßig aktiviert. Sie läuft über die Hintergrundanalyse der IDE, auch im Code-Modus und ohne laufende Vorschau. Der aktuelle ungespeicherte Dokumentinhalt wird geprüft; dafür werden weder `slidev build` noch ein Preview-Server gestartet.

Erkannt werden beispielsweise:

```html
{{ 1 + }}
<div v-click="1 +">Fehlerhafter Ausdruck</div>
<div :class="{ broken: }">Fehlerhafter Ausdruck</div>
```

Der Compiler prüft Interpolationen, gebundene Attribute und Direktivausdrücke. Dabei sind die unterschiedlichen Regeln für `v-for`, Slot-Parameter und Ereignisbehandlungen berücksichtigt. TypeScript-Ausdrücke sind zugelassen. Vue-Bereiche mit `v-pre` werden wie im Compiler behandelt. Die Prüfung führt die Ausdrücke nicht aus und meldet keine unbekannten Variablen oder Typfehler.

Voraussetzungen sind die in **Settings > Slidev** konfigurierte Node.js-Datei und ein lokal auflösbarer `@vue/compiler-dom` aus dem Präsentationsprojekt oder den Abhängigkeiten der lokalen Slidev-CLI. Auch pnpm-Verknüpfungen zur CLI werden berücksichtigt. Das Plugin installiert keine Pakete nach. Fehlt Node oder der Compiler, erscheint eine schwache Warnung am Dateianfang mit dem Grund. Die Funktion kann über die genannte Checkbox abgeschaltet werden.

YAML-Blöcke, Codebeispiele, Notizen sowie Script-/Style-Inhalte werden vor der Prüfung ausgeblendet. Zeilenenden und UTF-16-Offsets bleiben dabei erhalten. Vue-Diagnosen werden auf die ursprüngliche Markdown-Datei abgebildet. Manche Compilerfehler liefern lediglich einen Punkt am Anfang des Ausdrucks; die IDE markiert dann dort ein Zeichen. Ergebnisse eines veralteten Dokumentstands oder einer inzwischen geänderten Einstellung werden verworfen.

Die Prüfung startet einen kurzlebigen Node-Prozess pro Hintergrundanalyse mit relevanten Ausdrücken. Sie hat ein Zeitlimit von fünf Sekunden sowie Grenzen für Dokumentgröße (500.000 Zeichen), Ausgabe und Diagnoseanzahl. Bei Abbruch wird der Prozess beendet. Temporäre Eingabe-/Ausgabedateien werden anschließend entfernt. Das ist noch keine vollständige Slidev-Kompilierung: Theme-, Komponenten-, Import-, Transformations- und Vite-Fehler werden weiterhin über **Check build** geprüft. Eigene Preparser und komplexe Markdown-Sonderformen können von der lokalen Ausblendung abweichen.

Die unabhängigen Compiler-Tests können mit installierten Projektabhängigkeiten zusätzlich ausgeführt werden:

```bash
node scripts/test-expressions.cjs /pfad/zur/praesentation/slides.md
```

Der vollständige Plugin-Build und die Ausführung des neuen ExternalAnnotator in IntelliJ bleiben in der Erstellungsumgebung ungeprüft.

## Projektbezüge und Dateinavigation (0.1.15)

In erkannten Slidev-Präsentationen werden statische lokale Verweise geprüft, unabhängig von Node, Build und Preview:

* `src` in konventionellen Frontmatter-Blöcken, einschließlich Folienauswahl wie `./pages/intro.md#2,5-7`.
* Inline-Bilder in Markdown wie `![Bild](./images/cover.png)`, auch mit kodierten Leerzeichen, geklammerten Pfaden und optionalem Titel in Anführungszeichen.
* Statische `src`-Attribute an `<img>`, Bildverweise an SVG-`<image>` und `poster` an `<SlidevVideo>`.
* Einfache `image`- und bildartige `background`-Werte in Metadaten, einschließlich einzelner CSS-`url(...)`-Werte.
* `layout` in Folienmetadaten und unter `defaults` im Headmatter.

Relative Dateien werden ausgehend vom Verzeichnis der jeweiligen Markdown-Datei gesucht. Ein Bildpfad mit führendem `/`, etwa `/logo.svg`, wird in `public/` gesucht, nicht im Dateisystemwurzelverzeichnis. Berücksichtigt werden das Dateiverzeichnis, das erkannte Projekt sowie das gefundene Theme und der installierte Slidev-Client. Layout-Dateien werden in deren `layouts/` gesucht. Projektdateien haben Vorrang. Für bekannte eingebaute Layoutnamen erscheint keine Fehlmeldung, wenn deren Quelldatei lokal nicht aufgelöst werden kann.

Fehlende lokale Dateien erhalten eine Warnung direkt am Verweis. Für unbekannte Layoutnamen erscheint nur eine schwache Meldung mit dem Hinweis, dass Addon- und Vite-Layouts nicht geprüft wurden. Externe URLs werden nicht abgerufen. CSS-Farben, Gradienten, dynamische Vue-Bindungen, bekannte Aliasformen und Verweise in Codebeispielen oder Notizen werden ausgespart.

Mit der IntelliJ-Aktion **Go to Declaration or Usages** kann zu einer vorhandenen Zieldatei gesprungen werden, je nach Keymap über **Cmd+B / Ctrl+B** oder den entsprechenden modifizierten Mausklick. Bei einem Import mit Folienbereich wird zunächst die Markdown-Datei geöffnet. Ein Sprung zur konkreten importierten Folie und die Synchronisierung des gesamten importierten Decks sind noch nicht enthalten.

Die Navigation berücksichtigt auch native YAML-/HTML-Injektionen und arbeitet ausschließlich in erkannten Slidev-Dateien. Die native Aktion und ihre Mausdarstellung müssen noch in einer echten IDE geprüft werden. Die Auflösung bleibt konventionell: Markdown-Referenzbilder mit separater Linkdefinition, komplexe YAML-Formen, unquoted HTML-Attribute, beliebige Addons und dynamische Vite-Aliase sind noch nicht vollständig unterstützt. Es werden keine Dateien angelegt oder verändert.

## Beispiel und praktischer Abnahmetest

Das Verzeichnis `examples` enthält eine kleine Präsentation mit Folienmetadaten, Klickanimationen und einem Codeblock mit scheinbaren Folientrennern. Die Beispielabhängigkeiten verwenden `latest`; nach der Installation die erzeugte Lockdatei behalten, wenn das Beispiel reproduzierbar weiterverwendet werden soll.

```bash
cd examples
npm install
```

Danach `examples/slides.md` in der Testinstanz öffnen:

1. Prüfen, dass der Slidev-Editor angeboten wird und drei Folien in der Übersicht erscheinen.
2. Den Slidev-Tab auswählen und automatischen Start prüfen. Bei Bedarf die Debug-Konsole in den Einstellungen einblenden.
3. Auf Folie 2 einen Animationsschritt ausführen.
4. In Slidev den Vollbildmodus auswählen, Zustand im Präsentationsfenster prüfen, weiteren Animationsschritt ausführen und mit Esc zurückkehren.
5. Text ändern und die Aktualisierung in der Vorschau prüfen.
6. Code/Split/Preview umschalten und prüfen, dass Folie und Zustand erhalten bleiben. Dann den Editor schließen und kontrollieren, dass kein Prozess weiterläuft.
7. Eine gewöhnliche Markdown-Datei öffnen und prüfen, dass die normale Bearbeitung erhalten bleibt.
8. Node.js-Pfad absichtlich ungültig einstellen und prüfen, dass eine Fehlermeldung erscheint und ein neuer Start nach Korrektur möglich ist.
9. Das Ganze im kostenlosen IntelliJ-Modus wiederholen.

## Architektur

* `SlidevDetection`: konservative heuristische Dateierkennung.
* `SlidevSettings` und `SlidevConfigurable`: Node.js-Pfad und ausdrückliche Dateizuordnung.
* `SlidevEditorProvider`: registriert den zusätzlichen Dateieditor.
* `SlidevEditor`: delegiert die Quelltextbearbeitung an IntelliJs Texteditor, ergänzt Gliederung, Markierungen, Vorschau und Präsentationsfenster.
* `SlidevServer`: startet einen lokalen CLI-Prozess außerhalb des UI-Threads und verwaltet dessen Lebensdauer.
* `core/DeckParser`: unabhängiger Strukturparser mit ausführbaren Checks.

Die Implementierung verwendet Java und Gradle Kotlin DSL. Weitere Sprachfunktionen können später ohne Änderung der Präsentationsarchitektur ergänzt werden.

## Häufige Fehler

**Automatische Vorschau bleibt ohne Ausgabe:** Version 0.1.4 korrigiert den im IDE-Log nachgewiesenen Fehler beim Speichern aus dem Swing-Button-Handler. IntelliJ verlangt dafür Write-Intent-Zugriff. Das Speichern vor dem Serverstart sowie die Strukturaktualisierung und das Speichern aus dem Swing-Timer sind entsprechend geschützt. Andere Initialisierungsfehler erscheinen jetzt auch im Vorschaupanel und im IDE-Log.

**Datei erkannt, aber kein Slidev-Editor erstellt:** Wenn `idea.log` einen Provider-accepted-Eintrag, aber keinen Creating-editor-Eintrag enthält, wird der Provider möglicherweise durch eine andere Editorintegration unterdrückt. Zur Eingrenzung zusätzliche Markdown-Editorplugins wie Markdown Pro vorübergehend deaktivieren und die IDE neu starten. Die eingebaute Markdown-Unterstützung muss aktiviert bleiben. Eine solche Unterdrückung ist anhand dieser Meldungen allein nicht bewiesen.

**Treat as Slidev öffnet nur den normalen Editor:** Ab Version 0.1.4 prüft die Aktion nach dem Öffnen, ob der Slidev-Editor verfügbar ist. Falls nicht, erscheint ein Dialog mit den verfügbaren Editorklassen. In `idea.log` nach `[Slidev 0.1.15]` suchen: Die Einträge unterscheiden explizite Zuordnung, Erkennung durch den Provider und Editor-Erstellung. Bei einem Fehler während der Erstellung folgt der Stacktrace. Unter Settings > Plugins prüfen, dass Version 0.1.15 installiert ist.

**Package org.cef.browser oder org.cef.handler ist nicht vorhanden:** Im Gradle-Build müssen beide Module `intellij.platform.ui.jcef` und `intellij.libraries.jcef` eingebunden sein. Das erste enthält die IntelliJ-Wrapper, das zweite die zugrunde liegenden JCEF-Klassen. Diese Quellcodefassung enthält die zusätzliche Abhängigkeit. Die Laufzeitabhängigkeit `com.intellij.modules.jcef` in `plugin.xml` ersetzt die Compile-Abhängigkeit nicht.

**Local @slidev/cli missing:** Abhängigkeiten im Präsentationsprojekt installieren. Das Plugin lädt oder installiert sie nicht selbst.

**Cannot run program node:** Unter Settings > Slidev einen gültigen absoluten Node.js-Pfad eintragen.

**JCEF unavailable:** Die IDE mit der mitgelieferten JetBrains Runtime starten.

**Server startup timed out:** Version 0.1.5 übernimmt die lokale Adresse aus der Slidev-Ausgabe und lädt die ausgewählte Folienroute direkt in Chromium. Damit blockiert eine abweichende Netzwerk- oder Namensauflösung der Java-Laufzeit die Vorschau nicht mehr. Falls die CLI keine Adresse meldet, prüft das Plugin `/1` über `127.0.0.1`, `localhost` und `[::1]`, ohne globalen Proxy. Die Wurzelroute `/` wird nicht als Bereitschaftstest verwendet, weil sie trotz funktionierender Präsentation einen HTTP-Fehler liefern kann. Von Slidev gemeldete abweichende Ports werden übernommen. Die getesteten Adressen und ihre Ergebnisse erscheinen im Log.

**Plugin wird nach dem Build nicht geladen:** Buildausgaben und IDE-Log prüfen. Die API-Kompatibilität mit 2026.2 und 2026.3 ist noch nicht durch einen vollständigen Build und Plugin Verifier in der Erstellungsumgebung bestätigt.

## Weiterentwicklung

Siehe `BACKLOG.md`. Vor einer produktiven Nutzung stehen der vollständige Build, der Laufzeittest auf macOS und die Prüfung im kostenlosen IntelliJ-Modus an.
