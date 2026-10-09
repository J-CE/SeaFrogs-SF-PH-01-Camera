# ISO-/Zeitgrenzen und Vergleichstest 0.6.4

Wir prüfen hier die Sensor-ISO-/Zeitgrenzen und dokumentieren die anschließende UW-Korrektur aus 0.6.5.

Wir bewahren diesen Ablauf als historischen Versionsstand. In RC1 sind die damaligen Test-/Exportbuttons teilweise entfernt; für die heutige Bedienung verwenden wir [DIVE-0.8.1.md](DIVE-0.8.1.md) und [RC1.md](RC1.md).

## Gerätetest und Korrektur 0.6.5

Der vollständige Export seafrogs-iso-1791404655261.zip enthält vier lesbare
Original-JPEGs, sechs Ergebnisdatensätze, keine CRC-/Exportfehler, keine
verlorenen Protokolleinträge und keine Schreibfehler.

| Aufnahme | Sensor-ISO | Zeit | JPEG-EXIF-ISO | Ergebnis |
| --- | ---: | ---: | ---: | --- |
| Hauptkamera Auto | 667 | 29.997374 ms | 1827 | gespeichert |
| Hauptkamera Grenze 800 | 667 | 29.997374 ms | 1827 | Grenzen bestätigt |
| Hauptkamera Grenze 400 | 400 | 33.329016 ms | 1096 | Grenzen bestätigt, −0.586 EV Sensorbelichtung |
| UW Auto | 762 | 39.998667 ms | 1737 | gespeichert |
| UW Grenze 800 | keine Aufnahme | keine Aufnahme | fehlt | Request-Builder-Fehler |
| UW Grenze 400 | keine Aufnahme | keine Aufnahme | fehlt | Request-Builder-Fehler |

Tatsächliche Dateigrößen: Hauptkamera 4080 × 3072, UW 4000 × 3000.
JPEG und Sensorresultate haben identische Zeitstempel; alle vier Aufnahmen
melden AF 2 vor und bei der Aufnahme. Bei beiden begrenzten Hauptkamera-
Fotos bestätigt der Sensor die Grenzen und den übernommenen Post-RAW-Gain
274 Prozent. Die EXIF-ISO entspricht hier Sensor-ISO × 2.74 (gerundet).
Die ISO-Grenze begrenzt daher die Sensorverstärkung, nicht zusätzlich die
JPEG-Verstärkung. 0.6.5 benennt dies ausdrücklich im Setup und Status und
liest die tatsächliche JPEG-EXIF-ISO in die Aufnahmeevidenz ein.

Beide UW-Limit-Aufnahmen scheiterten mit Physical camera id: 3 is not valid.
Der Code hatte die Ausgabe physisch gepinnt, aber den CaptureRequest.Builder
ohne physische ID angelegt. setPhysicalCameraKey benötigt zusätzlich
createCaptureRequest(template, physicalCameraIdSet). 0.6.5 initialisiert diesen
Builder für begrenzte physische Still-Aufnahmen und kopiert unterstützte
physische Einstellungen aus dem gewählten Request, bevor es nur die
Belichtungswerte überschreibt. Unbegrenzte JPEG-/RAW-Aufnahmen behalten den
zuvor geprüften Request-Weg. Kein stiller Fallback auf die Hauptkamera.

Nach Installation von 0.6.5 wiederholen wir den ISO-TEST mit 1/30 s und
exportieren ISO ZIP. Die bisherige Serie bestätigt die Hauptkamera-Sensorgrenzen,
aber keine begrenzte UW-/Macro-Aufnahme und keinen messbaren Rauschvorteil.

## Bedienung

Über die ISO-Taste öffnen wir das Setup vor dem Tauchgang. ISO: Auto, maximal 400,
800 oder 1600. Längste Zeit bei aktiver ISO-Grenze: 1/30, 1/60 oder 1/125 s.
Unsere App speichert beide Werte. Die erste Installation startet mit Auto.
Auto begrenzt weder ISO noch Zeit. Zeitgrenzen gelten nur zusammen mit einer
aktiven ISO-Grenze; unsere App bietet hier keinen separaten Zeitprioritätsmodus.

Eine aktive Grenze verwendet STANDARD ohne OEM-Extensions. Die Vorschau
misst automatisch und zeigt weiter die automatische Belichtung. Erst beim
Foto wechselt unsere App temporär von CameraX zum Camera2-Aufnahmeweg; die
Vorschau pausiert bis zum Speichern. JPEG only bleibt JPEG only. RAW+JPEG
speichert weiterhin beide Dateien aus derselben Belichtung.

Wir übernehmen die frische AF-/AE-bestätigte Messung einschließlich EV
und berechnen ISO und Zeit. Dabei erhalten wir das Produkt ISO × Zeit, sofern beide
Obergrenzen und die Sensorranges das erlauben. Eine kurze Zeitgrenze darf
ISO innerhalb der gewählten ISO-Grenze erhöhen. Reicht das nicht, bleibt das
Foto dunkler; unsere App zeigt DUNKLER DURCH LIMIT und die berechnete Abweichung
in EV. Diese Anzeige beschreibt die Sensorbelichtung relativ zur Messung,
keine gemessene JPEG-Helligkeit oder Garantie zur Rauschverbesserung.

