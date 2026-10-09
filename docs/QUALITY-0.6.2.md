# Auflösung und AF-Test 0.6.2

Wir verbessern in diesem Meilenstein die gemeinsame Auflösungswahl und die Fokusprüfung unserer automatischen Tests.

Wir bewahren diesen Ablauf als historischen Versionsstand. In RC1 sind die damaligen Test-/Exportbuttons teilweise entfernt; für die heutige Bedienung verwenden wir [DIVE-0.8.1.md](DIVE-0.8.1.md) und [RC1.md](RC1.md).

## Änderungen

Für physisch gepinnte UW-/Macro-Ausgaben verwenden wir nur Größen, die logische
Elternkamera und gewählter physischer Sensor gemeinsam anbieten. JPEG beschränkt
sich auf annähernd 4:3 und bevorzugt die größte gemeinsam angebotene Größe.
Die Vorschau verwendet ebenfalls die gemeinsame Größenliste, ohne eine feste
Vorschaugröße zu erzwingen. Fehlende Schnittmengen melden einen Bind-Fehler.
Keine Kamera-ID und keine feste Pixel-Größe stehen im Code.

Auf dem Pixel-8-Bericht aus 0.6.1 ist der größte passende JPEG-Kandidat
4000×3000. Ob der HAL diese Größe tatsächlich ausgibt, bestätigt erst die
neue JPEG-Datei. Der Event `physicalStreamResolutionCandidates` protokolliert
die Kandidaten. Die Hauptkamera behält ihre normale Auflösungsverhandlung;
Extensions behalten ihre eigene Ausgabegrößenverhandlung.

In unserem automatischen Test warten wir weiterhin drei Sekunden nach Bereitschaft.
STANDARD verlangt anschließend mindestens drei unterschiedliche fokussierte
Vorschau-Frames über mindestens 400 ms. Der letzte Frame darf höchstens 500 ms
alt sein, eine Unterbrechung über 500 ms setzt die Bestätigung zurück.
PASSIVE_FOCUSED und FOCUSED_LOCKED zählen; Suchzustände, fehlende Metadaten und
unfokussierte Zustände setzen die Bestätigung zurück. Bei gepinntem Sensor
zählt ausschließlich dessen physischer AF-Result, nicht der logische AF.

Nach weiteren acht Sekunden ohne bestätigten Fokus protokolliert der Test
FAILED und nimmt kein Foto auf. Im Ergebnisbericht steht `preCaptureFocus`.
NIGHT/andere Extensions melden UNVERIFIED_EXTENSION, weil deren Session keine
Interop-AF-Telemetrie liefert. Ihre Aufnahme folgt der bisherigen Wartezeit.
Eine Vorschau-AF-Bestätigung ist kein objektiver Schärfetest und kein Nachweis
des AF-Zustands während der späteren JPEG-Belichtung.

Der Macro-Einstieg gibt dem Fokusversuch fünf statt zwei Sekunden bis zur
automatischen Rücksetzung. Nach Abschluss kehrt die Steuerung zum
kontinuierlichen AF zurück. Der manuelle Auslöser wartet weiterhin nicht auf
die neue Test-Fokusprüfung; die Änderung gilt für automatische Testaufnahmen.

## Gerätetest

Wir installieren die APK als Update. Wir starten NORMALTEST mit bedrucktem Motiv bei etwa 50 cm,
danach MACROTEST mit Motiv bei etwa 5 cm. Wir stützen das Handy ab und verwenden
gutes, konstantes Licht. Unsere App übernimmt alle Einstellungen und Aufnahmen.
Anschließend exportieren wir TEST ZIP und laden sie zur Auswertung hoch.

Zur Abnahme vergleichen wir tatsächliche UW-/Macro-JPEG-Dimensionen, Fokusstatus vor jeder Aufnahme,
AF-Timeouts sowie sichtbare Detailzeichnung bei den Crop-Stufen.
RAW/DNG, ISO-Limit, Video und weitere HID-Änderungen gehören nicht zu 0.6.2.
