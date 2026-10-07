# Build-Status 0.2.0-photo

2026-10-07: **assembleDebug und lintDebug erfolgreich** mit AGP 8.9.2,
Gradle 8.11.1, Kotlin 2.1.20, JDK 17 und Android SDK 35.
Die Debug-APK trägt Paket de.jce.seafrogs, versionCode 2 und
versionName 0.2.0-photo. apksigner bestätigt ihre APK-v2-Signatur.

Lint: 0 Fehler, 30 Warnungen. Die Warnungen betreffen Ressourcen/Lokalisierung,
KTX-Stil, fehlendes Launcher-Icon und die noch nicht explizit definierten
Android-12-Datenübertragungsregeln. Die App setzt allowBackup=false.
Compilerwarnungen betreffen ältere, auf API 26 weiterhin gültige WindowInsets-APIs.

Die erste Kotlin-Kompilierung fand einen IntArray/mapNotNull-Fehler in der
HID-Geräteliste. Die Korrektur wandelt die IDs vor dem Mapping in eine Liste um.
Der abschließende Build prüft sowohl Kamera- als auch Diagnosecode.

**Nicht geprüft:** Kamera-Hardware, AF, JPEG-Bildqualität, Orientierung und
Gehäuseeingaben auf dem Pixel 8. CAMERA-TEST.md und PIXEL8-TEST.md beschreiben
beide getrennten Hardware-Meilensteine. Keine gemessenen HID-Events liegen vor.

Erneuter lokaler Meilenstein bei Bedarf:

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

GitHub-Schreibzugriff bestätigt. Source auf main übertragen.

## Quelltext 0.3.0-lenses

Kamerawechsel und Macro-AF wurden nach dem obigen Build ergänzt.
Für diese Version wurde vereinbarungsgemäß kein weiterer Build/Lint- oder
Gerätetest ausgeführt. Die vorhandene 0.2.0-APK enthält diese Änderungen nicht.
Nächster gemeinsamer Testmeilenstein: nach Zoom- und EV-Zyklen.

## Meilenstein 0.4.0-controls, 2026-10-07

assembleDebug, lintDebug und testDebugUnitTest erfolgreich mit den unveränderten
Projektversionen AGP 8.9.2 / Gradle 8.11.1 / Kotlin 2.1.20 / JDK 17 / SDK 35.
Die Prüfung umfasst auch den zuvor ungebauten Objektivcode aus 0.3.0.

Vier Zyklustests: 4 bestanden, 0 Fehler, 0 übersprungen.
Lint: 0 Fehler, 39 Warnungen. Compiler: ältere WindowInsets-Zugriffe als deprecated.
APK: de.jce.seafrogs, versionCode 4, versionName 0.4.0-controls, minSdk 26.
apksigner bestätigt die APK-v2-Signatur. Kein Pixel-Hardwaretest durchgeführt.

APK-SHA256:
3a75e59c8624298e45cf77b47c4d589523836114d0630188677217bbe416df38

Der neue Debug-Schlüssel unterscheidet sich vom Schlüssel der ausgelieferten
0.2.0-APK. Eine bestehende Installation dieser APK muss vor Installation von
0.4.0 entfernt werden; vorher benötigte HID-Protokolle exportieren.

Die Build-Umgebung benötigte eine erneute Abhängigkeitsbeschaffung und den
JDK-17-Compiler. Diese Umgebungsarbeiten ändern keine Projektversionen.

## Meilenstein 0.4.1-exif, 2026-10-07

assembleDebug, lintDebug und testDebugUnitTest erfolgreich. Vier Zyklustests
bestanden, Lint 0 Fehler und 39 Warnungen. EV-Zyklus nun 0/+1/+2/−1/−2,
weiterhin an unterstützte Schrittweite und Grenzen angepasst. JPEGs erhalten
ergänzende EXIF-Diagnosedaten ohne erneute Bildkompression.
APK versionCode 5, versionName 0.4.1-exif. APK-SHA256: da100a0a948292623366abb8570dae9ead23d1f418238c84d058fdf1efb5cdd4
EXIF-Schreiben und Bildqualität auf Pixel 8 noch nicht geprüft.

## Gemeinsamer Meilenstein 0.5.0-quality-hid, 2026-10-07

assembleDebug und lintDebug erfolgreich. 10 Unit-Tests bestanden (4 Kamera-
zyklen, 6 HID-Burst-/Kombinationsfälle), 0 Fehler, 0 übersprungen. Nach der
abschließenden Layoutkorrektur nur Build/Lint wiederholt; Logik unverändert.
Lint: 0 Fehler, 54 Warnungen (vorwiegend Textlokalisierung, vorhandene
Ressourcen-/KTX-Hinweise). APK versionCode 6, versionName 0.5.0-quality-hid.
Kamera-Hardware, Extensions-Bildqualität und reale kombinierte Gehäuseeingaben
noch nicht getestet. TESTPROGRAMM-0.5.md legt diese Abnahme fest.
Video/RAW fehlen; DOWN bestätigt in dieser Version nur einen Diagnosebefehl.
CameraX einschließlich camera-extensions unverändert 1.4.2.
APK-SHA256: 9e5d7cb7608b46a549f4253ea6e638cb88983b869c02ed577cb1e8e1c87a7d52

