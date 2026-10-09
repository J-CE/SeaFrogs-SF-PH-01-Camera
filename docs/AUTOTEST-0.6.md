# Automatischer Kameratest 0.6

Wir prüfen in diesem historischen Meilenstein den automatischen Normal- und Macrotest.

Wir bewahren diesen Ablauf als historischen Versionsstand. In RC1 sind die damaligen Test-/Exportbuttons teilweise entfernt; für die heutige Bedienung verwenden wir [DIVE-0.8.1.md](DIVE-0.8.1.md) und [RC1.md](RC1.md).

Wir benötigen nur eine bedruckte Seite und möglichst gleichbleibendes Licht.
Der Test funktioniert ohne SeaFrogs-Gehäuse oder Bluetooth-Maus.

1. Wir öffnen unsere App und warten auf Bereit. Wir stützen das Handy ab und reinigen die Linse.
2. Wir legen eine bedruckte Seite etwa 50 cm vor die Kameralinse, drücken NORMALTEST,
   lesen die Hinweise und starten den Test. Handy und Motiv bewegen wir bis zum Ende nicht.
3. Wir legen dieselbe Seite etwa 5 cm vor die Kameralinse, drücken MACROTEST und
   starten den Test. Wenn bei 5 cm kein Nahfokus gelingt, wiederholen wir denselben Macrotest
   später bei 10 cm und notieren den Abstand bei der Abgabe.
4. Nach Ende drücken wir TEST ZIP, wählen den Speicherort aus und speichern die Datei.
5. Wir laden nur diese ZIP zur Auswertung hoch. Die Originalfotos müssen wir nicht separat suchen.

Im Querformat lässt sich die rechte Tastenleiste per Touch nach unten scrollen.
NORMALTEST, MACROTEST und TEST ZIP stehen dort. ABBRECHEN beendet den Ablauf.
Unsere App speichert fertige Fotos weiterhin in Pictures/SeaFrogs. Ein laufender
JPEG-Vorgang kann nach Abbruch noch fertig werden, startet aber keinen weiteren
Testschritt. Solche nachträglich fertigen Fotos stehen nicht zwingend im ZIP.

## Was unsere App prüft

Normaltest: Hauptkamera 1/1,5/3/5× und UW 1/1,5/2/3× sensorrelativ; je Kamera
EV 0/+1/+2/−1/−2 sowie verfügbare AUTO/HDR/NIGHT-Erweiterungen bei Zoom 1 und EV 0.
Macrotest: UW ohne Crop als Referenz, Macro mit UW-Cropfaktoren 1/2/4 sowie
verfügbare Extensions. Bei physisch gepinnten Kamerarouten überspringt unsere App
Extensions und dokumentiert die unsichere Sensorzuordnung. Das gilt derzeit
für UW/Macro auf dem getesteten Pixel 8. Der Test erzwingt keinen anderen Sensor.

Nach jeder Konfiguration wartet unsere App auf bestätigte Bereitschaft und weitere
3 Sekunden. Jeder unterstützte Schritt erzeugt ein Original-JPEG mit EXIF.
Die Bilder zeigen keinen automatischen Schärfewert; die spätere Auswertung
vergleicht die Originale, Fokusdiagnose, ISO und Belichtungsdaten. Der Test bewegt
weder Motiv noch Kamera und ersetzt keinen Vergleich mit der Pixel-Kamera.

Maximal 22 geplante Normal- und 7 Macro-Schritte. Nicht verfügbare Einstellungen
stehen als SKIPPED im Bericht. Initialisierungsfehler erhalten nach 30 Sekunden
FAILED; ein Aufnahmevorgang hat 90 Sekunden Zeit, danach endet der Durchlauf.
Während des Tests sperrt unsere App manuelle Kamerabefehle und HID-Kommandos.
Beim Verlassen der Kamera stoppt der automatische Ablauf. Nachtaufnahmen können
länger dauern; deshalb warten wir den aktuellen Durchlauf vollständig ab.

## ZIP-Inhalt

- photos/: unveränderte Original-JPEGs, benannt nach Test-ID.
- camera-test-report.json: gewünschte Einstellungen und Status jedes Schritts.
- events.jsonl: Kamera-/Eingabeprotokoll der aktuellen App-Sitzung.
- summary.txt: Protokollzählung, Verluste und Schreibfehler.
- export-errors.json: fehlende oder nicht lesbare Originalfotos.

Tatsächlich angewendete EV-/Zoomwerte und die letzte Vorschau-Telemetrie stehen
im JPEG-EXIF, nicht als behauptete Aufnahme-Messwerte im Bericht. Extensions
stellen die Interop-Telemetrie in dieser Version nicht bereit. PHYS bezeichnet
passende physische Resultatwerte; LOG allein bestätigt keinen physischen Macro-AF.

Unsere App behält die Ergebnisse des letzten Normal- und letzten Macrotests über
App-Neustarts hinweg. Ein neuer Normaltest ersetzt im Bericht nur den vorherigen
Normaltest, ein neuer Macrotest nur den vorherigen Macrotest. Bestehende Fotos
löscht unsere App nicht. Das ZIP kann deshalb Resultate aus zwei Sitzungen enthalten;
runId ordnet sie zu. Das aktuelle events.jsonl enthält nur die aktuelle Sitzung.
Beim Wiederholen des Macrotests notieren wir den Abstand des letzten Laufs.

Bildqualität, Hardware-AF und Export auf dem Pixel 8 benötigen diesen Gerätetest.
RAW und Video sind weiterhin nicht implementiert. Die HID-Kombinationssperre
bleibt in dieser Version unverändert; die automatische Kameraaufnahme sperrt
HID-Eingaben unabhängig davon vollständig.
