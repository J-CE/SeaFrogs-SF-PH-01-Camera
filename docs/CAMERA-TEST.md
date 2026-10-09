# Meilenstein 2: Hauptkamera und JPEG

Wir beschreiben hier unsere damaligen Kamera-Prüfschritte für 0.2 bis 0.4.1.

Wir bewahren diesen Ablauf als historischen Versionsstand. In RC1 sind die damaligen Test-/Exportbuttons teilweise entfernt; für die heutige Bedienung verwenden wir [DIVE-0.8.1.md](DIVE-0.8.1.md) und [RC1.md](RC1.md).

Version 0.2.0-photo. Hardwaretest auf Pixel 8 noch offen.

1. Wir installieren die APK und starten unsere App. Wir erlauben den Kamerazugriff.
   Unsere App muss rückseitige 1×-Vorschau und die ausgehandelte Fotogröße anzeigen.
2. Wir nehmen abwechselnd ein fernes und ein nahes Motiv ins Bild. Wir prüfen, ob
   kontinuierlicher AF selbstständig nachführt. Noch kein Macro-Test.
3. Wir nehmen zehn einzelne Fotos auf. Zusätzlich drücken wir mehrfach schnell FOTO.
   Während einer laufenden Aufnahme muss unsere App weitere Auslösungen sperren.
4. Wir öffnen Pictures/SeaFrogs in Google Fotos oder einer Dateiansicht.
   Wir prüfen die JPEG-Dateien auf Lesbarkeit, Auflösung, Fokus, Bildausschnitt und Vollständigkeit.
   Die Vorschau nutzt FIT_CENTER, damit sie den Bildausschnitt nicht beschneidet.
5. Wir nehmen Fotos im Hochformat, in beiden Querformaten und mit Android-Rotationssperre
   auf und prüfen die JPEG-Ausrichtung in einem EXIF-fähigen Betrachter.
6. Wir verlassen unsere App und kehren zurück, sperren und entsperren den Bildschirm und drehen das Smartphone.
   Kamera muss freigegeben werden und danach erneut starten.
7. Wir öffnen die HID-Diagnose und kehren zurück. Sie darf keine Kamera halten.
8. Wir verweigern die Kameraberechtigung und erlauben sie in Androids App-Einstellungen wieder. Es muss eine große Meldung erscheinen; Diagnose bleibt erreichbar.
9. Wir verwenden zwischendurch eine andere Kamera-App. Bei Kamerakonflikten muss ein
   verständlicher Fehler erscheinen und „Erneut starten“ einen neuen Versuch starten.
10. Wir beobachten das Display mindestens fünf Minuten bei aktiver Vorschau.
    Es darf durch den normalen Inaktivitätstimer nicht ausgehen.
11. Unter möglichst gleichen Bedingungen fotografieren wir ein Motiv mit der Pixel-Kamera und unserer
    App. Wir notieren Auflösung, Dateigröße und Aufnahmezeit.
    Dieser Vergleich bestätigt noch keine Unterwasser-Bildqualität.

Abnahme: zuverlässige JPEGs, korrekte Orientierung, AF-Nachführung,
Wiederaufnahme nach Lifecycle-Wechsel und klare Berechtigungs-/Aufnahmefehler.

Die Fotogröße folgt CameraX und der unterstützten Kombination mit Preview.
ResolutionSelector nimmt normale und High-Resolution-Ausgabegrößen auf,
aber keine zusätzlichen Ultra-High-Resolution-Sensormodi aus
SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION. 50 MP sind kein zugesichertes
Ergebnis. Wir prüfen die tatsächliche Größe im JPEG.

Der spätere HID-Adapter ruft PhotoCameraController.capturePhoto() auf.
Eine Gehäusetaste kann in dieser Version noch kein Foto auslösen.

## Nächster gemeinsamer Meilenstein: Objektive, Zoom und EV

Wir haben den Objektivcode aus 0.3.0-lenses mit 0.4.0-controls erfolgreich
gebaut. Der Gerätetest steht in diesem Versionsstand noch aus. Nach Zoom/EV ergänzen wir
die folgenden Prüfungen und führen sie gemeinsam aus:

1. Wir drücken KAMERA mehrfach: 1× → Macro → 0,5× → 1×. Bei jedem Modus nehmen wir ein JPEG
   auf und vergleichen den tatsächlichen Bildausschnitt von Vorschau und JPEG.