## Meilenstein 0.6.0-autotest, 2026-10-07

assembleDebug, lintDebug, testDebugUnitTest erfolgreich. 12 Tests bestanden;
Lint 0 Fehler und 56 Warnungen. Automatischer Normal-/Macro-Test
mit Bereitschaftswartezeit, Original-JPEGs, EXIF, persistentem Ergebnisbericht
und gemeinsamem ZIP-Export. Hauptkamera-Zoom 1/1,5/3/5×.
Physisch gepinnte UW/Macro-Routen behalten STANDARD; Extensions werden im
Testbericht als nicht sicher zuordenbar übersprungen. Keine neue Standard-
Bildverarbeitung. RAW, Video und die längere HID-Kombinationssperre fehlen weiterhin.
Hardware-Ablauf und ZIP-Export benötigen den Pixel-8-Test gemäß AUTOTEST-0.6.md.
APK versionCode 7, versionName 0.6.0-autotest, gleicher Debug-Schlüssel wie 0.4/0.5.
APK-SHA256: c9f25f8037588183297a9853eb23a608d6b060f8864bc63a7f59dd9f483770ca

## Meilenstein 0.6.1-capabilities, 2026-10-07

assembleDebug, lintDebug und testDebugUnitTest erfolgreich. 12 bestehende
Tests bestanden, 0 Fehler/übersprungen. Lint: 0 Fehler, 57 Warnungen.
APK versionCode 8, versionName 0.6.1-capabilities; Signatur mit demselben
Debug-Schlüssel wie 0.4/0.5/0.6.0 geprüft. Installation als Update möglich.

Die neue Hintergrundabfrage sammelt Camera2-Metadaten öffentlicher und
physischer Kameras einschließlich normaler, High-Resolution- und
Maximum-Resolution-JPEG-/RAW-Ausgabegrößen. TEST ZIP enthält automatisch
camera-capabilities.json. Kein RAW-Aufnahmeweg und keine Änderung der
Kamera- oder HID-Steuerung außer den zusätzlichen Diagnosedaten.
Die Metadatenabfrage und der neue ZIP-Eintrag brauchen noch den Gerätetest;
die bestehenden Unit-Tests prüfen Zyklen, HID-Gate und Auto-Testplanung.
Sie ersetzen keine Pixel-Hardwareprüfung. Ablauf: CAPABILITIES-0.6.1.md.

APK-SHA256: 226f44d70cdbf319f8f32de7c1a2b8d2199478a42695dda3c94827148788694d

## Meilenstein 0.6.2-resolution-af, 2026-10-07

assembleDebug, lintDebug und testDebugUnitTest erfolgreich. 16 Tests bestanden
(12 bestehende, 4 neue Fokus-Frische/Stabilitätsfälle), 0 Fehler/übersprungen.
Lint: 0 Fehler, 57 Warnungen. APK versionCode 9, versionName
0.6.2-resolution-af. Signatur mit unverändertem Debug-Schlüssel geprüft.

UW/Macro verhandelt JPEG/Vorschau nur aus gemeinsamen logischen/physischen
Größen. Der automatische STANDARD-Test verlangt frischen stabilen AF vom
gewählten Sensor und überspringt die Aufnahme bei AF-Timeout. Extensions
kennzeichnen fehlende AF-Prüfbarkeit explizit. Macro-Fokus-AutoCancel 5 s.
Keine Änderung der HID-Belegung. Kein RAW-/Video-/ISO-Limit-Aufnahmeweg.
Die tatsächliche JPEG-Größe und Bildschärfe brauchen den Gerätetest gemäß
QUALITY-0.6.2.md; der Build beweist keine Pixel-Hardwarefunktion.

APK-SHA256: 027c41ad3840ffd5453aa09062b77c63b99a96c57ab23959003d06437d69c5fc

## Meilenstein 0.6.3-raw-jpeg, 2026-10-07

assembleDebug, lintDebug und testDebugUnitTest erfolgreich. 17 Tests bestanden
(Zyklen, HID-Gate, AF-Stabilität, RAW-Testplanung), 0 Fehler/übersprungen.
Lint: 0 Fehler, 62 Warnungen. APK versionCode 10, versionName 0.6.3-raw-jpeg.
Unveränderter Debug-Schlüssel; Installation als Update möglich. CameraX 1.4.2,
AGP/Kotlin/Gradle bleiben unverändert. RAW nutzt den neuen Camera2-Aufnahmeweg.

FORMAT: JPEG only / RAW+JPEG gespeichert. RAW-TEST: drei Sensor-/Modusrouten,
je ein gleichzeitiges JPEG/DNG-Paar, Originaldateien im ZIP. Physischer
CaptureResult und RAW-Characteristics müssen zum Sensor passen; JPEG/RAW/
CaptureResult müssen gleiche Sensorzeitstempel haben. Die Vorschau pausiert
temporär im RAW-Aufnahmeweg. Macro-Faktor 4 entfällt in Steuerung und Testplan.

