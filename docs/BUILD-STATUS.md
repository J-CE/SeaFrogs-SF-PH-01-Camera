# Build-Status 0.2.0-photo

2026-10-07: Hauptkamera, Live-Vorschau und JPEG-Aufnahme implementiert.
Manifest-XML statisch geprüft. Vollständiges JDK 17 und Android SDK 35 vorhanden.
Ein eigener Gradle-Cache vermeidet die vorherige Cache-Sperre.

Der erste Build-Versuch erreichte beim Download der Android-/Kotlin-Abhängigkeiten
sein Zeitlimit vor der App-Kompilierung. Build und Lint laufen erneut mit gefülltem
Cache. Solange kein erfolgreicher Abschluss dokumentiert ist, gilt die APK als
**nicht verifiziert**. Der Pixel-8-Hardwaretest bleibt offen.

Lokaler Meilenstein:

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

Bei erfolgreichem Build die Debug-APK installieren und CAMERA-TEST.md abarbeiten.
Die separate HID-Diagnose anhand PIXEL8-TEST.md prüfen.

GitHub-Schreibzugriff am 2026-10-07 bestätigt. Source auf main übertragen.
