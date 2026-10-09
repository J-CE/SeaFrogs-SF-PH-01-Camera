# Bibliotheksprüfung und Datentest 0.6.7

Wir vergleichen in diesem historischen Meilenstein Bibliothekskandidaten und sammeln die dafür erforderlichen Originaldaten.

Wir bewahren diesen Ablauf als historischen Versionsstand. In RC1 sind die damaligen Test-/Exportbuttons teilweise entfernt; für die heutige Bedienung verwenden wir [DIVE-0.8.1.md](DIVE-0.8.1.md) und [RC1.md](RC1.md).

## Was bisher gemessen ist

Der hochgeladene 0.6.6-Qualitätsvergleich enthält sieben gespeicherte JPEGs,
keine ZIP-/Exportfehler. Die drei Varianten je Kamera hatten gleiche geprüfte
Belichtung, WB und Fokus. DEFAULT meldete bereits NR=HIGH_QUALITY und
EDGE=HIGH_QUALITY: erneutes Setzen dieser Werte ist keine neue Bildpipeline.
Hauptkamera: 4080 × 3072; UW-JPEG: 4000 × 3000; NIGHT: 2560 × 1920.
NIGHT ist eine unabhängige Referenz mit anderer Verarbeitung und Belichtung.
Macro-Nahfokus bleibt unbestätigt.

## Geprüfte Kandidaten

| Kandidat | Lizenz | Stand der technischen Prüfung |
| --- | --- | --- |
| timothybrooks/hdr-plus | MIT | C++/Halide, RAW-Ausrichtung, Mehrbildfusion und Ausgabe. Generator für align_and_merge mit Halide 24 lokal kompiliert und Host-Static-Library erzeugt. Noch kein Pixel-RAW-Bild damit verarbeitet. |
| f0enix/motioncam | GPL-3.0 | Android-App mit RAW-Puffer und nativer Mehrbildpipeline. Kein einfacher CameraX-Austausch. Native Denoise-Kompilierung scheitert mit Halide 24 am entfernten auto_schedule-API; ursprüngliche Buildskripte verwenden Halide 12. Vollständiger Build und Output noch offen. |

Geprüfte Repository-HEADs: HDR+ ef4dd2ca53a51e105ed923557c726b253f05c13b;
MotionCam cc7f7c9cad5234bc939699b1ab1ffbc4bdfd6690.

MotionCam RawContainer.cpp lädt calibrationMatrix2, forwardMatrix1 und
forwardMatrix2 in calibrationMatrix1. Vor einem Farbvergleich müssen diese
Zuweisungen korrigiert werden. DNG-Eingaben benötigen eine überprüfte Umsetzung
in MotionCams RAW-Container inklusive Farb-/WB-/Lens-Shading-Metadaten.
Ein Kompiliererfolg eines einzelnen Generators wäre kein geprüfter App-Build.

Diese APK enthält keine der beiden Bibliotheken. Ihre Lizenz bleibt Apache 2.0.
Eine kombinierte App mit übernommenem GPL-Code muss beim Verteilen die GPL-
Pflichten einschließlich passenden Quelltexts und Buildinformationen erfüllen.
Die Lizenzen aller nativen Abhängigkeiten müssen vor einer Veröffentlichung
zusätzlich geprüft werden; eine vollständige Drittanbieter-Lizenzprüfung steht aus.

## HDR-Verfügbarkeit prüfen

CameraX 1.6.2 ersetzt 1.4.2. compileSdk 36 ist für die neuen AARs erforderlich;
targetSdk bleibt 35. Das garantiert keine zusätzliche Herstellerverarbeitung.
Der Export protokolliert getrennt CameraX-Verfügbarkeit und direkt gemeldete
Camera2-Extension-Typen, JPEG/YUV/JPEG_R-Größen und Request-/Result-Keys.
Camera2-Meldungen sind advertisedOnlyNotCaptured: keine Behauptung eines
funktionierenden Camera2ExtensionSession-Captures. Der Referenztest versucht
die von CameraX angebotenen Modi. Nicht verfügbare Modi werden übersprungen.
Nur wenn beide APIs HDR nicht anbieten, ist eine bloße CameraX-Auswahl als
Ursache weitgehend ausgeschlossen. Die proprietäre Pixel-Kamera-Pipeline
ist keine allgemeine Camera2-/CameraX-Garantie.

## Einmaliger Test auf dem Pixel

1. Wir lassen mindestens 1 GB frei und stützen das Handy fest ab. Wir platzieren ein bedrucktes Motiv mit
   feinen Details etwa 50 cm entfernt bei gleichbleibendem Licht.
2. Wir drücken BIB-TEST und bestätigen Test starten. Motiv und Handy halten wir still.
3. Wir warten, bis LIBRARY fertig erscheint. Nicht unterstützte RAW-/Extension-
   Schritte werden mit Grund gemeldet; fehlende Serien sind kein Vergleich.
4. Wir speichern BIB ZIP, warten auf Export gespeichert und senden anschließend die ZIP.
   Größenordnung 250 MB. Macrotest ist separat; sein Export heißt DIAGNOSE ZIP.

Je Hauptkamera/UW entstehen fünf RAW+JPEG-Paare bei fixierter Belichtung,
WB und Fokus. RAW-/JPEG-/Result-Zeitstempel und angewendete Einstellungen
werden pro Frame geprüft. Die Aufnahme verwendet eine Sitzung, aber schreibt
DNGs zwischen den Einzelaufnahmen. Sie ist kein schneller ZSL-Burst und kein
Test auf Bewegungsrobustheit. Tatsächliche Abstände werden aus Zeitstempeln
berechnet. AUTO/HDR/NIGHT-Referenzen sind unabhängige Aufnahmen mit eigener
Belichtung; sie dürfen nicht als identischer Eingang verglichen werden.

## Auswertung und nächster Meilenstein

```bash
python -m pip install numpy rawpy
python tools/validate_library_zip.py seafrogs-library-TIMESTAMP.zip --output validation.json
```

Die Prüfung kontrolliert ZIP/Export, vollständige Serien, RAW-/JPEG-/Result-
Zuordnung, fixe Einstellungen, CFA/Schwarz-/Weißpegel, RAW-Größen und fünf
unterschiedliche Sensorframes. Sie erzeugt keine Qualitätsnote. Original-DNGs
bleiben unverändert. Eine ältere ZIP ohne RAW-Serie wird zurückgewiesen.

Danach führen wir beide vollständigen Engines mit denselben RAWs, dokumentierter
Metadaten-Konvertierung und kontrollierter Ausgabegröße aus. Wir vergleichen Single-
Frame-Referenz, Rauschen in flachen Bereichen, Detailerhalt, Farbwiedergabe,
Artefakte, Laufzeit und Speicherbedarf. Wir treffen die Auswahl anhand tatsächlicher
Ergebnisse. Erst anschließend integrieren wir die passende Engine
und prüfen Aufnahmezeit, Bewegung, Temperatur und Macro auf dem Pixel.

## Primärquellen

- https://github.com/timothybrooks/hdr-plus
- https://github.com/f0enix/motioncam
- https://developer.android.com/jetpack/androidx/releases/camera
- https://developer.android.com/reference/android/hardware/camera2/CameraExtensionCharacteristics
- https://developer.android.com/media/camera/camerax/extensions-api
