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
