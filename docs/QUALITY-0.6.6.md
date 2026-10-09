# Kontrollierter Bildverarbeitungstest 0.6.6

Wir vergleichen in diesem Meilenstein die angebotenen JPEG-Verarbeitungsmodi unter kontrollierten Aufnahmebedingungen.

Wir bewahren diesen Ablauf als historischen Versionsstand. In RC1 sind die damaligen Test-/Exportbuttons teilweise entfernt; für die heutige Bedienung verwenden wir [DIVE-0.8.1.md](DIVE-0.8.1.md) und [RC1.md](RC1.md).

## Gerätetest

1. Wir installieren die APK als Update. Wir legen das Smartphone fest auf oder stützen es ab und richten die Kamera
   ungefähr 50 cm vor ein bedrucktes Motiv mit feinen Details.
   Wir halten die Beleuchtung konstant. Der Test setzt ISO-Grenzen selbst aus.
2. Wir starten QUALITÄTSTEST. Während des gesamten Ablaufs bewegen wir nichts und
   drücken keine Tasten. Unsere App nimmt bis zu sieben JPEGs auf:

| Kamera | Variante | Steuerung |
| --- | --- | --- |
| Hauptkamera | DEFAULT | Still-Template-Verarbeitung, eingefrorene Szenenwerte |
| Hauptkamera | NR_HIGH_QUALITY | HQ-Entrauschen, Schärfungsmodus aus Referenz |
| Hauptkamera | NR_EDGE_HIGH_QUALITY | HQ-Entrauschen und HQ-Schärfung |
| UW | DEFAULT | Still-Template-Verarbeitung, eigene eingefrorene Szenenwerte |
| UW | NR_HIGH_QUALITY | HQ-Entrauschen, Schärfungsmodus aus UW-Referenz |
| UW | NR_EDGE_HIGH_QUALITY | HQ-Entrauschen und HQ-Schärfung |
| Hauptkamera | NIGHT_REFERENCE | unabhängige OEM-Extension, sofern verfügbar |

3. Wir warten bis QUALITY fertig, exportieren QUALITÄT ZIP und laden sie erst nach der
   Meldung Export gespeichert hoch. Das Paket enthält nur die neue
   QUALITY-Gruppe, Protokoll, Ergebnis- und Kamera-Fähigkeitsbericht.

Die neuen Tasten liegen im scrollbar gestalteten Steuerbereich. NORMALTEST
zeigt während jeder automatischen Serie ABBRECHEN. Der Test überschreibt
keine gespeicherten Format-/ISO-Einstellungen. Nach Ende/Abbruch gelten
wieder die bisherigen Werte; es entsteht noch kein neuer Produktionsmodus.

## Vergleichskontrolle

Je Kamera misst die DEFAULT-Aufnahme bei stabilem Fokus und bestätigtem
AE/AWB frische Sensor-ISO, Zeit, Framedauer, Post-RAW-Gain, Fokusdistanz,
WB-Gains und Farbmatrix. Die manuelle Vergleichsphase deaktiviert AE/AWB/AF
und übernimmt diese Werte, wartet auf einen stationären passenden Fokus und
speichert dann das erste JPEG. Die beiden folgenden Varianten übernehmen
die tatsächlich gemeldeten Werte dieses erfolgreichen Referenzfotos.

Jeder native Vergleich pausiert die CameraX-Vorschau, verwendet aber denselben
JPEG-Aufnahmeweg und die gleiche Auflösung/Zoom/EV je Sensor. Unsere App prüft
die Werte am Sensor-CaptureResult und die JPEG-/Result-Zeitstempel. Pinned
UW-Ausgaben benötigen passende physische Ergebnisse; keine logische Ersatz-
Telemetrie und kein stiller Wechsel zur Hauptkamera. Fehlende Referenz oder
nicht angebotene Einstellungen führen zu SKIPPED. Abweichende angewendete
Einstellungen führen zu FAILED; gespeicherte Bilder bleiben zur Diagnose im ZIP.

