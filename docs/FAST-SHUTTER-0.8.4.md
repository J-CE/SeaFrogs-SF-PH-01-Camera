# Dauerhafte Fotositzung ab 0.8.4

## Problem und Änderung

Der bisherige normale RAW+JPEG-/ISO-Grenzen-Weg schloss CameraX, öffnete eine neue Camera2-Sitzung, wartete mindestens 1.500 ms auf AF/AE und startete nach dem Speichern die Vorschau neu. Diese Vorbereitung lief nach dem Klick.

Jetzt eröffnet der normale Fotomodus (`FREI`) eine dauerhafte Camera2-Sitzung, sobald RAW+JPEG oder eine ISO-/Digitalgrenze gewählt ist. Sie enthält Vorschau, JPEG und optional RAW. Die Wiederholungsanforderung belichtet und fokussiert kontinuierlich. Der Klick sendet direkt eine Still-Anforderung, verwendet den neuesten Sensormesswert für die Grenzen und lässt die Vorschau weiterlaufen. Erfolgreiches Speichern schließt weder Kamera noch Sitzung. Es gibt keine feste AF-/AE-Wartezeit nach dem Klick.

JPEG ohne Grenzen bleibt bei CameraX. Video, Hersteller-NIGHT und automatisierte Vergleichsdiagnosen behalten ihre eigenen Wege. NIGHT kann wegen Mehrbildverarbeitung weiterhin verzögert reagieren.

## Zuverlässigkeit

- Zoom/EV bleiben gesperrt, bis ein Ergebnis der neuen Vorschauanforderung vorliegt. Auslösen während einer laufenden Aufnahme oder eines Einstellungswechsels ist gesperrt.
- JPEG und RAW werden anhand desselben Sensorzeitstempels zugeordnet. Bestehende Sensor-/Grenzen-/WB-Prüfungen bleiben aktiv.
- Ein Kamera-/Aufnahmefehler oder Timeout schließt die Sitzung; verspätete Ergebnisse werden damit nicht einer folgenden Aufnahme zugeordnet. Sichtbarer Fehler fordert einen Neustart an.
- Startup-Timeout 20 s, Kontrollbestätigung 5 s, Aufnahmeabschluss 20 s sind Fehlergrenzen und keine normalen Auslösewartezeiten.
- Lifecycle, Kamera-/Modus-/Setup-Wechsel schließen die Sitzung. Die Oberfläche ignoriert Rückmeldungen alter Sitzungen über ihre Generation.
- Frische Sitzungen starten kontinuierlichen Foto-AF. Ein dauerhaft verriegelnder AF-START wird vermieden. Bei noch laufendem AF oder veränderter Beleuchtung kann die sofortige Aufnahme unscharf oder noch nicht korrekt belichtet sein; die App wartet bewusst nicht auf eine erneute Bestätigung beim Klick.
- JPEG/DNG und EXIF werden nach der Belichtung geschrieben. Bis das Speichern endet bleibt der nächste Klick gesperrt. Serienaufnahmen sind nicht Bestandteil dieser Änderung.

## Messdaten ohne zusätzlichen Diagnoseablauf

Das normale Foto protokolliert `persistentSession`, `fixedShutterDelayMs=0`, `shutterRequestUptimeMs`, `workerQueueDelayMs`, `captureSubmittedUptimeMs`, `captureStartedCallbackUptimeMs`, `clickToStartedCallbackMs`, `captureStartedSensorTimestampNs` und `savedUptimeMs`. Callback-Uptime und Sensorzeitstempel verwenden unterschiedliche Zeitbasen und dürfen nicht direkt subtrahiert werden. `clickToStartedCallbackMs` ist die beobachtete Callback-Verzögerung ab dem Software-Auslöseaufruf; sie ist keine exakt gemessene mechanische Tastendruck-zu-Belichtung-Zeit. EXIF-Abschluss folgt dem angegebenen JPEG/DNG-Speicherabschluss.

## Prüfstand und offene Geräteabnahme

Build, JVM-Tests und Android Lint prüfen Softwareintegration; sie ersetzen kein Pixel 8. Vorschau-Ausrichtung, simultane Vorschau/JPEG/RAW-Streamkombination auf beiden Sensoren, fortlaufender AF sowie reale Auslöse-/Speicherzeiten sind auf dem Gerät noch nicht bestätigt. Keine garantierte Millisekundenangabe für die Hardware.
