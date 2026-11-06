# Backlog

## 0.1.19: Prozess- und Integrationsvalidierung

* [x] Serverprozess aus IDE-Adapter herauslösen und dieselbe Implementierung unabhängig prüfen.
* [x] Crash-Log vor Fehlercallback vollständig lesen; wiederholtes Beenden behandeln.
* [x] Reale Unterprozesse, stdout/stderr, Startfehler, Neustart und Kindprozessende prüfen.
* [x] Vollständiges Slidev-Projekt mit importierter Folie, Syntaxfehler und Korrektur im selben Server testen.
* [x] Eindeutige Compiler-Ausschnitte für verschobene Zeilen und HTML-Wrapper verwenden.
* [x] GitHub-Workflow für SDK-Builds beider Zielplattformen und Plugin Verifier einrichten.
* [ ] Erfolgreiche Workflow-Ergebnisse bestätigen; sichtbaren IntelliJ-/JCEF-Akzeptanztest durchführen.
* [ ] Transform-Source-Maps für Fälle ohne eindeutigen Compiler-Ausschnitt.

## 0.1.18: Slidev Inspect

* [x] Standardmäßig ausgeschaltete Inspect-Einstellung für den Preview-Startparameter.
* [x] Laufende Vorschauen bei Änderung der Einstellung neu starten.
* [x] Inspect auf demselben Server im externen Browser öffnen, in allen Ansichtsmodi.
* [x] Inspect-11-/Inspect-12-Voraussetzungen dokumentieren und echte Vite-Plugin-Fixtures prüfen.
* [ ] Native Settings-/Neustart-/Browserintegration in IntelliJ prüfen.
* [ ] Optionale separate Inspect-Buildberichte mit aufbewahrtem Ausgabeziel.

## 0.1.17: Fehlerdiagnose der laufenden Preview

* [x] Vite-Servermeldungen während der Vorschau inkrementell erkennen und begrenzen.
* [x] Erkannte Deck-Positionen einschließlich importierter Quellen anklickbar machen und markieren.
* [x] Preview- und Build-Ergebnisse getrennt halten; alte Preview-Diagnosen bei Quelländerungen entfernen.
* [x] Gemeinsame Fehlerliste und direkte Log-/Neustartaktionen in allen Ansichtsmodi.
* [x] 24 reine Java-Prüfungen und echte Vite-/Vue-Serverfixtures für Vite 7 und 8.
* [ ] Native IntelliJ-/JCEF-Integration und Ausgaben vollständiger Slidev-Projekte praktisch prüfen.
* [ ] Transform-Source-Maps, zusätzliche Logformate und komponentenübergreifende Diagnosen.

## 0.1.16: Quick Fixes, importierte Decks und Diagnosezugriff

* [x] Metadaten-/Layoutkorrekturen, eindeutige Blockabschlüsse und fehlende Markdown-Quellen als Intentionen.
* [x] Lokalen Slidev-Parser für globale Gliederung, Imports, Bereiche und Quellzuordnung verwenden.
* [x] Quellwechsel im bestehenden Tab ohne Browserwechsel; wiederholte Imports berücksichtigen.
* [x] Immer erreichbares Logfenster und manueller Preview-Neustart mit erhaltenem Sitzungslog.
* [x] Reine Java-Checks und echte Parserprozesse gegen Slidev 52.14.1 und 52.20.1.
* [ ] Vollständiger SDK-Build, native Intentionen/Undo, Quellwechsel und Log-/Neustartlebenszyklus praktisch prüfen.
* [ ] Eigene Theme-/Addon-Preparser und dynamische Vite-Resolver berücksichtigen.
* [ ] Exakte Transform-Source-Maps für Build-Diagnosen.

## 0.1.15: Projektbezüge

* [x] Statische src-, Bild- und Layout-Bezüge erkennen und lokal prüfen.
* [x] Relative Dateien, öffentliche Assets und Projekt-/Theme-/Client-Layouts unterscheiden.
* [x] Vorhandene Dateien über Go to Declaration öffnen, auch aus injizierten Bereichen.
* [x] Externe/dynamische Referenzen und Codebeispiele aussparen.
* [x] 113 reine Dateisystem-/Offset-/Navigationszielprüfungen.
* [ ] Native Warnungen und Dateinavigation in IntelliJ 2026.2/2026.3 prüfen.
* [ ] Markdown-Referenzbilder, erweiterte YAML-Formen und dynamische Resolver unterstützen.

