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
