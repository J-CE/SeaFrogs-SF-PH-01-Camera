# SeaFrogs SF-PH-01 Camera

Android-Kamera für Google Pixel 8 und SeaFrogs SF-PH-01 Pro.
Aktueller Stand: **0.8.6-wb-ev-fix**. Ziel bleibt zuverlässiges Aufnehmen und nahezu blinde Bedienung mit fünf Gehäuseeingaben.

Links wechselt Hauptkamera/Macro/UW, Hoch Zoom, Rechts EV, Runter Foto/Video,
Klick Foto beziehungsweise Video Start/Stop. **Links + Hoch** aktiviert
Kamerasteuerung; **Rechts + Runter** aktiviert klassische Maus.

Hauptkamera-Zoom 1/1,5/3/5, UW 1/1,5/3, Macro 0,5×/1× Crop.
EV 0/+1/+2/−1/−2. Zoom-/EV-Zyklen sind im Setup editierbar.

Tauchprofil speichert App-Helligkeit, Ausrichtung, gewählte Maus und Steuerungsmodus.
Kamerasteuerung zeigt große Statusangaben und möglichst viel Vorschau.
SETUP enthält JPEG/RAW+JPEG, Foto-ISO-/Digitalgrenzen, Video 4K30/4K60,
unterstützte WB-Presets, modellbasierte Unterwasserprofile, DL08-Flutlicht,
UW-Korrekturstärke, Tauchprofil, Zyklen und Diagnose.
Video bleibt **ohne Ton**. Bilder: Pictures/SeaFrogs, Clips: Movies/SeaFrogs.

Macro-Nahfokus, tatsächliche Video-FPS und zuverlässige Umschaltgesten sind
noch nicht am Gerät abgenommen. Unterwasser-WB verwendet veröffentlichte
Absorptionsdaten mit dokumentierten Modellannahmen und routenspezifischen
Sensormatrizen. Keine gemessene Meerwasser-/Pixel-Kalibrierung. Der Status
vergleicht angeforderte und gemeldete WB-Parameter.
STANDARD bleibt Default. RAW, aktive Belichtungsgrenzen und manuelles WB
schalten Extensions aus. Mehrbild bleibt eingefroren, ohne sichtbare Testschaltflächen.

Vollständige 4:3-Fotovorschau mit maximaler Fensterfläche; Status und Bedienung
nutzen Randflächen. Setup überlagert die Vorschau, statt sie zu verkleinern.

Details: [Tauchoberfläche](docs/UI-4-3.md),
[WB-Profile und Datenquellen](docs/WB-0.8.2.md),
[Tauchprofil 0.8.1](docs/DIVE-0.8.1.md),
[Videoarchitektur](docs/PHOTO-VIDEO-0.8.md),
[Buildstatus](docs/BUILD-STATUS.md).

Kotlin, CameraX 1.6.2, Camera2-Interop; Android Studio, JDK 17.
Die App steht unter Apache 2.0. MIT-/Halide-Vermerke stehen in NOTICE und
in der APK. Die CIE-abgeleitete WB-Datendatei steht separat unter CC BY-SA 4.0.
Der separate Host-Bibliotheksvergleich unter tools/library-benchmark
enthält GPL-3.0-only-Komponenten und gehört nicht zur App.

Die übrigen versionsbezogenen Dokumente beschreiben historische Versuche und
ersetzen nicht die aktuelle Bedienung in DIVE-0.8.1.md.

### Schnellere RAW-/ISO-Aufnahme ab 0.8.4

Im normalen Fotomodus bleiben Vorschau und RAW-/JPEG-Ausgänge offen. Auslösen verwendet die laufenden Messwerte direkt, ohne Kamera-Neustart oder feste AF/AE-Wartezeit. Schärfe ist bei bewegten Motiven weiterhin vom laufenden Autofokus abhängig. JPEG/DNG werden anschließend gespeichert. NIGHT kann durch die Herstellerverarbeitung länger dauern. Details: [Auslöseweg](docs/FAST-SHUTTER-0.8.4.md).

### Korrektur 0.8.5

Die Camera2-TextureView rotiert nicht mehr zusätzlich um die bereits vom System korrigierte Sensororientierung. Die vollständige 4:3-Fotovorschau bleibt unverzerrt. Eine ISO-/Zeit-/Digitalgrenzen-Warnung nach erfolgreich gespeicherten Dateien lässt die Fotositzung offen. Die UI zeigt die gemeldeten Werte in Gelb; sie bestätigt überschrittene oder fehlende Grenzen nicht als eingehalten. Details: [Korrektur 0.8.5](docs/FIXES-0.8.5.md).

### Korrektur 0.8.6

WB-Presetwechsel sichern die Statusanzeige gegen vorbereitete Matrizen des vorherigen Profils ab. EV zeigt den gewünschten Wert sofort; weitere EV-Schritte warten nicht auf die vorige AE-Bestätigung. Nur die jüngste Rückmeldung aktualisiert die UI. [Details](docs/FIXES-0.8.6.md).