## 0.1.14: Live-Ausdrucksprüfung

* [x] Hintergrundprüfung von Vue-Ausdrücken mit lokal installiertem Vue-Compiler, ohne Build oder Preview.
* [x] Ungespeicherte Inhalte prüfen und Original-Offsets trotz Code-/Metadaten-Ausblendung erhalten.
* [x] Abbruch, Zeit-/Größenlimits, veraltete Ergebnisse und fehlende Abhängigkeiten behandeln.
* [x] Einstellbare Checkbox, standardmäßig aktiv; Node-Konfiguration wiederverwenden.
* [x] Tests mit echtem Vue-Compiler und Node-Prozess einschließlich pnpm-Auflösung.
* [ ] ExternalAnnotator und Checkbox in echter IDE praktisch prüfen.
* [x] Erste Prüfung lokaler src-Dateien, Bilder und Layouts mit Navigation.
* [x] Erste Quick Fixes für typische Metadaten-/Strukturfehler ergänzen.
* [x] Importierte Folien für Gliederung, Diagnosen und Synchronisierung auflösen.

## 0.1.13: Erweiterte Completion

* [x] Slidev-Komponenten, Direktiven, ausgewählte Modifier und Komponentenattribute.
* [x] Kontextfilter für YAML, Markup, Code, Kommentare und Attributwerte.
* [x] Verschachtelte Completion für fonts, drawings, export und defaults.
* [x] Layouts und Vue-Komponenten aus konventionellen Projekt- und installierten Theme-Verzeichnissen.
* [x] Tag-Paare und Wertattribute einfügen, vorhandene Begrenzungen und Werte erhalten.
* [x] 68 zusätzliche Kontext-, Einfüge- und Dateisystemprüfungen.
* [ ] Native Completion-Popups und Einfügehandler in IntelliJ 2026.2/2026.3 prüfen.

## 0.1.12: Erste Sprachunterstützung

* [x] YAML-Lexer und Parser je erkanntem Metadatenblock verwenden; Diagnosen auf Markdown abbilden.
* [x] Slidev-Root-Felder und ausgewählte Werte vervollständigen, Headmatter-Scope beachten.
* [x] Doppelte YAML-Schlüssel, ausgewählte Feldwerte und offene Blöcke melden.
* [x] Core-Regressionstests für Kontexte, Recovery, Offsets und offene Schemafelder.
* [ ] Vollständigen Build und die YAML-/Completion-Integration in IntelliJ 2026.2/2026.3 prüfen.

## 0.1.11: IntelliJ 2026.3

* [x] Deklarierten Kompatibilitätsbereich auf 262 bis 263.* erweitern.
* [x] JCEF-Plugin als explizite Build-Abhängigkeit ergänzen.
* [x] Verifier für Build-Basis und 2026.3-RC konfigurieren; Zielversionen überschreibbar.
* [ ] Vollständigen Build und Plugin Verifier gegen 2026.3 ausführen.
* [ ] Erkennung, Vorschau, Live-Updates, Navigation und Vollbild auf 2026.3 testen.

## 0.1: Prototyp praktisch validieren

* [ ] Vollständiger Build mit JDK 25 und IntelliJ 2026.2.3.
* [ ] IntelliJ Plugin Verifier ausführen und API-Abhängigkeiten prüfen.
* [ ] Ohne Ultimate-Lizenz testen.
* [ ] Vorschau und rahmenloses Präsentationsfenster auf macOS testen.
* [ ] Erhaltung von Folie und Klickschritt beim Fensterwechsel bestätigen.
* [ ] Escape-Fokus und Verhalten mehrerer Monitore prüfen.
* [ ] Serverabbruch, Fehlerzustände und Prozesse nach Editor-/Projektende prüfen.

