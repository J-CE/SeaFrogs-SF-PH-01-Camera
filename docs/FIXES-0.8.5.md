# Korrektur 0.8.5

Wir korrigieren in diesem Meilenstein die Vorschaurotation und den Umgang mit Grenz-Warnungen nach gespeicherten Fotos.

## Bestätigte Fehler in 0.8.4

In der neuen Camera2-Vorschau verwendeten wir JPEG-Sensorrotation für eine TextureView. Android korrigiert die Sensororientierung dieser Oberfläche bereits. Der zusätzliche Drehschritt verursachte die gemeldete 90-Grad-Rechtsdrehung. Mit der neuen Transformation korrigieren wir ausschließlich Displayrotation und Skalierung. Wir passen das vollständige Bild ein, ohne Crop oder Streckung; JPEG/DNG-Ausrichtung bleibt unabhängig.

Quelle: Android Developers, Camera preview und Support resizable surfaces in your camera app:
https://developer.android.com/media/camera/camera2/camera-preview
https://developer.android.com/codelabs/android-camera2-preview

Eine fehlende ISO-/Zeitbestätigung nach geschriebenen JPEG/DNG-Dateien lief in den fatalen Sitzungsabschluss. Dadurch verschwand die Vorschau und unsere App verlangte einen Neustart. In der dauerhaften Fotositzung trennen wir jetzt eine gespeicherte Aufnahme mit Warnung von Kamera-/Dateifehlern. Eine Grenz-Warnung lässt Vorschau und nächste Aufnahme aktiv. Echte Kamera-/Timeout-/Dateifehler behalten ihren Fehlerpfad. Automatisierte Vergleichsdiagnosen behandeln unbestätigte Vergleichsparameter weiterhin als fehlgeschlagen.

## Was der Screenshot nicht belegt

Er enthält keine tatsächlichen Sensor-ISO-, Sensorzeit- oder AE-Moduswerte. Er klärt deshalb nicht, ob die Hardware ISO/Zeit überschritt, ob ein gemeldeter AE-Modus abwich oder ob Metadaten fehlten. Wir halten keine dieser Ursachen für erwiesen.

Mit der neuen Grenzprüfung benennen wir den Grund und zeigen Sensor-ISO sowie Zeit in Millisekunden. `aeModeActual` und `limitVerificationIssues` ergänzen die Metadaten. Die Grenzen bleiben streng: ISO 401 überschreitet ISO 400, und auch 1 ns über der Zeitgrenze bleibt unbestätigt. Keine künstliche Rundungstoleranz, keine heimliche Lockerung, kein Erfolgsetikett für nicht belegte Grenzen. Warnungen bleiben in Gelb sichtbar, bis eine neue Aktion den Status ersetzt.

## Softwareprüfung

Mit neuen JVM-Regressionstests prüfen wir alle vier Displayrotationswerte mit verschiedenen Sensororientierungen, vollständige unverzerrte 4:3-Geometrie und Letterboxing. Zusätzliche Grenztests prüfen fehlende Metadaten, exakte Grenzen, ISO-/Zeitüberschreitungen und getrennte JPEG-Verstärkung. Die Tests ersetzen keine Sichtprüfung auf dem Pixel 8.