Prüftoleranzen: identische Sensor-ISO/Post-RAW-Gain, Zeit 0.1 Prozent oder
20 Mikrosekunden, Fokus 0.02 Dioptrien oder 0.1 Prozent, WB-Gains/Matrix
0.5 Prozent oder absolut 0.005. Zusätzlich AE/AWB/AF OFF und keine gemeldete
Linsenbewegung. AF 0 während einer fixierten Aufnahme ist hier erwartbar;
der vorgeschaltete AF bestätigt die Referenz, danach prüft unsere App die
manuelle Fokusposition. Die Kamera-Fähigkeitsabfrage enthält jetzt edgeModes.

sensorCapture dokumentiert comparisonRequestedSettings, comparisonActualSettings,
exposureFrozenVerified, whiteBalanceFrozenVerified, focusFrozenVerified,
processingVerified, comparisonVerified und die tatsächlichen NR-/Edge-Modi.
NR/Edge 0 = OFF, 1 = FAST, 2 = HIGH_QUALITY. DEFAULT ist die unveränderte
Still-Template-Wahl für NR/Edge; die Szenensteuerung ist bereits fixiert.
Unsere App meldet damit auch, wenn DEFAULT ohnehin HQ verwendet und eine
ausdrückliche HQ-Anforderung keine neue Verarbeitung aktiviert.

NIGHT ist eine separate Referenz: Die Extension besitzt eigene Automatik,
Mehrbild-/Verarbeitungsentscheidungen und Auflösungswahl. Sie übernimmt nicht
die fixierten Szenenwerte. Der Bericht kennzeichnet sie als
INDEPENDENT_EXTENSION_REFERENCE; unsere App injiziert keine manuellen
Camera2-Parameter in die Extension. Ihr AF-Status bleibt UNVERIFIED_EXTENSION.

Keine automatische Rausch-/Schärfebewertung, kein Versprechen einer Pixel-
Camera/HDR+-Pipeline. Auch fixierte Kameraeinstellungen verhindern keine
Verschiebung des Geräts, wechselndes Licht oder Motivbewegung.

## Vorherige Geräteabnahme 0.6.5

seafrogs-iso-1791405271326.zip: sechs vollständige JPEGs, alle SAVED, keine
CRC-/Export-/Schreibfehler und keine verlorenen Ereignisse. Hauptkamera
4080 × 3072, UW 4000 × 3000. Beide Sensoren bestätigen ISO- und Zeitgrenzen.
Hauptkamera Grenze 400: ISO 400, 33.329016 ms, −0.586 EV Sensorbelichtung.
UW Grenze 800: ISO 799, 33.326425 ms, −0.195 EV; Grenze 400: ISO 400,
33.326425 ms, −1.193 EV. JPEG-Metadaten enthalten weiterhin den Einfluss
der Post-RAW-Verstärkung. Die neue UW-Request-Initialisierung funktioniert
in diesem getesteten Ablauf. Das beweist keinen Rauschgewinn oder den neuen
fixierten WB-/Fokus-/HQ-Aufnahmeweg aus 0.6.6.

Die Extension-Abfrage der getesteten Hauptkamera meldet ausschließlich NIGHT;
AUTO und HDR liefern keine Verfügbarkeit. Die physische UW-Route hat denselben
logischen Elternselektor, für den NIGHT verfügbar ist, aber keine sichere
physische Zuordnung dieser Extension. Deshalb bleibt sie bei STANDARD.
Diese Aussagen betreffen CameraX-Extensions dieses Geräts/Builds, nicht die
internen Modi der originalen Pixel-Kamera.

## Referenzen

https://developer.android.com/reference/android/hardware/camera2/CaptureRequest
https://developer.android.com/reference/android/hardware/camera2/CameraDevice
https://developer.android.com/reference/androidx/camera/extensions/ExtensionsManager