## 0.1.9: Automatische Vorschau und Ansichten

* [x] Automatischer Start beim Aktivieren des Slidev-Tabs, Start/Stop/Present entfernt.
* [x] Vollbildereignis der Slidev-Vorschau mit bestehendem Präsentationsfenster verbinden.
* [x] Code/Split/Preview mit Symbolbuttons und wiederverwendeten Komponenten.
* [ ] Autostart, alle Ansichtswechsel und Web-Fullscreen in echtem IntelliJ/JCEF testen.

## 0.1.4: Build-Diagnosen

* [x] Explizite Build-Prüfung ohne Vorschau-Server mit Abbruch und temporärem Ausgabeziel.
* [x] Beide gemeldeten Vite-Ausgabeformate und folienrelative Zeilenzuordnung.
* [x] Fehlerliste, Navigation, Markierungen und Entfernen veralteter Diagnosen.
* [ ] Prüfung der neuen Oberfläche in IntelliJ 2026.2.3 mit echtem Slidev.
* [ ] Source Maps und importierte Folien für präzise Zuordnung verwenden.
* [ ] Schneller Linter für Frontmatter und strukturelle Fehler beim Tippen.

## 0.2: Sprache und Erkennung

* [ ] Slidev-Struktur mit dem autoritativen Parser abgleichen.
* [x] YAML-Fragmentanalyse und Fehlerabbildung je konventionellem Frontmatter-Block.
* [ ] Vollständige editierbare YAML-Sprachinjektion je Folie.
* [x] Erste Root-Feld-/Wert-Completion mit Beschreibungen für Headmatter/Frontmatter.
* [x] Erste verschachtelte Completion für fonts, drawings, export und defaults.
* [ ] Vollständige Dokumentation und Quick Fixes ergänzen.
* [ ] Vue-Direktiven und Slidev-Komponenten ohne Ultimate als Basissyntax erkennen.
* [ ] Erweiterte Vue-Codeanalyse optional integrieren.
* [ ] Block Frontmatter, YAML-Varianten und benutzerdefinierte Parser unterstützen.
* [x] Importierte Dateien zu Hauptdecks zuordnen und Navigation über `src`.
* [ ] Dateierkennung zwischenspeichern und manuelle Zuordnung zurücknehmen können.
* [ ] Projekt-Entry-Konfiguration statt weiterer unsicherer Dateinamensheuristiken.
* [x] Konventionelle Projekt- und Theme-Layouts lokal für Completion erkennen.
* [ ] Beliebige Addons und dynamische Konfiguration in die Dateierkennung einbeziehen.

## 0.3: Vorschau und Präsentieren

* [ ] Standard-Editorintegration und Faltungen auf Konflikte mit Markdown prüfen.
* [x] Dateilokale Synchronisierung zwischen Cursor und Vorschau in beiden Richtungen, Schalter und Vollbildpause.
* [ ] Synchronisierung in IntelliJ/JCEF mit echten Themes praktisch prüfen.
* [x] Lokale CLI-Adresse und geänderte Ports für die Vorschau übernehmen.
* [ ] Portkollisionen und alternative Serverkonfigurationen mit echtem Slidev testen.
* [ ] Einstellbare Paketmanager-/CLI-Pfade und Startargumente.
* [ ] Verbinden mit bereits laufendem Server.
* [ ] Explizite Monitorwahl, nativer macOS-Vollbildmodus und Slidev-Fullscreen-Callbacks.
* [x] Hauptdeck und importierte Folien in einer gemeinsamen Gliederung.
* [ ] Anzeigen von Serverfehlern als IDE-Benachrichtigung.
* [ ] Server bei geöffneten Ansichten desselben Decks gemeinsam verwenden.
* [ ] Autosave-Verhalten konfigurierbar machen.

## Später

* [ ] Presenter-Modus, Notizen und nächste Folie, wenn angefordert.
* [ ] Exportaktionen für PDF, PNG und HTML.
* [ ] Remote Development/Gateway als eigenes Entwicklungsziel.
* [ ] Ältere IntelliJ Community-Versionen nach Bedarf gesondert prüfen.
