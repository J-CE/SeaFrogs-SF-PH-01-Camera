# RAW + JPEG 0.6.3

Wir ergänzen in diesem Meilenstein RAW+JPEG und prüfen Dateiformat, Metadaten und Sensorzuordnung.

Wir bewahren diesen Ablauf als historischen Versionsstand. In RC1 sind die damaligen Test-/Exportbuttons teilweise entfernt; für die heutige Bedienung verwenden wir [DIVE-0.8.1.md](DIVE-0.8.1.md) und [RC1.md](RC1.md).

## Bedienung und Test

Über FORMAT wählen wir JPEG only / RAW + JPEG (DNG) und speichern unsere
Wahl vor dem Tauchgang. Im RAW-Modus bleibt STANDARD aktiv; Extensions lassen
sich nur bei JPEG-only wählen. FOTO und der HID-Klick verwenden dieselbe Wahl.

RAW-TEST benötigt ein bedrucktes Motiv etwa 50 cm vor dem abgestützten Handy
bei gutem konstantem Licht. Er übernimmt drei Aufnahmen: Hauptkamera, UW und
Macro, jeweils ohne Crop, EV 0, STANDARD. Dieser Test prüft RAW-Dateiformat und
Sensorzuordnung; den Nahfokus prüft weiterhin MACROTEST bei etwa 5 cm.
TEST ZIP enthält die JPEG- und DNG-Originale samt Aufnahmebericht.
Normal-/Macro-/RAW-Ergebnisgruppen bleiben unabhängig erhalten.

Macro hat nur noch die sensorrelativen Faktoren 1 und 2, angezeigt als
0,5× und 1× Crop. Faktor 4 / 2× Crop entfällt auch im neuen Macrotest.
Historische Ergebnisse ändern sich erst beim erneuten Lauf dieser Testgruppe.

## Aufnahmeweg

Für JPEG-only nutzen wir in diesem Versionsstand weiterhin CameraX 1.4.2 und die bestätigte UW-Auflösungswahl.
RAW+JPEG gibt vorübergehend die CameraX-Use-Cases frei und öffnet eine eigene
kurze Camera2-Session. Die Vorschau pausiert während dieser Aufnahme, der
Status und gesperrte Bedienelemente zeigen den Vorgang. Anschließend schließt
Camera2 und CameraX stellt die bisherige Vorschau/Einstellungen wieder her.
Die RAW-Session verwendet denselben ermittelten Kameraroute, keine festen IDs.

JPEG und RAW sind Ziele derselben Still-Capture-Anforderung. DNG verwendet die
Characteristics und den CaptureResult des gewählten Sensors, bei gepinntem UW
die physischen Werte. Wir verlangen übereinstimmende Sensorzeitstempel von
JPEG, RAW und CaptureResult. Fehlende physische Metadaten, unpassende Größen
oder nicht unterstützte Streamkombinationen melden einen Fehler; kein
stillschweigender Wechsel auf eine andere Kamera oder JPEG-only.

Die Session fokussiert mit kontinuierlichem AF und wartet auf stabilen AF und
konvergierte AE. DNG stammt aus ImageFormat.RAW_SENSOR und DngCreator.
RAW enthält Sensorpixel; JPEG-Crop und JPEG-Ausgabegröße bestimmen nicht die
Anzahl der RAW-Pixel. Crop-Verhalten und DNG-DefaultCrop-Tags müssen auf dem
Gerät geprüft werden, besonders bei Zoom.

Der RAW-Bericht speichert tatsächliche RAW-Dimensionen, ISO, Belichtungszeit,
AF und Brennweite aus dem Still-CaptureResult. Ergänzte JPEG-EXIF enthält diese
Werte zusätzlich zur ausdrücklich als Vorschau gekennzeichneten CameraX-
Telemetrie. DNG-Beschreibung enthält App-/Routen-/Aufnahmedaten. Die JPEG-
Pixel bleiben unverändert; das Programm ergänzt ihre EXIF ohne Neukompression.
Dateien landen über MediaStore unter Pictures/SeaFrogs. Unvollständige
Dateien entfernt der Schreibweg; bereits vollständige JPEGs bleiben bei einem
anschließenden DNG-Fehler erhalten und erscheinen im Ergebnis als FAILED.

Nach 25 Sekunden beendet der Aufnahmeweg den Versuch mit einem Fehler.
Lifecycle-Wechsel beendet die native Session. CameraX öffnet erst nach
Camera2-Schließen erneut. Kamera-Busy beim Übergang kann bis zu fünf Sekunden
wiederholt werden. Auf Android 8/9 gilt weiterhin Legacy-Speicherberechtigung.

## Abnahme

Für die neue Hardwareprüfung untersuchen wir: DNG-Abmessungen und Rohdatenstruktur,
korrekte physische Sensor-/Farbmetadaten, JPEG/DNG-Paarung, Orientierung,
Vorschau-Rückkehr und wiederholte HID-Auslösung. Build und bestehende
Unit-Tests ersetzen diese Geräteprüfung nicht.

Erwartete maximale RAW-Kandidaten aus 0.6.1: MAIN 4080×3072,
UW/Macro 4032×3016. Dies sind bis zum echten DNG-Test angebotene Größen.
Das ZIP kann mit DNGs erheblich größer sein als der bisherige JPEG-Test.

Referenzen:
- https://developer.android.com/reference/android/hardware/camera2/DngCreator
- https://developer.android.com/reference/android/hardware/camera2/params/OutputConfiguration
