# Korrektur 0.8.5

## Bestätigte Fehler in 0.8.4

Die neue Camera2-Vorschau verwendete JPEG-Sensorrotation für eine TextureView. Android korrigiert die Sensororientierung dieser Oberfläche bereits. Der zusätzliche Drehschritt verursachte die gemeldete 90-Grad-Rechtsdrehung. Die neue Transformation korrigiert ausschließlich Displayrotation und Skalierung. Sie passt das vollständige Bild ein, ohne Crop oder Streckung; JPEG/DNG-Ausrichtung bleibt unabhängig.

Quelle: Android Developers, Camera preview und Support resizable surfaces in your camera app:
https://developer.android.com/media/camera/camera2/camera-preview
https://developer.android.com/codelabs/android-camera2-preview

Eine fehlende ISO-/Zeitbestätigung nach geschriebenen JPEG/DNG-Dateien lief in den fatalen Sitzungsabschluss. Dadurch verschwand die Vorschau und die App verlangte einen Neustart. Die dauerhafte Fotositzung trennt jetzt eine gespeicherte Aufnahme mit Warnung von Kamera-/Dateifehlern. Eine Grenz-Warnung lässt Vorschau und nächste Aufnahme aktiv. Echte Kamera-/Timeout-/Dateifehler behalten ihren Fehlerpfad. Automatisierte Vergleichsdiagnosen behandeln unbestätigte Vergleichsparameter weiterhin als fehlgeschlagen.

## Was der Screenshot nicht belegt

Er enthält keine tatsächlichen Sensor-ISO-, Sensorzeit- oder AE-Moduswerte. Er klärt deshalb nicht, ob die Hardware ISO/Zeit überschritt, ob ein gemeldeter AE-Modus abwich oder ob Metadaten fehlten. Keine dieser Ursachen wird als erwiesen behauptet.

Die neue Grenzprüfung benennt den Grund und zeigt Sensor-ISO sowie Zeit in Millisekunden. `aeModeActual` und `limitVerificationIssues` ergänzen die Metadaten. Die Grenzen bleiben streng: ISO 401 überschreitet ISO 400, und auch 1 ns über der Zeitgrenze bleibt unbestätigt. Keine künstliche Rundungstoleranz, keine heimliche Lockerung, kein Erfolgsetikett für nicht belegte Grenzen. Warnungen bleiben in Gelb sichtbar, bis eine neue Aktion den Status ersetzt.

## Softwareprüfung

Neue JVM-Regressionstests prüfen alle vier Displayrotationswerte mit verschiedenen Sensororientierungen, vollständige unverzerrte 4:3-Geometrie und Letterboxing. Zusätzliche Grenztests prüfen fehlende Metadaten, exakte Grenzen, ISO-/Zeitüberschreitungen und getrennte JPEG-Verstärkung. Die Tests ersetzen keine Sichtprüfung auf dem Pixel 8.
