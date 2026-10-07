# SeaFrogs SF-PH-01 Camera

Android-Kamera-Projekt für Google Pixel 8 und SeaFrogs SF-PH-01 Pro.
Aktueller Stand: **0.1.0, ausschließlich HID-Diagnose**. Keine Kamerafunktion,
keine bestätigte SeaFrogs-Tastenbelegung und kein bestätigter Hardwaretest.

## Öffnen und bauen

Repository in Android Studio öffnen, JDK 17 für Gradle wählen, Android SDK 35
installieren und Gradle synchronisieren. Fest gepinnt: AGP 8.9.2, Kotlin 2.1.20,
Gradle 8.11.1. Mindestversion Android 8.0 (API 26).

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

Linux/macOS: `./gradlew :app:assembleDebug :app:lintDebug`.
APK: `app/build/outputs/apk/debug/app-debug.apk`.
Android Studio kann die App per USB-Debugging direkt auf dem Pixel installieren.

## Diagnose

Das Gehäuse zuerst in Androids Bluetooth-Einstellungen koppeln. Die App benötigt
keine Bluetooth-, Kamera-, Mikrofon- oder allgemeine Speicherberechtigung:
Android liefert die HID-Eingaben; der Export verwendet den System-Dateidialog.

Die Ansicht zeigt alle Android-Eingabegeräte und deren Bewegungsachsen.
„Normal“ protokolliert die normale Ereigniszustellung mit sichtbarem Cursor.
„Capture“ fordert Pointer Capture für eine fokussierte View an. Nach erfolgreicher
Aktivierung verschwindet der Cursor und Android liefert relative X/Y-Bewegungen.
Die Statusanzeige unterscheidet Anforderung und tatsächlichen Capture-Zustand.
Touch bleibt für die Diagnosebedienung verfügbar.

Vor einer Gehäusetaste per Touch eine Markierung auswählen. Diese bleibt bis
zur nächsten Markierung aktiv. Sie behauptet **keine** automatisch erkannte
Richtung. „Loslassen“ ist eine optionale separate Testmarkierung; zum Prüfen der
Rückbewegung genügt es, die ursprüngliche Markierung aktiv zu lassen.

„Export ZIP“ beendet Capture und öffnet einen Speicherort. Das ZIP enthält
`events.jsonl` und `summary.txt`. Hintergrundschreiben verhindert Datei-I/O
in den Eingabe-Callbacks. Die Ansicht zeigt nur die letzten 14 Ereignisse;
die Datei enthält die gesamte Sitzung. Ein neues Activity-Exemplar beginnt
eine neue Sitzung. Vor Drehen, Beenden oder Neustarten exportieren.
Lokale Sitzungsdateien bleiben im privaten App-Verzeichnis bis zum Löschen
der App-Daten. Der Dateidialog kann Eingaben erzeugen; diese bleiben als solche
im Protokoll sichtbar.

Die Diagnose erfasst **Android-App-Ereignisse, keine Bluetooth-HID-Rohreports**.
Sie beobachtet MotionEvent, KeyEvent, Button-State, Action-Button, Quellen,
Geräte-IDs, Scroll-/relative Achsen und alle historischen Bewegungssamples.
Normale X/Y-Werte sind Positionen. Deren Differenzen darf die spätere Auswertung
nicht mit hardwareseitigen Rohdeltas gleichsetzen.
Die Eingabeoberfläche übernimmt keine Cursorpositionierung.

Bei extremer Ereignislast begrenzt die Schreibwarteschlange den Speicherbedarf.
Die Zusammenfassung nennt verlorene Datensätze und Schreibfehler ausdrücklich.
Ein solcher Export gilt nicht als vollständiger Trace. Für einen vergleichbaren
Hardwaretest muss der Export beide Werte als 0 melden.

## Nächster Meilenstein

Einmaliger Build mit Lint, danach [Pixel-8-Hardwaretest](docs/PIXEL8-TEST.md).
Erst nach Auswertung und Bestätigung implementieren wir die Input-Abstraktion
und anschließend den CameraX-Kern. Die Diagnose verwendet native Android Views;
die spätere Kameraoberfläche legt das noch nicht fest.

Geplante Bedienung: Links Macro/0,5×/1×, Hoch Zoom oder Macro-Crop, Runter
Foto/Video, Klick Auslöser beziehungsweise Video Start/Stop, Rechts EV-Zyklus.
RAW, Video-FPS, Weißabgleich und Kameraauswahl erfordern Capability-Prüfungen
am realen Gerät. Keine dieser Funktionen ist in Version 0.1.0 implementiert.

## Lizenz und Quellen

Apache 2.0, siehe LICENSE und NOTICE. Kein Quelltext der Google Jetpack Camera
App übernommen. Nur der offizielle Gradle Wrapper stammt aus Gradle.

- [Pointer Capture](https://developer.android.com/develop/ui/views/touch-and-input/gestures/movement#pointer-capture)
- [MotionEvent einschließlich historischer Achsenwerte](https://developer.android.com/reference/android/view/MotionEvent)
- [AGP-8.9-Kompatibilität](https://developer.android.com/build/releases/agp-8-9-0-release-notes)
- [Gradle Wrapper 8.11.1](https://github.com/gradle/gradle/tree/v8.11.1/gradle/wrapper)

Exporte enthalten Gerätebezeichnungen, Descriptor und Android-Buildinformationen.
Vor Veröffentlichung in diesem öffentlichen Repository prüfen.
