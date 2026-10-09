# 0.7.1: Ausgabe und Aufnahmeablauf

Wir dokumentieren hier unsere damaligen Korrekturen an Ausgabe und Ablauf des experimentellen Mehrbildwegs.

Wir bewahren diesen Ablauf als historischen Versionsstand. In RC1 sind die damaligen Test-/Exportbuttons teilweise entfernt; für die heutige Bedienung verwenden wir [DIVE-0.8.1.md](DIVE-0.8.1.md) und [RC1.md](RC1.md).

## Änderungen

In der MIT-Ausgabe verwenden wir nun die Farbentrauschung vor der sRGB-Farbmatrix.
Vorher berechnete die Quelle diese Stufe, führte sie aber nicht weiter. Die
Tonwertfaktoren gelten geometrisch über die drei Durchläufe; 1/1 nimmt direkt
die unveränderte lineare sRGB-Ausgabe vor Gamma/Kontrast. Die bisherige zusätzliche
Aufhellung entfällt. Weißabgleich/Matrix/LSC bleiben metadatengesteuert.

Im Sensoraufnahmeweg setzen wir Videostabilisierung OFF und bei gemeldeter
Unterstützung SCALER_ROTATE_AND_CROP_NONE. JPEG-Orientierung bleibt explizit.
Die Ausgabe berücksichtigt gemeldete Sensor-/RAW-Grenzen und wendet digitalen
Zoom genau einmal an, auch auf UW/Macro und auf dem älteren CropRegion-Pfad.
Kein fest geschätzter 1,16×-Faktor kompensiert den bisherigen Bildausschnitt.

Aufnahmestufen zeigen Vorbereitung, Frame 1/5 bis 5/5, Verarbeitung und Speichern.
Abbruch berücksichtigt die nächste Aufnahmegrenze und bewahrt gespeicherte
Fotos. Ergebnisbenachrichtigung folgt erst nach Beginn der Vorschau-Wiederbindung.
Der Auslöser zeigt WARTE, solange die Kamera nicht bereit ist; ein frühzeitiges
Bereit im Kamerastatus erscheint als Warte auf Vorschau/Fokus.

Die Sensoraufnahme prüft freien Speicher: mindestens 80 MiB für JPEG und 160 MiB
für RAW+JPEG, konservative Reserven inklusive Rückfallbild. Die UI zeigt freien
Speicher sowie tatsächliche Sensor-ISO und JPEG-ISO nach dem Foto. Sensor-ISO-
Limits begrenzen weiterhin nicht die separate post-RAW-JPEG-Verstärkung.

## Was der Pixel-Test 0.7.0 tatsächlich bestätigte

Alle drei Fusionen erfolgreich, jeweils fünf unterschiedliche Sensorzeitstempel,
etwa 1,00 s für die Serie und 3,16–3,26 s für Verarbeitung/Speichern. MAIN:
3072×4080, Sensor-ISO 667. UW: 3016×4021, Sensor-ISO 762. Keine Exportfehler.
Das ISO-800-Limit griff nicht aktiv ein, weil die Messung bereits darunter lag.
Dateien zeigen native Helligkeits-/Farbprobleme und kleinere Motivdarstellung.
In den gematchten Hauptkameraansichten beträgt der geometrische Maßstab ungefähr
0,862. Vorschau-CropRegion bleibt voll; still-Result-Geometrie fehlte im alten
Bericht. Die genaue Ursache ist damit nicht bewiesen.

Der neue Bericht enthält Sensor-/logische CropRegion, RAW Image.cropRect,
RotateAndCrop, Videostabilisierung, DistortionCorrection, Intrinsics und
LensDistortion, soweit der Sensor diese Werte tatsächlich liefert. Diese
Informationen unterscheiden zusätzliche HAL-Geometrie von unseren Cropfehlern.
Die Pixel-Abnahme muss zeigen, ob die expliziten Kontrollen die Differenz beheben.
Eine vollständige RAW-Verzeichnungskorrektur ist weiterhin nicht implementiert.

Android-Referenz: https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#SCALER_ROTATE_AND_CROP

## Nächster kleiner Test

Wir stützen das Handy ab und verwenden dasselbe bedruckte Motiv bei gleichbleibendem Licht.
Wir wählen MEHRBILD TEST → START, warten auf den Abschluss, exportieren TEST ZIP und laden sie hoch. Der dritte Schritt nutzt
jetzt **Sensor-ISO 400** und maximal 1/30 s. Bei der bisherigen Messung ISO 667
muss die Begrenzung eingreifen; das Foto darf dadurch dunkler werden und meldet
die Abweichung. Das Testmenü ändert keine gespeicherten Benutzerlimits.

Mit den sechs JPEGs prüfen wir Tonwerte, Farbsäume und Bildausschnitt. Der Bericht prüft
angewendete Grenzen/Geometrie und tatsächliche Aufnahmezeit. Kein neuer großer
RAW-Export. Macro-Nahfokus, bewegte Motive und Video bleiben eigene Meilensteine.
