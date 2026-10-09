# Weißabgleich 0.8.2

Wir leiten unsere Unterwasserprofile aus veröffentlichten Daten und ausdrücklich benannten Modellannahmen ab.

## Bedienung

SETUP → WEISSABGLEICH: Auto, unterstützte Camera2-Lichtpresets sowie
Unterwasser flach (0–8 m), mittel (>8–20 m), tief (>20 m) und DL08-Flutlicht.
SETUP → UW-WB STÄRKE: 25/50/75/100 Prozent; Default 50 Prozent.
DL08 verwendet unabhängig davon den nominalen 5000-K-Weißpunkt.
Auswahl und Stärke bleiben gespeichert. Kein zusätzlicher Gehäusebefehl.
Foto, Vorschau, RAW+JPEG und Video erhalten denselben angeforderten WB.
Nicht unterstützte manuelle Profile erscheinen nicht in der Auswahl.
Eine gespeicherte, auf einer anderen Route nicht verfügbare Auswahl meldet den
Grund und verwendet sichtbar Auto. Manuelle Profile schließen Extensions aus.

## Daten und Modell

Wir leiten die Weißpunkte für die drei Tiefen aus folgenden Daten und Rechenschritten ab:

- Pope/Fry (1997), Absorptionsspektrum reinen Wassers, 380–700 nm, DOI 10.1364/AO.36.008710.
  OMLC-Transkription, Einheiten 1/cm → 1/m.
- CIE D65, DOI 10.25039/CIE.DS.hjfjmt59, sowie CIE 1931 2°,
  DOI 10.25039/CIE.DS.xvudnb9b. Daten-Hashes prüft derive.py.
- Beer-Lambert: S(λ)=D65(λ)·exp(−a(λ)·L), anschließend XYZ-Integration bei 1 nm
  im Bereich 380–700 nm und Normierung auf Y=1.

Unsere vereinbarten Bereiche brauchen für ein einzelnes Preset eine
repräsentative Modellposition: 4 m, 14 m, 25 m. Dazu kommt jeweils 1 m Motivabstand,
also Modellwege von 5/15/26 m. Das sind ausdrücklich Implementierungsannahmen,
keine gemessenen Grenztiefen. Der Bereich >20 m bedeutet nicht identische
physikalische Korrektur bei beliebiger Tiefe. Reines Wasser berücksichtigt weder
Schwebstoffe/gelöste Stoffe noch Streuung, Sonnenwinkel, Hintergrundlicht oder
individuelle Motivabstände. Daher sind die Profile modellbasierte Startprofile
für klares Wasser, keine kalibrierten Meerwasserprofile und keine Sea-Thru-Restaurierung.

Wir haben die früher gefundene Haifa-RAW-Sammlung nicht numerisch ausgewertet oder
für diese Koeffizienten verwendet. Ihr Vorhandensein allein liefert keine
Pixel-Werte. Hauptkamera-DNG aus unserem vorhandenen Geräteexport diente nur zur
Plausibilitätsprüfung der Sensorübertragung, nicht zur Unterwasser-Kalibrierung.

## Sensorübertragung

Wir lesen pro Route D65-ColorMatrix, CameraCalibration und ForwardMatrix.
ColorMatrix und Calibration übertragen XYZ in angenäherte Sensor-Neutralwerte.
Inverse Neutralwerte ergeben Bayer-Gains. Die Korrekturstärke interpoliert
logarithmisch zwischen D65 und dem modellierten Wasser-Weißpunkt.
Wir normalisieren den kleinsten Gain auf 1. Eine feste D65-Kalibrierungsmatrix
ist eine lineare Näherung für gefilterte Spektren und ersetzt keine spektralen
Empfindlichkeitskurven des Pixel-Sensors. Hauptkamera und UW verwenden getrennte
Metadaten. Nichtdiagonale Calibration-Matrizen benötigen ein erweitertes Modell
und werden abgelehnt, statt still falsch angewendet zu werden.

ForwardMatrix, diagonale Calibration, D50→D65-Adaption und XYZ→lineares sRGB
bilden die CCM. Zeilennormierung erhält neutrales Grau. Matrixelemente außerhalb
Camera2-garantierter −1,5…3 werden abgelehnt. AWB OFF und TRANSFORM_MATRIX
aktivieren angeforderte Gains/CCM. Es gibt keinen universellen Kelvin→RGGB-Trick.

Mit dem vorhandenen Hauptkamera-DNG ergibt 100 Prozent ungefähr R/G/B:
flach 4,146/1/1,247; mittel 9,358/1,062/1; tief 18,437/1,404/1.
Die G-Even/Odd-Werte sind gleich. Das demonstriert die aggressive Verstärkung
bei voller Neutralisierung, keine bestätigte Aufnahmequalität.
Camera2 garantiert nur Gains von 1 bis 3 ohne Begrenzung. Höhere Werte bleiben
Anforderungen; unsere App vergleicht Sensorresultate mit Gains/CCM und zeigt
WB ANGEWENDET, WB NICHT BESTÄTIGT oder WB ABWEICHEND / LIMITIERT.
Angewendet bedeutet Parameterübernahme, nicht nachgewiesene Farbtreue.
Fotoaufnahme bleibt bei Warnung möglich. RAW/JPEG protokolliert zusätzlich den
WB-Abgleich des tatsächlichen CaptureResult. DNG-Sensorwerte bleiben RAW;
WB betrifft Metadaten und JPEG-Verarbeitung, keine eingefärbte RAW-Pixelmatrix.

## DL08

Wurkkos nennt 5000 K und CRI 90 für das 133°-Flutlicht; der Spot nennt
6000–6500 K und 12°. Profil DL08 gilt ausschließlich für weißes Flutlicht.
Die nominale Farbtemperatur allein beschreibt nicht dessen LED-Spektrum.
Wir modellieren den Weißpunkt als 5000-K-Planck-Strahler; Duv und gemessene
spektrale Leistungsverteilung des konkreten Exemplars liegen nicht vor.
Kein Tiefenaufschlag: für lampendominiertes Nahmotiv. Lange Lichtwege und
Mischlicht bleiben außerhalb dieser einfachen Näherung.
https://wurkkos.com/products/wurkkos-dl08-3600lm-rechargeable-diving-light

## Reproduzierbarkeit und Lizenz

Mit python3 tools/wb-model/derive.py erzeugen wir whitepoints.json (numpy erforderlich).
CIE-abgeleitete Datendatei: CC BY-SA 4.0, Attribution/Änderungen auch in APK.
App-Implementierung: Apache 2.0. Kein Forschungsbild oder fremder
Bildrestaurierungs-Code wird übernommen.

Mit unserer Softwareprüfung decken wir Neutralisierung, Stärke, grauerhaltende CCM,
Erkennung begrenzter Gains und ungültiger Sensorwerte ab. Ein Pixel-/Tauchtest
steht aus. Keine neue ZIP-Anforderung und kein behaupteter Qualitätsgewinn.
