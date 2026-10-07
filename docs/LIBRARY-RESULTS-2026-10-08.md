# Bibliotheksvergleich mit Pixel-8-RAWs, 2026-10-08

## Entscheidung

Wir verwenden den MIT-HDR+-Mehrbildkern als Grundlage für die nächste Kamera-
Integration. Seine reine RAW-Fusion reduziert in diesem Datensatz mehr Rauschen
als MotionCams reine Fusion. Die zusätzliche MotionCam-Wavelet-Entrauschung
senkt das Rauschen weiter, glättet aber stärkere Kanten. HDR+ benötigt im Hosttest
weniger Verarbeitungszeit und hat weniger Integrationsabhängigkeiten.
Die Wahl ist eine Projektentscheidung aus den folgenden Messungen, kein allgemeiner
Qualitätssieger für alle Szenen. Die App enthält bisher noch keine dieser Engines.

## Tatsächlich ausgeführter Test

Validiertes ZIP seafrogs-library-1791409700620.zip: 311522930 Byte,
SHA256 2cc3c08781f7177327be34397bdc3140b80e49f77e08eabdc34c3c24c82fc9e3. Keine CRC-/Exportfehler.
Hauptkamera: fünf RAWs 4080 × 3072, ISO 667, 29.997374 ms.
UW: fünf RAWs 4032 × 3016, ISO 762, 29.986825 ms.
Belichtung, WB und Fokus fix und pro Frame verifiziert; fünf unterschiedliche
Sensorframes, jeweils 1.633 s vom ersten bis letzten Frame.
Direkte Camera2-Extensions der öffentlichen IDs 0/1: nur NIGHT, HDR nicht
angeboten. CameraX AUTO/HDR übersprungen; NIGHT gespeichert mit 2560 × 1920.

HDR+ align/merge und finish laufen nativ als Halide-AOT-Kerne auf diesen RAWs.
MotionCam verwendet nativ fuse_denoise, sechslevelige forward/inverse_transform
und postprocess. OpenCV DIS PRESET_FAST mit Patch 16/Stride 8 liefert die
Bewegungsfelder, wie im ursprünglichen ImageProcessor. Die Python-Brücke
übergibt die Original-Sensordaten als Bayer-/Vierkanalbuffer. Das ist kein
Vergleich zweier fertig installierter Android-Apps: RAW-Container, Aufnahme-
automatik und automatische Tonwert-/Chroma-Parametersuche sind nicht Bestandteil
unseres Hosttests.

Geprüfte, später anhand aller C++/Header-Dateien identisch bestätigte Quellstände:
HDR+ ef4dd2ca53a51e105ed923557c726b253f05c13b (MIT),
MotionCam cc7f7c9cad5234bc939699b1ab1ffbc4bdfd6690 (GPL-3.0).
MotionCam-Generatorkopien ersetzen lediglich auto_schedule/get_auto_schedule()
durch using_autoscheduler(). Alle getesteten Generatoren kompilieren mit Halide
21.0.0. Beide RAW-Fusionkerne lassen sich zusätzlich als arm-64-android-AOT-
Bibliotheken erzeugen. Android-Link, JNI und Ausführung auf dem Pixel sind offen.

## RAW-Ergebnisse vor Schärfung/Tonwerten

| Pipeline | Hauptkamera: Rausch-Proxy reduziert | UW: Rausch-Proxy reduziert | Kantenkontrast MAIN/UW relativ zum Referenz-RAW |
| --- | ---: | ---: | ---: |
| HDR+ Fusion | 54.7 % | 57.8 % | 98.4 % / 95.6 % |
| MotionCam nur zeitliche Fusion | 48.8 % | 44.4 % | 98.3 % / 97.0 % |
| MotionCam Fusion + Wavelets | 72.7 % | 70.7 % | 95.2 % / 91.2 % |

Der Rausch-Proxy ist die mediane Hochpass-Standardabweichung in 32 gleichmäßigen
64×64-Kacheln einer grünen Bayer-Ebene. Die Kacheln wählt ausschließlich der
Median der fünf Eingaben, unabhängig von den Kandidatenausgaben. MAIN:
Einzelbild 3.979 DN, HDR+ 1.803 DN, MotionCam+Wavelet 1.086 DN. UW:
9.969 / 4.202 / 2.925 DN. Der Kanten-Proxy ist ein Sobel-Gradientenverhältnis
an starken Referenzkanten nach identischer leichter Glättung. Diese Proxys
ersetzen weder einen bekannten rauschfreien Referenzsensor noch MTF- oder
Labormessungen. 98.4 % bedeutet nicht 98.4 % aller Bilddetails. Kanten-Rausch-
Wechselwirkungen und Strukturreste bleiben möglich. Die Ausschnitte erlauben
zusätzlich die visuelle Prüfung feiner Schrift und flacher Wandflächen.

## Hostaufwand

Vier Halide-Threads, Linux x86-64, g++ 13, Halide 21.0.0; Python/OpenCV-Brücke.
Zeiten sind einzelne gemessene Hostläufe nach der Buildprüfung, keine belastbare
Pixel-Geschwindigkeitsprognose. Laufzeitschwankungen traten bei Wiederholungen auf.

