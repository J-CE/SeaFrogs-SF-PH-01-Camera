# Meilenstein 2: Hauptkamera und JPEG

Version 0.2.0-photo. Hardwaretest auf Pixel 8 noch offen.

1. APK installieren und App starten. Kameraberechtigung erlauben.
   Die App muss rückseitige 1×-Vorschau und die ausgehandelte Fotogröße anzeigen.
2. Ein fernes und ein nahes Motiv abwechselnd ins Bild nehmen. Prüfen, ob
   kontinuierlicher AF selbstständig nachführt. Noch kein Macro-Test.
3. Zehn einzelne Fotos aufnehmen. Zusätzlich mehrfach schnell FOTO drücken.
   Während einer laufenden Aufnahme muss die App weitere Auslösungen sperren.
4. In Google Fotos oder einer Dateiansicht Pictures/SeaFrogs öffnen.
   JPEG-Dateien auf Lesbarkeit, Auflösung, Fokus, Bildausschnitt und Vollständigkeit prüfen.
   Die Vorschau nutzt FIT_CENTER, damit sie den Bildausschnitt nicht beschneidet.
5. Fotos im Hochformat, in beiden Querformaten und mit Android-Rotationssperre
   aufnehmen. JPEG-Ausrichtung in einem EXIF-fähigen Betrachter prüfen.
6. App verlassen und zurückkehren, Bildschirm sperren/entsperren, App drehen.
   Kamera muss freigegeben werden und danach erneut starten.
7. HID-Diagnose öffnen und zurückkehren. Sie darf keine Kamera halten.
8. Kameraberechtigung verweigern und in Androids App-Einstellungen wieder
   erlauben. Es muss eine große Meldung erscheinen; Diagnose bleibt erreichbar.
9. Andere Kamera-App dazwischen verwenden. Bei Kamerakonflikten muss ein
   verständlicher Fehler erscheinen und „Erneut starten“ einen neuen Versuch starten.
10. Display mindestens fünf Minuten bei aktiver Vorschau beobachten.
    Es darf durch den normalen Inaktivitätstimer nicht ausgehen.
11. Unter möglichst gleichen Bedingungen ein Motiv mit Pixel-Kamera und dieser
    App fotografieren. Auflösung, Dateigröße und Aufnahmezeit notieren.
    Dieser Vergleich bestätigt noch keine Unterwasser-Bildqualität.

Abnahme: zuverlässige JPEGs, korrekte Orientierung, AF-Nachführung,
Wiederaufnahme nach Lifecycle-Wechsel und klare Berechtigungs-/Aufnahmefehler.

Die Fotogröße folgt CameraX und der unterstützten Kombination mit Preview.
ResolutionSelector nimmt normale und High-Resolution-Ausgabegrößen auf,
aber keine zusätzlichen Ultra-High-Resolution-Sensormodi aus
SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION. 50 MP sind kein zugesichertes
Ergebnis. Die tatsächliche Größe im JPEG prüfen.

Der spätere HID-Adapter ruft PhotoCameraController.capturePhoto() auf.
Eine Gehäusetaste kann in dieser Version noch kein Foto auslösen.
