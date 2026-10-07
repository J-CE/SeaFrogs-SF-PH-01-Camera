# SeaFrogs SF-PH-01 Camera

Android-Kamera-Projekt für Google Pixel 8 und SeaFrogs SF-PH-01 Pro.
Aktueller Quelltext: **0.3.0-lenses**. Live-Vorschau, JPEG-Aufnahme und
Kamerawechsel 1× → Macro → 0,5× sowie separate HID-Diagnose.
Der letzte erfolgreiche Build betrifft 0.2.0-photo; 0.3.0 ist noch nicht gebaut. SeaFrogs-Tastenbelegung und
Pixel-8-Hardwaretest sind noch nicht bestätigt.

## Öffnen und bauen

Repository in Android Studio öffnen, JDK 17 für Gradle wählen, Android SDK 35
installieren und Gradle synchronisieren. Fest gepinnt: AGP 8.9.2, Kotlin 2.1.20,
Gradle 8.11.1. Kamera: CameraX 1.4.2 und AndroidX Activity 1.10.1. Mindestversion Android 8.0 (API 26).

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

Linux/macOS: `./gradlew :app:assembleDebug :app:lintDebug`.
APK: `app/build/outputs/apk/debug/app-debug.apk`.
Android Studio kann die App per USB-Debugging direkt auf dem Pixel installieren.

## Hauptkamera und JPEG

Die App startet in der Kameraansicht. Kameraberechtigung erlauben und auf die
Live-Vorschau warten. FOTO löst eine JPEG-Aufnahme aus; während der Aufnahme
bleiben Auslöser, Neustart und Diagnosewechsel gesperrt. Aufnahmen landen unter
Pictures/SeaFrogs und erscheinen über MediaStore in der Galerie. Auf Android 10
und neuer braucht das keine allgemeine Speicherfreigabe; Android 8/9 benötigt
die auf diese Versionen beschränkte Legacy-Speicherberechtigung.

Der separate PhotoCameraController besitzt Vorschau und Aufnahme-Use-Case,
setzt die rückseitige logische Kamera auf 1× und nutzt CameraXs kontinuierlichen
Foto-Autofokus. Er verhandelt die höchste unterstützte 4:3-JPEG-Auflösung
einschließlich High-Resolution-Ausgabegrößen zusammen mit der Vorschau. Die
Statusanzeige zeigt die tatsächliche Größe. Zusätzliche Ultra-High-Resolution-
Sensormodi und die proprietäre Pixel-Bildverarbeitung gehören nicht dazu.

Die Vorschau zeigt den vollständigen Ausschnitt mit FIT_CENTER. JPEG-Orientierung
folgt der Geräteorientierung. Das App-Fenster bleibt während der Kameranutzung
wach. Beim Verlassen gibt die App ihre Kamera-Use-Cases frei und bindet sie
bei Rückkehr erneut. Fehlende Berechtigung und Kamera-/Aufnahmefehler erscheinen
im Statusbereich. „Erneut starten“ initialisiert die Kamera neu.

[Erster Kameratest](docs/CAMERA-TEST.md): Vorschau, AF, JPEGs, Orientierung,
Berechtigungen und Freigabe nach Lifecycle-Wechsel auf dem Pixel 8 prüfen.
Mausadapter, Video, Zoom-/EV-Zyklen, RAW und
Weißabgleichprofile folgen nach separater Abstimmung.

## Kamerawechsel und Macro

Die große KAMERA-Taste schaltet 1× → Macro → 0,5× → 1×.
Während Aufnahme, Initialisierung und Macro-Fokusstart ist sie gesperrt.
Macro verwendet eine zur Laufzeit ermittelte Ultraweitwinkelkamera mit
kontinuierlichem Foto-AF und stößt mittigen Autofokus neu an. Anschließend
wird die kurzzeitige Fokussperre wieder aufgehoben. Beide Ultraweitwinkelmodi
verwenden momentan das volle Sensorfeld, ohne digitalen Crop.

Fehlende Kameras werden gemeldet; der nächste Tastendruck fährt im Zyklus fort.
Bei einem fehlgeschlagenen Bind versucht die App, die vorherige Kamera erneut
zu öffnen. Rückkehr aus der Diagnose bewahrt den gewählten Modus im selben
Activity-Exemplar; eine neue Activity startet mit 1×.

[Kameraerkennung und Grenzen](docs/LENS-MODES.md) sowie
[Geräteprüfung](docs/CAMERA-TEST.md) beschreiben die noch offene Abnahme.

## Diagnose

Das Gehäuse zuerst in Androids Bluetooth-Einstellungen koppeln. Die Diagnose
braucht keine Bluetooth-, Kamera-, Mikrofon- oder Speicherberechtigung. Android
liefert die HID-Eingaben; der Export verwendet den System-Dateidialog. Die
Kameraansicht fordert ihre eigenen Berechtigungen an.

Die Kameraansicht öffnet sie über „HID-Diagnose“. Die Ansicht zeigt alle Android-Eingabegeräte und deren Bewegungsachsen.
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

Nach Ergänzung von Zoom und EV: Build mit Lint, danach [Kameratest](docs/CAMERA-TEST.md) und getrennt
[HID-Hardwaretest](docs/PIXEL8-TEST.md). Die Kamera funktioniert unabhängig von
der späteren Input-Abstraktion. Beide Ansichten verwenden native Android Views.
Build-Ergebnisse stehen in [BUILD-STATUS.md](docs/BUILD-STATUS.md).

Geplante Bedienung: Links Macro/0,5×/1×, Hoch Zoom oder Macro-Crop, Runter
Foto/Video, Klick Auslöser beziehungsweise Video Start/Stop, Rechts EV-Zyklus.
RAW, Video-FPS, Weißabgleich und Kameraauswahl erfordern Capability-Prüfungen
am realen Gerät. Diese erweiterten Funktionen sind in Version 0.3.0 noch nicht implementiert.

## Lizenz und Quellen

Apache 2.0, siehe LICENSE und NOTICE. Kein Quelltext der Google Jetpack Camera
App übernommen. Nur der offizielle Gradle Wrapper stammt aus Gradle.

- [Pointer Capture](https://developer.android.com/develop/ui/views/touch-and-input/gestures/movement#pointer-capture)
- [MotionEvent einschließlich historischer Achsenwerte](https://developer.android.com/reference/android/view/MotionEvent)
- [AGP-8.9-Kompatibilität](https://developer.android.com/build/releases/agp-8-9-0-release-notes)
- [Gradle Wrapper 8.11.1](https://github.com/gradle/gradle/tree/v8.11.1/gradle/wrapper)

Exporte enthalten Gerätebezeichnungen, Descriptor und Android-Buildinformationen.
Vor Veröffentlichung in diesem öffentlichen Repository prüfen.