Ein erster Compilerlauf fand einen Kotlin-Zeilenumbruch vor einem Indexzugriff;
die Korrektur verwendet einen expliziten get-Aufruf. Nach der abschließenden
Korrektur für Fehler beim Kameraöffnen wurde der gemeinsame Build wiederholt.
Kein nativer RAW-Hardwaretest ausgeführt. Die tatsächlichen DNG-Dateien,
Farbdaten und Kameraübergaben prüft der nächste Meilenstein RAW-0.6.3.md.

APK-SHA256: ee414178fda53fc4eedbe24b9896f3500e4c99879db37cf25fe58642ac284bff

## Meilenstein 0.6.4-iso-limits, 2026-10-07

assembleDebug, lintDebug und testDebugUnitTest erfolgreich. 23 Tests bestanden,
0 Fehler/übersprungen: 17 bestehende, 5 Belichtungsrechenfälle und 1 ISO-
Vergleichsplan. Lint: 0 Fehler, 70 Warnungen. Der abschließende Build umfasst
die ergänzte Unterscheidung von Vorschau-ISO und tatsächlichen Fotowerten.
APK versionCode 11, versionName 0.6.4-iso-limits. Unveränderter Debug-Schlüssel,
Installation als Update möglich. Projekt-/CameraX-Versionen unverändert.

Gespeicherte ISO-Grenzen Auto/400/800/1600 und Zeitgrenzen 1/30, 1/60, 1/125 s.
ISO-begrenzte JPEG-/RAW+JPEG-Aufnahmen nutzen die bestätigte AE-Messung als
Ausgangspunkt für eine manuelle Sensorbelichtung. Grenzen, tatsächliche ISO/
Zeit, Post-RAW-Gain und eventuelle reduzierte Sensorbelichtung stehen im
CaptureResult-Bericht und in den Dateimetadaten. Keine Extensions bei aktiver
Grenze. ISO-TEST fotografiert sechs ungecroppte STANDARD-JPEGs auf demselben
nativen Aufnahmeweg; ISO ZIP lässt ältere RAW-/Normal-/Macro-Fotos weg.
Portrait-Steuerbereich scrollt, damit alle zusätzlichen Testtasten erreichbar
bleiben. Auto ist erste Startvorgabe, gespeicherte Setup-Werte bleiben erhalten.

Die drei DNG-Dateien aus 0.6.3 sind nun vollständig gelesen und mit LibRaw
entwickelt: Hauptkamera 4080 × 3072, UW/Macro 4032 × 3016, passende CFA/
Farbmatrizen und Aufnahmemetadaten. ISO-Grenzen brauchen noch den realen
Pixel-8-Vergleich gemäß ISO-0.6.4.md; der Build ersetzt diese Abnahme nicht.

APK-SHA256: 2328a76efb9ee12e1eff19130071ffda9d19afef202aa5e00d00f6b05df212ac

## Korrektur 0.6.5-physical-iso, 2026-10-07

Der Pixel-8-Vergleich aus 0.6.4 enthält vier erfolgreiche JPEGs und zwei
fehlgeschlagene begrenzte UW-Aufnahmen. Hauptkamera: Auto und Grenze 800
je ISO 667/29.997374 ms; Grenze 400 tatsächlich ISO 400/33.329016 ms,
−0.586 EV gegenüber der Messung. Sensorgrenzen und Post-RAW-Gain bestätigt.
JPEG-EXIF-ISO 1827 beziehungsweise 1096: Sensor-ISO und zusätzliche JPEG-
Verstärkung unterscheiden sich. ZIP vollständig, keine Export-/CRC-/Logfehler.

Der UW-Request-Builder fehlte in der physicalCameraIdSet-Initialisierung.
0.6.5 korrigiert diese für begrenzte physische Still-Aufnahmen, übernimmt
unterstützte physische Settings aus dem Request und überschreibt dann nur
die Belichtung. Keine Abschaltung der physischen UW-Ausgabe, kein Fallback.
Setup/Status nennt ausdrücklich Sensor-ISO; jpegExifIso ergänzt die Evidenz.

Abschließender assembleDebug/lintDebug/testDebugUnitTest erfolgreich.
23 Tests bestanden, 0 Fehler/übersprungen; Lint 0 Fehler, 70 Warnungen.
API-Request-Builder-Initialisierung benötigt den neuen UW-Gerätetest und
lässt sich durch die JVM-Rechentests nicht bestätigen. Kein neuer Hardware-
Erfolg behauptet. APK versionCode 12/versionName 0.6.5-physical-iso, identischer
Debug-Schlüssel; Installation als Update möglich. Projektversionen unverändert.

APK-SHA256: 6d27d4de3c387cfcb956a32684d0b2a64a65c6d9e4ecec701413df119f72af26
