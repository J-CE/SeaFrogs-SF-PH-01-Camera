# 0.8.0: Foto und Video statt weiterer Mehrbild-Testschleifen

Wir stellen in diesem Meilenstein unsere normale Bedienung auf Foto und Video um.

Wir bewahren diesen Ablauf als historischen Versionsstand. In RC1 sind die damaligen Test-/Exportbuttons teilweise entfernt; für die heutige Bedienung verwenden wir [DIVE-0.8.1.md](DIVE-0.8.1.md) und [RC1.md](RC1.md).

Mit unseren Tests 0.7.0 bis 0.7.2 haben wir keinen überzeugenden Qualitätsgewinn der
experimentellen MIT-Verarbeitung belegt. Sie bleibt als Offline-Experiment erhalten,
aber STANDARD ist Default, nativeFusion-Einstellungen aktivieren sie nicht
mehr automatisch, der Qualitätszyklus enthält keinen MEHRBILD-Eintrag und
die Test-/Exportschaltflächen sind aus der Oberfläche entfernt.

## Bedienung

| Eingabe | Foto | Video |
| --- | --- | --- |
| Links | Hauptkamera → Macro → UW | derselbe Zyklus, während REC gesperrt |
| Hoch | vorhandener Zoomzyklus | vorhandener Zoomzyklus |
| Rechts | EV 0/+1/+2/−1/−2 | derselbe EV-Zyklus |
| Runter | Video öffnen | Foto öffnen, während REC gesperrt |
| Klick | Foto auslösen | Start/Stop |

In diesem damaligen Stand führen wir keine Doppel-/Kombinationstasten ein. Seit 0.8.1 verwenden wir die zwei Umschaltgesten gemäß [DIVE-0.8.1.md](DIVE-0.8.1.md).
Normalfläche: große Kamera-, Zoom-, EV-, Auslöser- und Modusschaltflächen.
SETUP zeigt Format, Sensor-ISO/Digitalgrenzen, Video-FPS, reale Foto-Extensions,
Mausauswahl, Neustart, HID-Diagnose, Kamera-Diagnose-ZIP und Macrotest.
Normale Statusanzeige blendet ausführliche Kameradiagnose aus.

## Videoarchitektur

CameraX camera-video 1.6.2, getrennte Preview+VideoCapture-Sitzung statt JPEG+
Video gleichzeitig. Quality.UHD ohne niedrigeren Fallback, FPS-Zielbereich
[30,30] oder [60,60]. Grundprüfung auf UHD-Profil und unterstützten Sensor-FPS-
Bereich; eine fehlgeschlagene Bindung meldet den Fehler und kehrt zu Foto zurück.
Camera2Interop bindet UW/Macro auch für Video an den ausgewählten physischen
Sensor. Qualität/60-FPS-Verfügbarkeit muss der echte Pixel bestätigen; der
angeforderte FPS-Bereich allein garantiert keine tatsächlich kodierten 60 FPS.

Recorder speichert stille MP4s in Movies/SeaFrogs. Es gibt keine zusätzliche
Mikrofonberechtigung. REC zeigt Dauer, Stop wartet auf Finalize, Fehler sind
sichtbar. Mindestreserve beim Start 256 MiB, Dateigröße begrenzt auf verfügbaren
Speicher minus 128 MiB; andere Prozesse können währenddessen Speicher belegen.
Verlassen der App stoppt die Aufnahme. Alte Finalize-Ereignisse protokollieren
das Ergebnis, ändern aber keine neuere Kamerasitzung. Kamera-/Moduswechsel
während Aufnahme sind gesperrt; Orientierung bleibt für die Aufnahme fix.

Foto-ISO-/Digitalgrenzen greifen nicht im Videomodus. Video verwendet Auto-
Belichtung plus EV. Setup-Werte bleiben für die Rückkehr zu Foto erhalten.
DIAGNOSE ZIP protokolliert tatsächliche MP4-Auflösung, Metadaten-FPS sofern
verfügbar, Dauer, Größe, Fehler und Camera2-FPS-/FrameDuration-Meldungen.
Die Clips selbst liegen in der Galerie, nicht im Diagnose-ZIP.

## Kleiner Pixel-Meilenstein

Update der SeaFrogs Test App, 0.7.2 bleibt signaturkompatibel.

1. Wir nehmen ein STANDARD-Foto mit der Hauptkamera auf und öffnen es in der Galerie.
2. Wir drücken RUNTER und KLICK, filmen fünf Sekunden und drücken erneut KLICK. Wir prüfen REC und anschließend die Speicherung.
3. Wir wechseln mit RUNTER zurück und nehmen ein normales Foto auf. Optional prüfen wir Zoom/EV beim Filmen.

Danach exportieren wir über SETUP → DIAGNOSE ZIP. Für den ersten Test verwenden wir 4K30.
4K60 und UW/Macro folgen nur, wenn dieser Basisablauf funktioniert. Kein neuer
Mehrbild-ZIP und kein großer RAW-Upload erforderlich. Macro-Nahfokus bleibt
unbestätigt; der vorhandene AF-Neustart ist kein Beleg für scharfe Nahaufnahmen.

Primärquelle: https://developer.android.com/media/camera/camerax/video-capture
FPS-API: https://developer.android.com/reference/androidx/camera/video/VideoCapture.Builder
