# 0.7.2: Farbe, Farbrauschen und Verstärkung

Der Pixel-Test 0.7.1 verarbeitet drei Serien erfolgreich, je fünf RAW-Frames.
Sensor-ISO 400 greift, aber der gemessene digitale Boost 318 % führt zu
JPEG-EXIF-ISO 1272. Mehrbild zeigt keinen überzeugenden Detailgewinn,
UW zeigt Farbrauschen und die native Ausgabe wirkt wärmer als das HAL-JPEG.
Der Bildausschnitt passt jetzt visuell weitgehend zusammen.

## Änderungen

Der Generator normalisiert LSC und Weißabgleich vor dem 16-Bit-Demosaicker.
Die Farbmatrix übernimmt die Normalisierungsfaktoren und den digitalen Boost.
So begrenzen die vorgelagerten Integer-Stufen Farbkanäle nicht vor der Matrix.
Ein synthetischer Test mit hohen WB-/Boost-Werten ergibt exakt dieselben
RGB-Werte wie die unabhängig berechnete, bereits transformierte Referenz:
[202,224,224]. Neutraltest: [118,118,118].

Der bilaterale Farbfilter arbeitet nach der sRGB-Matrix. Seine Toleranz skaliert
von 8- auf 16-Bit-Werte (257² für die Varianz); Differenzen bleiben float.
Bei vollständig ausgeschlossenen Nachbarn übernimmt er den ursprünglichen
Farbwert, statt durch null zu dividieren oder gesättigte Farben zu löschen.
Kein pauschaler Gelb-/Grün-Gegenfilter, keine Änderung der gemessenen AWB-Gains.

Setup: Sensor-ISO Auto/400/800/1600, längste Zeit 1/30, 1/60, 1/125 s bei
aktiver Sensorgrenze. Zusätzlich digitaler Boost Auto/100/200/400 %.
Eine digitale Grenze allein begrenzt weder Sensor-ISO noch Belichtungszeit.
Auto übernimmt den Messwert; Grenzen erhöhen niedrigere Verstärkung nicht.
Nicht steuerbare/unerreichbare Grenzen melden einen Fehler. Tatsächliche
Sensor-ISO, digitaler Boost und JPEG-ISO erscheinen getrennt im Status/Report.
Ein dunkleres Bild bei eingeschränkter Verstärkung ist erwartetes Verhalten.

## Pixel-Meilenstein

MEHRBILD TEST: Hauptkamera Auto, UW Auto, Hauptkamera ISO 400 + Digital 1×.
Je ein HAL-JPEG und ein natives JPEG, gleiche Sensoreinstellung/Fokus/WB pro
Paar. Handy fest abstützen, Motiv/Licht konstant. Danach TEST ZIP exportieren.
Prüfen: Farbstich, Farbrauschen, Schrift/Detail, Laufzeit, tatsächlich bestätigte
Boost-Grenze. Macro-Nahfokus, bewegte Motive und Video sind damit nicht bestätigt.
STANDARD bleibt die sichere Voreinstellung bis zum Gerätevergleich.

## Installation dieses Builds

Der vorherige temporäre Debug-Signierschlüssel ist nicht verfügbar.
Der hier ausgelieferte Build verwendet daher `-PseafrogsParallelTest=true`,
Paket `de.jce.seafrogs.test`, Launchername `SeaFrogs Test`. Er installiert neben
der vorhandenen App, ohne sie zu ersetzen. Seine Einstellungen/Permissions
sind eigenständig. Der Standard-Build behält `de.jce.seafrogs` bei.