Die finale Aufnahme deaktiviert nur AE. AF und AWB bleiben aktiv. Unsere App
übernimmt eine verfügbare, einstellbare Post-RAW-Verstärkung des Messwerts
für das JPEG und protokolliert deren tatsächlich gemeldeten Wert. Physisch
gepinnte UW-/Macro-Ausgaben verwenden ihre eigenen Sensormetadaten und
gegebenenfalls unterstützte physische Request-Keys. Sie fallen bei fehlenden
physischen Ergebnissen nicht auf logische Sensormetadaten zurück.

Wir prüfen am tatsächlichen CaptureResult ISO, Zeit und AE-OFF. Eine
überschrittene oder unbestätigte Grenze meldet einen Fehler; das gespeicherte
Foto bleibt zur Diagnose erhalten. Der automatische Test kennzeichnet eine
solche Aufnahme als FAILED, statt eine erfolgreiche Begrenzung zu behaupten.

## Automatischer Gerätetest

1. Wir installieren die APK als Update. Wir stützen das Handy ab, stellen ein bedrucktes Motiv ungefähr
   50 cm vor die Linse und halten Beleuchtung und Motiv konstant.
2. Im ISO-Setup wählen wir die längste Zeit, für den ersten Vergleich 1/30 s.
   Die gewählte ISO-Obergrenze ändert die Testfolge nicht.
3. Wir starten ISO-TEST und warten bis ISO fertig. Unsere App nimmt sechs JPEGs auf:
   Hauptkamera Auto/800/400, anschließend UW Auto/800/400. Jeweils ungecroppt,
   EV 0, STANDARD, identischer nativer Aufnahmeweg. Auto begrenzt keine Zeit.
4. Wir exportieren ISO ZIP und laden sie erst nach der Meldung Export gespeichert hoch.
   Dieses Paket enthält nur ISO-Vergleichsfotos, Ergebnisbericht,
   Kamera-Fähigkeitsbericht und das aktuelle Ereignisprotokoll. TEST ZIP
   enthält weiterhin alle gespeicherten Testgruppen einschließlich RAW.

Der Test setzt Format, ISO-/Zeitgrenzen und Extensions nur vorübergehend.
Danach gelten wieder die gespeicherten Setup-Werte. Er prüft angewendete
Sensorwerte, AF/AE vor der Aufnahme, Zeitstempel und Dateierfolg. Er bewertet
Rauschen und Schärfe nicht automatisch. Die Geräteabnahme der ISO-Grenzen
steht bis zum neuen Pixel-8-Ergebnis aus.

## RAW-Abnahme 0.6.3

Alle drei separat hochgeladenen DNGs ließen sich vollständig als Bayer-Daten
lesen und mit LibRaw entwickeln. Hauptkamera: 4080 × 3072, ISO 667,
29.997374 ms, 6.9 mm. UW/Macro: 4032 × 3016, ISO 762, 39.998667 ms,
1.95 mm. DNG 1.4, korrekte Orientierung, Weißabgleich und unterschiedliche
Farbmatrizen/CFA für Hauptkamera und UW. Die beiden UW-Modi besitzen dieselbe
Sensorkalibrierung. Die Dateizeitstempel passen zum jeweiligen RAW+JPEG-
CaptureResult des Protokolls; alle Bildstreifen lagen vollständig im Dateiumfang.

RAW-Sensorgröße versus DefaultCropSize: Hauptkamera 4064 × 3056 innerhalb
4080 × 3072; UW/Macro 4016 × 3000 innerhalb 4032 × 3016. Ein RAW-Konverter
kann diesen kleinen Randbeschnitt anwenden. Kein 50-MP-Aufnahmeweg in den
gemeldeten Größen. Die RAW-Serie prüfte entfernte Motive und bestätigt daher
keinen Nahfokus. Das ursprüngliche ZIP brach vor den RAW-Dateien ab; Ursache
ungeklärt. Die separat gelieferten DNGs sind vollständig.

## Technische Referenzen

Android CaptureRequest: SENSOR_SENSITIVITY, SENSOR_EXPOSURE_TIME,
CONTROL_AE_MODE und CONTROL_POST_RAW_SENSITIVITY_BOOST:
https://developer.android.com/reference/android/hardware/camera2/CaptureRequest

Physische Request-Keys:
https://developer.android.com/reference/android/hardware/camera2/CaptureRequest.Builder

Eine gewöhnliche AE-ON-Anfrage kann den manuellen ISO-Wert überschreiben.
AE-Priorität benötigt eine ausdrücklich unterstützte optionale Fähigkeit;
dieser Build verlangt diese nicht und behält compileSdk 35/CameraX 1.4.2.
