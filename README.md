# SeaFrogs SF-PH-01 Camera

Wir entwickeln unsere Android-Kamera für Google Pixel 8 und SeaFrogs SF-PH-01 Pro.
Mit **1.0.0-rc2** ergänzen wir die funktionierende RC1-Version um Release-Signierung, automatische Prüfungen und deaktivierte HID-Protokollierung. Unser Ziel bleibt zuverlässiges Aufnehmen und nahezu blinde Bedienung mit fünf Gehäuseeingaben.

Mit Links wechseln wir Hauptkamera/Macro/UW, mit Hoch den Zoom, mit Rechts EV,
mit Runter Foto/Video und mit Klick lösen wir aus beziehungsweise starten/stoppen Video.
Mit **Links + Hoch** aktivieren wir die Kamerasteuerung, mit **Rechts + Runter** die klassische Maus.

Hauptkamera-Zoom 1/1,5/3/5, UW 1/1,5/3, Macro 0,5×/1× Crop.
EV 0/+1/+2/−1/−2. Zoom-/EV-Zyklen sind im Setup editierbar.

Tauchprofil speichert App-Helligkeit, Ausrichtung, gewählte Maus und Steuerungsmodus.
Kamerasteuerung zeigt große Statusangaben und möglichst viel Vorschau.
SETUP enthält JPEG/RAW+JPEG, Foto-ISO-/Digitalgrenzen, Video 4K30/4K60,
unterstützte WB-Presets, modellbasierte Unterwasserprofile, DL08-Flutlicht,
UW-Korrekturstärke, Tauchprofil, Zyklen und Diagnose.
Video bleibt **ohne Ton**. Bilder: Pictures/SeaFrogs, Clips: Movies/SeaFrogs.

Wir halten die positive Geräterückmeldung zum Gehäusebetrieb einschließlich beider
Umschaltgesten nach 0.8.10 fest. Macro-Nahfokus hinter dem Gehäusefenster und
tatsächliche Video-FPS bleiben gezielte Praxisprüfungen. Für Unterwasser-WB verwenden wir veröffentlichte
Absorptionsdaten mit dokumentierten Modellannahmen und routenspezifischen
Sensormatrizen. Wir haben noch keine gemessene Meerwasser-/Pixel-Kalibrierung. Der Status
vergleicht angeforderte und gemeldete WB-Parameter.
STANDARD bleibt Default. RAW, aktive Belichtungsgrenzen und manuelles WB
schalten Extensions aus. Mehrbild bleibt eingefroren, ohne sichtbare Testschaltflächen.

Vollständige 4:3-Fotovorschau mit maximaler Fensterfläche; Status und Bedienung
nutzen Randflächen. Setup überlagert die Vorschau, statt sie zu verkleinern.

Details: [Tauchoberfläche](docs/UI-4-3.md),
[WB-Profile und Datenquellen](docs/WB-0.8.2.md),
[Tauchprofil 0.8.1](docs/DIVE-0.8.1.md),
[Videoarchitektur](docs/PHOTO-VIDEO-0.8.md),
[Buildstatus](docs/BUILD-STATUS.md),
[unser aktueller Projektstand](docs/PROJECT-STATUS.md),
[Installation und Verteilung](docs/INSTALLATION.md),
[Optimierungen nach RC1](docs/RC1-OPTIMIERUNGEN.md).

Wir verwenden Kotlin, CameraX 1.6.2 und Camera2-Interop sowie Android Studio und JDK 17.
Wir veröffentlichen unsere App unter Apache 2.0. MIT-/Halide-Vermerke stehen in NOTICE und
in der APK. Die CIE-abgeleitete WB-Datendatei steht separat unter CC BY-SA 4.0.
Der separate Host-Bibliotheksvergleich unter tools/library-benchmark
enthält GPL-3.0-only-Komponenten und gehört nicht zur App.

In den übrigen versionsbezogenen Dokumenten halten wir historische Versuche fest.
Für unsere aktuelle Bedienung gelten DIVE-0.8.1.md und RC1.md. Die Auslieferung von RC2 beschreiben wir in [RELEASE.md](docs/RELEASE.md).

### Schnellere RAW-/ISO-Aufnahme ab 0.8.4

Im normalen Fotomodus bleiben Vorschau und RAW-/JPEG-Ausgänge offen. Auslösen verwendet die laufenden Messwerte direkt, ohne Kamera-Neustart oder feste AF/AE-Wartezeit. Schärfe ist bei bewegten Motiven weiterhin vom laufenden Autofokus abhängig. JPEG/DNG werden anschließend gespeichert. NIGHT kann durch die Herstellerverarbeitung länger dauern. Details: [Auslöseweg](docs/FAST-SHUTTER-0.8.4.md).

### Korrektur 0.8.5

Die Camera2-TextureView rotiert nicht mehr zusätzlich um die bereits vom System korrigierte Sensororientierung. Die vollständige 4:3-Fotovorschau bleibt unverzerrt. Eine ISO-/Zeit-/Digitalgrenzen-Warnung nach erfolgreich gespeicherten Dateien lässt die Fotositzung offen. Die UI zeigt die gemeldeten Werte in Gelb; sie bestätigt überschrittene oder fehlende Grenzen nicht als eingehalten. Details: [Korrektur 0.8.5](docs/FIXES-0.8.5.md).

### Korrektur 0.8.6

WB-Presetwechsel sichern die Statusanzeige gegen vorbereitete Matrizen des vorherigen Profils ab. EV zeigt den gewünschten Wert sofort; weitere EV-Schritte warten nicht auf die vorige AE-Bestätigung. Nur die jüngste Rückmeldung aktualisiert die UI. [Details](docs/FIXES-0.8.6.md).

### Setup 0.8.7

Wir ordnen Fotoformat und ISO-/Digitalgrenzen als einzelne Einträge im gemeinsamen Setup-Block bei Tauchprofil, Weißabgleich, Zyklen und Video ein. Die separate Zweier-Schaltflächenzeile entfällt. Wir halten die positive Geräterückmeldung zum ohne SeaFrogs-Maus prüfbaren Betrieb nach 0.8.6 fest. Damals planten wir Gehäuse-HID und Wasserpraxis als nächste Abnahmen.

### Bereinigte Oberfläche 0.8.8

Test-, Export- und Qualitätsbuttons sind aus der Oberfläche entfernt. Die Diagnose-Implementierungen bleiben im Quelltext. Setup enthält weiterhin Mausauswahl, HID-Diagnose und Neustart für die anstehende Gehäuseabnahme. Tauchbedienung: Kamera, Zoom, EV, Foto/Video, Auslöser, Setup.

### RC1

Wir halten die positive Geräterückmeldung zum Gehäusebetrieb nach 0.8.10 fest. Kamerawechsel, Zoom, EV, Foto/Video und Auslöser bleiben ab RC1 in beiden Mausmodi und bei geöffnetem Setup sichtbar. Setup überlagert die Vorschau oberhalb der Toolbar; die vollständige 4:3-Fotofläche behält ihre Größe. Unterwasser-Farbwirkung und Dauerbetrieb bleiben Praxisprüfungen.