2. Wir kontrollieren die reale Sensorauswahl, etwa durch vorsichtiges Abdecken jeweils
   einer Linse im trockenen Test. Macro und 0,5× müssen dieselbe UW-Linse verwenden.
   Keine Aufnahme wird allein aufgrund eines Statuslabels als Macro bestätigt.
3. Wir platzieren ein nahes Motiv mittig, wechseln in Macro und beobachten den Fokus-Neustart.
   Danach wechseln wir ohne weiteren Tastendruck zwischen zwei Nahmotiven: AF muss
   wieder kontinuierlich nachführen. Wir messen und dokumentieren den Mindestabstand.
4. Bei nicht bestätigtem Fokus muss die Meldung sichtbar sein. AF-Unterstützung
   in Metadaten ersetzt keine Prüfung der tatsächlichen Nahfokussierung.
5. Beim Wechsel und AF-Neustart dürfen keine Fotos ausgelöst werden; während
   Aufnahme darf keine Kamera gewechselt werden.
6. Aus Macro öffnen wir die Diagnose und kehren zurück; Macro muss wieder aktiv sein.
7. Wir prüfen Fehler bei Kamera-Bind/Zoom: Rückkehr zum vorherigen Modus oder klare
   Fehlermeldung mit Möglichkeit zum Neustart. Wir halten nicht unterstützte Modi fest.
8. Bei physisch gepinnten Streams prüfen wir AF-Wirkung sowie gleiche Kamera in Vorschau
   und JPEG ausdrücklich. Logische CameraX-Kontrollen sind HAL-abhängig.

Wir protokollieren Android-Build, gewählte logische/physische IDs (Debugger),
JPEG-Größen je Modus, Fokusabstände, Ausschnitt und Fehler. Noch keine Messwerte.

### Zoom-/EV-Abnahme für 0.4.0-controls

1. In 1× und 0,5× prüfen wir jeden Zoomschritt samt Rückkehr zur Basis. Wir vergleichen das Bildfeld
   von Vorschau und JPEG; nicht unterstützte Faktoren werden übersprungen.
2. Macro: voller UW-Ausschnitt → Faktor 2 → Faktor 4 → voller Ausschnitt.
   Wir achten auf echte UW-Sensorausgabe sowie AF-Nachführung. Nominale 0,5×/1×/2×
   müssen dem tatsächlichen Bildfeld entsprechen, bevor sie als bestätigt gelten.
3. Wir durchlaufen den EV-Zyklus vollständig und notieren CameraX-Schrittweite, Indexbereich und
   erreichte Werte; beispielsweise 0/+1/+2/−1/−2.
   Unter unveränderter Beleuchtung vergleichen wir die JPEG-Helligkeit.
4. Wir wechseln die Kamera und prüfen den vollen Ausschnitt und den übernommenen EV-Wert. Wir beachten die Grenzen anderer
   Kameras. Nach Diagnose-Rückkehr müssen Zoom und EV wieder aktiv sein.
5. Wir prüfen schnell wechselnde Befehle und Auslösung während Zoom-/EV-Änderung:
   keine konkurrierenden Operationen, keine veralteten Statusrückmeldungen.
6. In beiden Querformaten prüfen wir, ob Status, Vorschau und große Tasten Platz haben.

Automatisch prüfbar: Zyklusende, unzulässige Zoomstufen, Macro-Faktoren,
EV-Rundung auf Drittelstufen und Entdoppelung bei eingeschränktem EV-Bereich.
Diese Unit-Tests ersetzen keinen Pixel-8-Kameratest.

### EXIF-Abnahme 0.4.1

Wir nehmen je ein Original-JPEG mit Hauptkamera 1×, UW ohne Crop und Macro in jeder
Crop-Stufe auf. Gerät, Motiv, Abstand und Beleuchtung halten wir konstant.
Zum Vergleich nutzen wir die originale Pixel-Kamera und notieren den vergleichbaren Bildausschnitt.
Wir exportieren die Originale als Datei/ZIP und prüfen vorhandene
EXIF und den App-JSON-UserComment. Wir notieren Speicher- oder EXIF-Fehler.
Bildqualität, tatsächliche Kameraauswahl und Nahfokus bleiben Hardwareprüfungen.
