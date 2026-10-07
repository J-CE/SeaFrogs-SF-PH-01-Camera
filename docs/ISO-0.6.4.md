# ISO-/Zeitgrenzen und Vergleichstest 0.6.4

## Bedienung

Die ISO-Taste öffnet das Setup vor dem Tauchgang. ISO: Auto, maximal 400,
800 oder 1600. Längste Zeit bei aktiver ISO-Grenze: 1/30, 1/60 oder 1/125 s.
Die App speichert beide Werte. Die erste Installation startet mit Auto.
Auto begrenzt weder ISO noch Zeit. Zeitgrenzen gelten nur zusammen mit einer
aktiven ISO-Grenze; die App bietet hier keinen separaten Zeitprioritätsmodus.

Eine aktive Grenze verwendet STANDARD ohne OEM-Extensions. Die Vorschau
misst automatisch und zeigt weiter die automatische Belichtung. Erst beim
Foto wechselt die App temporär von CameraX zum Camera2-Aufnahmeweg; die
Vorschau pausiert bis zum Speichern. JPEG only bleibt JPEG only. RAW+JPEG
speichert weiterhin beide Dateien aus derselben Belichtung.

Die App übernimmt die frische AF-/AE-bestätigte Messung einschließlich EV
und berechnet ISO und Zeit. Sie erhält das Produkt ISO × Zeit, sofern beide
Obergrenzen und die Sensorranges das erlauben. Eine kurze Zeitgrenze darf
ISO innerhalb der gewählten ISO-Grenze erhöhen. Reicht das nicht, bleibt das
Foto dunkler; die App zeigt DUNKLER DURCH LIMIT und die berechnete Abweichung
in EV. Diese Anzeige beschreibt die Sensorbelichtung relativ zur Messung,
keine gemessene JPEG-Helligkeit oder Garantie zur Rauschverbesserung.

Die finale Aufnahme deaktiviert nur AE. AF und AWB bleiben aktiv. Die App
übernimmt eine verfügbare, einstellbare Post-RAW-Verstärkung des Messwerts
für das JPEG und protokolliert deren tatsächlich gemeldeten Wert. Physisch
gepinnte UW-/Macro-Ausgaben verwenden ihre eigenen Sensormetadaten und
gegebenenfalls unterstützte physische Request-Keys. Sie fallen bei fehlenden
physischen Ergebnissen nicht auf logische Sensormetadaten zurück.

Die App prüft am tatsächlichen CaptureResult ISO, Zeit und AE-OFF. Eine
überschrittene oder unbestätigte Grenze meldet einen Fehler; das gespeicherte
Foto bleibt zur Diagnose erhalten. Der automatische Test kennzeichnet eine
solche Aufnahme als FAILED, statt eine erfolgreiche Begrenzung zu behaupten.

## Automatischer Gerätetest

1. APK als Update installieren. Handy abstützen, bedrucktes Motiv ungefähr
   50 cm vor die Linse stellen, Beleuchtung und Motiv konstant halten.
2. Im ISO-Setup die längste Zeit wählen. Für den ersten Vergleich 1/30 s.
   Die gewählte ISO-Obergrenze ändert die Testfolge nicht.
3. ISO-TEST starten und bis ISO fertig warten. Die App nimmt sechs JPEGs auf:
   Hauptkamera Auto/800/400, anschließend UW Auto/800/400. Jeweils ungecroppt,
   EV 0, STANDARD, identischer nativer Aufnahmeweg. Auto begrenzt keine Zeit.
4. ISO ZIP exportieren. Erst nach der Meldung Export gespeichert hochladen.
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
