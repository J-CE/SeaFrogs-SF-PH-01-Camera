# SeaFrogs SF-PH-01 Camera

Android-Kamera für Google Pixel 8 und SeaFrogs SF-PH-01 Pro.
Aktueller Stand: **0.8.2-wb**. Ziel bleibt zuverlässiges Aufnehmen und nahezu blinde Bedienung mit fünf Gehäuseeingaben.

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

Details: [WB-Profile und Datenquellen](docs/WB-0.8.2.md),
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