| Pipeline | MAIN | UW |
| --- | ---: | ---: |
| HDR+ Fusion | 0.368 s | 0.353 s |
| HDR+ Fusion + Finish | 0.918 s | 0.878 s |
| MotionCam Fusion/Padding/Flow/Wavelets + eigener Finish | 1.484 s | 1.493 s |
| maximaler RSS des gesamten HDR+-Versuchsprozesses | 865 MiB | 841 MiB |
| maximaler RSS des gesamten MotionCam-Versuchsprozesses | 1234 MiB | 1197 MiB |

RSS enthält Python, alle Eingabebuffer, Konvertierungen und Ausgabe-/Messpuffer.
Er ist ausdrücklich kein gemessener nativer Android-Speicherbedarf. Vor einer
App-Integration müssen wir Pufferlebenszeiten minimieren und den Pixel messen.

## Ausgabepipelines und Farbe

Zusätzlich zur RAW-Messung erzeugen beide Engines Bilder in voller RAW-Auflösung.
Für den isolierten Fusionvergleich erhalten HDR+, MotionCam und Einzelbild
identische HDR+-Ausgabeoperatoren (compression=1, gain=1). Das vermeidet einen
Vorteil durch unterschiedliche Aufhellung/Schärfung. MotionCams eigene Ausgabe
läuft separat mit shadows=2, räumlicher Stärke 1, gamma=2.2, sharpen0=2.5,
sharpen1=1.3, pop=1.25 und chromaEps=0.03. Diese festen Werte ersetzen seine
App-Automatik. BGR wird vor JPEG-Ausgabe korrekt in RGB umgeordnet.

Die Brücke verwendet geprüfte aktuelle WB-Gains/ColorCorrectionTransform aus
CaptureResult und DNG-GainMaps. Für MotionCam setzt sie diese Farbabbildung in
dessen D50-PCS um. Sie durchläuft nicht dessen RawContainer-Farbmatrixloader,
der die bereits dokumentierten falschen Matrixzuweisungen enthält. DNG-
OpcodeList3-Verzeichnungskorrektur fehlt; Randpixel sind kein Qualitätsmaßstab.
Ohne Farbkarte/Referenzbeleuchtung ist keine absolute Farbrichtigkeitsnote möglich.
Die eigenständigen Standard-Looks benötigen weitere Abstimmung. HDR+-Default
compression=3.8 hellte diese Szene zu stark auf; der kontrollierte Vergleich
nutzt deshalb durchgängig compression=1. Diese Parametrierung ist dokumentiert
und für alle gemeinsamen Ausgaben identisch.

## Bewegung und Grenzen

Zusätzlicher synthetischer Test der MAIN-Serie: bekannte ganze Bayer-erhaltende
Translationen (0,0), (4,0), (0,4), (-4,-4), (12,8) Sensorpixel. Beide Kerne führen
wieder zu ausgerichteten Bildern. Fehler an starken geglätteten Referenzkanten:
HDR+ 0.947 DN statisch / 1.171 DN verschoben, MotionCam+Wavelet 1.024 / 1.161 DN.
Dieser Test simuliert globale Translation, keine Fische, Parallaxe, Rolling-
Shutter, Geisterbilder an unabhängig bewegten Motiven oder Unterwasserpraxis.

Die fünf gleich belichteten RAWs bestätigen Rauschminderung, keine Belichtungs-
reihe und keine Rekonstruktion bereits ausgebrannter Highlights. Die Messung
belegt keinen Gleichstand mit Googles proprietärer Pixel-Kamera. Macro-Nahfokus
ist weiterhin unbestätigt; das Motiv steht im normalen Fokusbereich.

## Umsetzung als nächster Meilenstein

Native HDR+-Fusion hinter einem eigenen Camera2-RAW-Burst-Aufnahmeweg, mit
geprüfter physischer UW-/Macro-Zuordnung, fixierten AE/WB/AF und begrenzten
Puffern. Pixel-Hardwaretest prüft zunächst Aufnahmezeit, RAM, Wärme, Bildausgabe
und Bewegung. Erst bei erfolgreicher Abnahme wird der Mehrbildmodus zum Standard.
Die bisherige JPEG-Aufnahme bleibt für Rückfälle bei Speicher-/Aufnahmefehlern.
Kein neues APK und keine Änderung der Gehäusetasten in diesem Benchmarkschnitt.

Der Standalone-Testaufbau in tools/library-benchmark hat GPL-3.0-only, weil er
MotionCam-Kerne und Ablaufteile verwendet. Er ist nicht im Android-Build verlinkt.
Die App bleibt Apache 2.0. Für die gewählte MIT-Integration müssen Lizenz-/Urheber-
vermerke von HDR+ und Halide erhalten bleiben.

Versionskorrektur bei der Integration: Frühere Reportfelder nannten Halide 24
fest im Messskript. Die tatsächlich installierte und verwendete Version ist
21.0.0 (auch in requirements.txt). Das Metadatenlabel ist korrigiert; die
Algorithmusausgaben und Vergleichswerte werden dadurch nicht geändert.
