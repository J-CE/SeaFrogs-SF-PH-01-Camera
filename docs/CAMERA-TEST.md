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

## Nächster gemeinsamer Meilenstein: Objektive, Zoom und EV

Der Objektivcode stammt aus 0.3.0-lenses und wurde mit 0.4.0-controls erfolgreich
gebaut. Der Gerätetest steht noch aus. Die folgenden Prüfungen werden nach Zoom/EV ergänzt und
zusammen ausgeführt:

1. KAMERA mehrfach drücken: 1× → Macro → 0,5× → 1×. Bei jedem Modus JPEG
   aufnehmen und den tatsächlichen Bildausschnitt von Vorschau und JPEG vergleichen.
2. Reale Sensorauswahl kontrollieren, etwa durch vorsichtiges Abdecken jeweils
   einer Linse im trockenen Test. Macro und 0,5× müssen dieselbe UW-Linse verwenden.
   Keine Aufnahme wird allein aufgrund eines Statuslabels als Macro bestätigt.
3. Nahes Motiv mittig platzieren, in Macro wechseln, Fokus-Neustart beobachten.
   Danach ohne weiteren Tastendruck zwischen zwei Nahmotiven wechseln: AF muss
   wieder kontinuierlich nachführen. Mindestabstand messen und dokumentieren.
4. Bei nicht bestätigtem Fokus muss die Meldung sichtbar sein. AF-Unterstützung
   in Metadaten ersetzt keine Prüfung der tatsächlichen Nahfokussierung.
5. Beim Wechsel und AF-Neustart dürfen keine Fotos ausgelöst werden; während
   Aufnahme darf keine Kamera gewechselt werden.
6. Aus Macro die Diagnose öffnen und zurückkehren; Macro muss wieder aktiv sein.
7. Fehler bei Kamera-Bind/Zoom prüfen: Rückkehr zum vorherigen Modus oder klare
   Fehlermeldung mit Möglichkeit zum Neustart. Nicht unterstützte Modi melden.
8. Bei physisch gepinnten Streams AF-Wirkung sowie gleiche Kamera in Vorschau
   und JPEG ausdrücklich prüfen. Logische CameraX-Kontrollen sind HAL-abhängig.

Protokollieren: Android-Build, gewählte logische/physische IDs (Debugger),
JPEG-Größen je Modus, Fokusabstände, Ausschnitt und Fehler. Noch keine Messwerte.

### Zoom-/EV-Abnahme für 0.4.0-controls

1. In 1× und 0,5× jeden Zoomschritt samt Rückkehr zur Basis prüfen. Bildfeld
   von Vorschau und JPEG vergleichen; nicht unterstützte Faktoren werden übersprungen.
2. Macro: voller UW-Ausschnitt → Faktor 2 → Faktor 4 → voller Ausschnitt.
   Auf echte UW-Sensorausgabe sowie AF-Nachführung achten. Nominale 0,5×/1×/2×
   müssen dem tatsächlichen Bildfeld entsprechen, bevor sie als bestätigt gelten.
3. EV-Zyklus vollständig durchlaufen. CameraX-Schrittweite, Indexbereich und
   erreichte Werte notieren; beispielsweise 0/+1/+2/−1/−2.
   Unter unveränderter Beleuchtung JPEG-Helligkeit vergleichen.
4. Kamera wechseln: voller Ausschnitt und übernommener EV-Wert. Grenzen anderer
   Kameras beachten. Nach Diagnose-Rückkehr müssen Zoom und EV wieder aktiv sein.
5. Schnell wechselnde Befehle und Auslösung während Zoom-/EV-Änderung prüfen:
   keine konkurrierenden Operationen, keine veralteten Statusrückmeldungen.
6. In beiden Querformaten prüfen, ob Status, Vorschau und große Tasten Platz haben.

Automatisch prüfbar: Zyklusende, unzulässige Zoomstufen, Macro-Faktoren,
EV-Rundung auf Drittelstufen und Entdoppelung bei eingeschränktem EV-Bereich.
Diese Unit-Tests ersetzen keinen Pixel-8-Kameratest.

### EXIF-Abnahme 0.4.1

Je ein Original-JPEG mit Hauptkamera 1×, UW ohne Crop und Macro in jeder
Crop-Stufe aufnehmen. Gerät, Motiv, Abstand und Beleuchtung konstant halten.
Zum Vergleich die originale Pixel-Kamera nutzen und vergleichbaren Bildausschnitt
notieren. Originale als Datei/ZIP exportieren; keine Screenshots. Vorhandene
EXIF und den App-JSON-UserComment prüfen. Speicher- oder EXIF-Fehler notieren.
Bildqualität, tatsächliche Kameraauswahl und Nahfokus bleiben Hardwareprüfungen.
