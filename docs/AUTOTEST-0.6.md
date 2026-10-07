# Automatischer Kameratest 0.6

Du brauchst nur eine bedruckte Seite und möglichst gleichbleibendes Licht.
Der Test funktioniert ohne SeaFrogs-Gehäuse oder Bluetooth-Maus.

1. App öffnen und auf Bereit warten. Handy abstützen, Linse reinigen.
2. Bedruckte Seite etwa 50 cm vor die Kameralinse legen. NORMALTEST drücken,
   Hinweise lesen und Test starten. Handy und Motiv bis zum Ende nicht bewegen.
3. Dieselbe Seite etwa 5 cm vor die Kameralinse legen. MACROTEST drücken und
   Test starten. Wenn bei 5 cm kein Nahfokus gelingt, denselben Macrotest später
   bei 10 cm wiederholen und den Abstand bei der Abgabe notieren.
4. Nach Ende TEST ZIP drücken, Speicherort auswählen und speichern.
5. Nur diese ZIP hier hochladen. Originalfotos musst du nicht separat suchen.

Im Querformat lässt sich die rechte Tastenleiste per Touch nach unten scrollen.
NORMALTEST, MACROTEST und TEST ZIP stehen dort. ABBRECHEN beendet den Ablauf.
Die App speichert fertige Fotos weiterhin in Pictures/SeaFrogs. Ein laufender
JPEG-Vorgang kann nach Abbruch noch fertig werden, startet aber keinen weiteren
Testschritt. Solche nachträglich fertigen Fotos stehen nicht zwingend im ZIP.

## Was die App prüft

Normaltest: Hauptkamera 1/1,5/3/5× und UW 1/1,5/2/3× sensorrelativ; je Kamera
EV 0/+1/+2/−1/−2 sowie verfügbare AUTO/HDR/NIGHT-Erweiterungen bei Zoom 1 und EV 0.
Macrotest: UW ohne Crop als Referenz, Macro mit UW-Cropfaktoren 1/2/4 sowie
verfügbare Extensions. Bei physisch gepinnten Kamerarouten überspringt die App
Extensions und dokumentiert die unsichere Sensorzuordnung. Das gilt derzeit
für UW/Macro auf dem getesteten Pixel 8. Der Test erzwingt keinen anderen Sensor.

Nach jeder Konfiguration wartet die App auf bestätigte Bereitschaft und weitere
3 Sekunden. Jeder unterstützte Schritt erzeugt ein Original-JPEG mit EXIF.
Die Bilder zeigen keinen automatischen Schärfewert; die spätere Auswertung
vergleicht die Originale, Fokusdiagnose, ISO und Belichtungsdaten. Der Test bewegt
weder Motiv noch Kamera und ersetzt keinen Vergleich mit der Pixel-Kamera.

Maximal 22 geplante Normal- und 7 Macro-Schritte. Nicht verfügbare Einstellungen
stehen als SKIPPED im Bericht. Initialisierungsfehler erhalten nach 30 Sekunden
FAILED; ein Aufnahmevorgang hat 90 Sekunden Zeit, danach endet der Durchlauf.
Während des Tests sperrt die App manuelle Kamerabefehle und HID-Kommandos.
Beim Verlassen der Kamera stoppt der automatische Ablauf. Nachtaufnahmen können
länger dauern; deshalb den aktuellen Durchlauf fertig abwarten.

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

Die App behält die Ergebnisse des letzten Normal- und letzten Macrotests über
App-Neustarts hinweg. Ein neuer Normaltest ersetzt im Bericht nur den vorherigen
Normaltest, ein neuer Macrotest nur den vorherigen Macrotest. Bestehende Fotos
löscht die App nicht. Das ZIP kann deshalb Resultate aus zwei Sitzungen enthalten;
runId ordnet sie zu. Das aktuelle events.jsonl enthält nur die aktuelle Sitzung.
Beim Wiederholen des Macrotests notieren, welchen Abstand der letzte Lauf hatte.

Bildqualität, Hardware-AF und Export auf dem Pixel 8 benötigen diesen Gerätetest.
RAW und Video sind weiterhin nicht implementiert. Die HID-Kombinationssperre
bleibt in dieser Version unverändert; die automatische Kameraaufnahme sperrt
HID-Eingaben unabhängig davon vollständig.
