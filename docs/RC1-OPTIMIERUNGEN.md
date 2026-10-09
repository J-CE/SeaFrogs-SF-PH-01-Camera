# Welche Optimierungen wir nach RC1 priorisieren

Wir bewerten hier den funktionierenden RC1-Quellstand vom 9. Oktober 2026. Die folgenden Maßnahmen sind Vorschläge; mit diesem Dokumentationsstand haben wir sie noch nicht im Programmcode umgesetzt. Wir erhalten die bestätigte Gehäusebelegung, die fünf sichtbaren Aufnahmebuttons und unsere eingefrorene Mehrbildentwicklung.

## In welcher Reihenfolge wir vorgehen

| Priorität | Was wir im Quellstand sehen | Was wir verbessern können | Woran wir den Nutzen prüfen |
|---|---|---|---|
| 1: Auslieferung | Wir haben einen Debug-Build und eine Test-Paketidentität, aber keine eigene Release-Signierkonfiguration oder GitHub-Actions-Pipeline. | Wir richten einen gesicherten Release-Build, eindeutige Paketidentität, automatisierte Prüfungen und nachvollziehbare Versionen ein. Für den kleinen Testkreis empfehlen wir Google Play Internal Testing. | Wir prüfen Signatur, Update-/Installationsweg, nicht debuggable Release, Native-Link/Ausrichtung und Kamera-/HID-Betrieb der tatsächlich ausgelieferten Variante. |
| 2: Eingabepfad | In `CameraActivity.handleMouse()` erzeugen wir für jedes eingehende Mausereignis ein vollständiges JSON, bevor wir auf die gewählte Maus filtern. `EventEncoder` liest je Sample und Pointer alle 64 Achsen; der Hintergrundschreiber hat eine Queue mit 8192 Einträgen. | Wir trennen normale Ereignisnotizen von der vollständigen Rohdiagnose. Im Normalbetrieb protokollieren wir gezielt Zustandswechsel und Befehle und vermeiden unnötige JSON-Erzeugung. Die Rohdiagnose bleibt auf Wunsch vollständig. | Wir vergleichen Allokationen, Queueverluste und Eingabereaktionszeit bei gehaltenen Richtungen, Wiederverbindung und beiden Umschaltgesten. Wir übernehmen Android-Ereignisdaten vor ihrer Wiederverwendung; wir reichen kein recyceltes MotionEvent an einen Hintergrundthread weiter. |
| 3: Oberfläche und Energie | Unser Gesundheitstakt ruft alle zwei Sekunden `renderState()` auf; zusätzliche Kamera-/Video-Rückmeldungen rendern ebenfalls die Oberfläche. | Wir aktualisieren Texte und Layout nur bei relevanten Änderungen und entkoppeln Akku-/Speicher-/Wärmeabfragen von schnellen Kameraanzeigen. Wir behalten sofort sichtbare Aufnahme- und Fehlermeldungen bei. | Wir messen Main-Thread-Zeit, Frame-Aussetzer und Energiebedarf bei identischer Helligkeit, Szene und Aufnahmekonfiguration. |
| 4: APK-Größe | Wir haben keine explizit aktivierte R8-/Ressourcenverkleinerung für Release. Der eingefrorene native HDR-Kern ist weiterhin im App-Build verlinkt. | Wir aktivieren R8 und Ressourcenverkleinerung schrittweise. Unbenötigte experimentelle Produktionsbestandteile prüfen wir separat auf Entkopplung; das Experiment bewahren wir im Quellstand. R8 allein entfernt nicht automatisch eine verlinkte native Engine. | Wir vergleichen APK-/Installationsgröße und Startzeit. Wir prüfen insbesondere JNI-Aufrufe und behalten zur Entschlüsselung von Absturzberichten passende Mapping-/Symboldateien. |
| 5: Wartbarkeit | `CameraActivity` vereint Oberfläche, Setup, HID, Berechtigungen und Diagnose; der Controller steuert mehrere Kamerapfade. | Wir trennen Zuständigkeiten schrittweise und machen Zustandsübergänge leichter nachvollziehbar. Wir beginnen mit kleinen Abschnitten, deren Verhalten bereits geprüft ist. | Wir prüfen bestehende Regressionen und gezielt die jeweils betroffenen Lebenszyklus-/Kameraübergänge. Wir vermeiden einen großen Umbau unmittelbar vor der Praxisabnahme. |
| 6: Bild und Dauerbetrieb | Wir haben modellbasierte UW-WB-Profile und eine funktionsfähige Kamerabedienung, aber keine vermessene Unterwasser-Farbkalibrierung oder dokumentierte Langzeitmessung. | Wir stimmen WB-Stärke anhand realer Vergleichsbilder ab und prüfen Auslöse-/Speicherzeiten, 4K30/60, Wärme und Akku. Wir ändern Bildparameter nur anhand nachvollziehbarer Ergebnisse. | Wir vergleichen gleiche Szenen mit und ohne DL08, möglichst mit Farb-/Graureferenz. Bei Video prüfen wir die gespeicherten Dateien auf Auflösung, tatsächliche FPS, Dauer und Abspielbarkeit. |

## Wie wir Leistungsverbesserungen belegen

Wir messen vor einer Änderung eine Referenz und danach dieselbe Aufgabe mit derselben Build-Variante unter möglichst gleichen Bedingungen. Debug- und Release-Messungen vermischen wir nicht. Wir beginnen mit wenigen konkreten Größen:

- Wir messen App-Start und Kamera-Bereitschaft getrennt.
- Wir vergleichen HID-Eingabe bis zum sichtbaren Zustandswechsel und prüfen, ob ein Druck genau einen Befehl erzeugt.
- Wir verwenden die vorhandenen Auslöse-, Capture-Start- und Speicherzeitpunkte für Fotos. Unterschiedliche Zeitbasen subtrahieren wir nicht direkt.
- Wir beobachten Speicherbedarf, thermischen Status und Akkuverbrauch bei einem festgelegten Vorschau-/Videoablauf im Gehäuse.

Wir geben vor solchen Messungen keine Prozent- oder Millisekundenverbesserung an. In unserem normalen RAW-/ISO-Weg ist der frühere Kamera-Neustart pro Foto bereits entfernt; weitere Latenzverbesserungen müssen wir an verbleibenden Engpässen festmachen.

## Was wir für RC1 konkret empfehlen

Wir beginnen mit Release-Auslieferung und einer Referenzmessung. Anschließend reduzieren wir die normale HID-Protokollierung und unnötige UI-Aktualisierungen in kleinen, einzeln prüfbaren Änderungen. APK-Verkleinerung und größere Strukturarbeit folgen, wenn die ausgelieferte Release-Variante stabil bleibt. Unterwasserfarben und Dauerbetrieb prüfen wir gebündelt als Praxistest.

Unsere Installationsstrategie und den Übergang von der bestehenden Test-App beschreiben wir in [INSTALLATION.md](INSTALLATION.md). Unsere Aussagegrenzen zum WB-Modell dokumentieren wir in [WB-0.8.2.md](WB-0.8.2.md). Für RC1 behalten wir die funktionierende Bedienung bei.

## Welche Android-Dokumentation wir dafür verwenden

- [R8 und Ressourcenoptimierung](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization)
- [Release-Signierung](https://developer.android.com/studio/publish/app-signing)
- [Verteilung über einen internen Play-Test](https://support.google.com/googleplay/android-developer/answer/9845334)
