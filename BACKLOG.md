# Backlog

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
* [ ] Verschachtelte Completion, vollständige Dokumentation und Quick Fixes ergänzen.
* [ ] Vue-Direktiven und Slidev-Komponenten ohne Ultimate als Basissyntax erkennen.
* [ ] Erweiterte Vue-Codeanalyse optional integrieren.
* [ ] Block Frontmatter, YAML-Varianten und benutzerdefinierte Parser unterstützen.
* [ ] Importierte Dateien zu Hauptdecks zuordnen und Navigation über `src`.
* [ ] Dateierkennung zwischenspeichern und manuelle Zuordnung zurücknehmen können.
* [ ] Projekt-Entry-Konfiguration statt weiterer unsicherer Dateinamensheuristiken.
* [ ] Erkennung und Completion eigener Theme-Layouts.

## 0.3: Vorschau und Präsentieren

* [ ] Standard-Editorintegration und Faltungen auf Konflikte mit Markdown prüfen.
* [x] Dateilokale Synchronisierung zwischen Cursor und Vorschau in beiden Richtungen, Schalter und Vollbildpause.
* [ ] Synchronisierung in IntelliJ/JCEF mit echten Themes praktisch prüfen.
* [x] Lokale CLI-Adresse und geänderte Ports für die Vorschau übernehmen.
* [ ] Portkollisionen und alternative Serverkonfigurationen mit echtem Slidev testen.
* [ ] Einstellbare Paketmanager-/CLI-Pfade und Startargumente.
* [ ] Verbinden mit bereits laufendem Server.
* [ ] Explizite Monitorwahl, nativer macOS-Vollbildmodus und Slidev-Fullscreen-Callbacks.
* [ ] Hauptdeck und importierte Folien in einer gemeinsamen Gliederung.
* [ ] Anzeigen von Serverfehlern als IDE-Benachrichtigung.
* [ ] Server bei geöffneten Ansichten desselben Decks gemeinsam verwenden.
* [ ] Autosave-Verhalten konfigurierbar machen.

## Später

* [ ] Presenter-Modus, Notizen und nächste Folie, wenn angefordert.
* [ ] Exportaktionen für PDF, PNG und HTML.
* [ ] Remote Development/Gateway als eigenes Entwicklungsziel.
* [ ] Ältere IntelliJ Community-Versionen nach Bedarf gesondert prüfen.
