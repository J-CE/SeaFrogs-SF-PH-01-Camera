# Pixel 8 / SeaFrogs: Testprogramm 0.5

Ziel: erst Kameraqualität und Eingabesicherheit abnehmen. Nicht unter Wasser testen.
Version 0.5.0-quality-hid. Die App kann JPEG-Fotos aufnehmen; Video und RAW fehlen.
Runter zählt und protokolliert den Befehl, schaltet aber keinen Videomodus ein.
Dieser Punkt bleibt ausdrücklich offen und verhindert eine vollständige Produktabnahme.

## Vorbereitung

APK als Update zu 0.4 installieren. Android-Version, App-Version, Akkustand und
Gehäusezustand notieren. Erst ohne Gehäuse, danach mit Gehäuse vergleichen.
Linse und Fenster reinigen. Querformat verwenden, Smartphone abstützen. Die rechte Tastenleiste lässt sich
bei kleinem Display per Touch scrollen; TESTFALL steht unten.
Ein flaches Motiv mit feinem Text, Stoffstruktur oder Lineal verwenden.
Beleuchtung und Abstand bei jedem Vergleich konstant halten. EV 0 einstellen.
Einmal pro Zeile TESTFALL wählen, dann drei Fotos aufnehmen; dazwischen auf Bereit warten.
Fotos niemals als Screenshot oder verkleinertes Chat-Bild senden.

## Kamera, zuerst ohne Maus

STANDARD steht für den bisherigen CameraX-Aufnahmepfad. Ein Druck auf die
Qualitätstaste wechselt nur durch verfügbare Modi: STANDARD, AUTO, HDR, NIGHT.
Der Wechsel setzt Zoom auf 1 und EV auf 0. Bei physisch gepinntem UW/Macro
bleibt STANDARD aktiv; die App zeigt den Grund. Diese Einschränkung verhindert,
dass ein Qualitätstest heimlich auf einen anderen Sensor wechselt.

| Testfall | Einstellung und Ablauf | Ergebnis festhalten |
|---|---|---|
| K01 MAIN | Hauptkamera 1×, STANDARD, Motiv 50 cm entfernt | Schärfe, Dateigröße, Auflösung, AF/ISO |
| K02 UW | UW ohne zusätzlichen Zoom, gleicher Abstand | Bildwinkel, Schärfe, gemeldete Sensor-ID |
| K03 MACRO NAH | Macro 0,5× ohne Crop; nacheinander 15, 10, 5 und 3 cm Abstand von der Linse; jeweils 3 s warten | Kürzester scharfer Abstand, AF-Anzeige, Fokusdistanz |
| K04 MACRO FERN | Macro ohne Crop, zwischen 5 und 50 cm dreimal wechseln, je 3 s warten | Folgt der kontinuierliche AF in beide Richtungen? |
| K05 CROP1 | Scharfen Abstand aus K03 verwenden; Macro 1× Crop | Detailverlust gegenüber K03 |
| K06 CROP2 | Gleicher Abstand; Macro 2× Crop | Detailverlust gegenüber K03/K05 |
| K07 EXT MAIN | Hauptkamera 1×, jeweils STANDARD und jeden verfügbaren Modus; EV 0 | Details, Rauschen, Farben, Auslöse-/Speicherzeit; zusätzlich dunklere Szene |
| K08 EXT UW | UW: Qualitätstaste drücken; angebotene Modi oder Sperrmeldung notieren | Sensorzuordnung, Verfügbarkeit; nur angebotene Modi vergleichen |
| K09 EXT MACRO | Wie K08, im Macro-Modus ohne Crop | Schärfe/Verfügbarkeit; keine Extension-Unterstützung voraussetzen |
| K10 EV | Hauptkamera STANDARD, zyklisch 0/+1/+2/−1/−2/0, pro Stufe ein Foto | Tatsächlicher EV-Wert, Helligkeitsänderung, kompletter Zyklus |

Die originale Pixel-Kamera anschließend am selben Motiv testen: Hauptkamera,
Ultraweitwinkel und automatisches Macro. Abstand, Beleuchtung und Ausschnitt
notieren. Bei unterschiedlichem Bildwinkel nicht einfach Gesamtbilder vergleichen:
den gleichen Motivbereich beurteilen. Hochgerechnete 12-MP-Dateien beweisen keine
12 MP erhaltene Details. Macro 2× Crop entspricht im aktuellen Code einem 4×
Cropfaktor auf UW, also geometrisch 1/16 der Sensorfläche. Der Test entscheidet,
ob wir diese Stufe entfernen oder durch einen anderen Aufnahmepfad ersetzen.

AF-Codes: 0 inaktiv, 1 passives Suchen, 2 passiv scharf, 3 aktives Suchen,
4 scharf gesperrt, 5 unscharf gesperrt, 6 passiv unscharf. Fehlende Werte beweisen
keinen Fokusfehler. Fokusdistanz in Dioptrien ist HAL-Telemetrie, keine gemessene
Motiventfernung. EXIF latestPreviewResultNotPhotoResult enthält ausdrücklich den
letzten Vorschau-Resultatwert, keinen exakt zum JPEG gehörenden CaptureResult.
Die Anzeige PHYS bezeichnet einen passenden physischen CaptureResult. LOG
bezeichnet logische Kamera-Metadaten und bestätigt bei gepinntem UW/Macro keinen
physischen Nahfokus. Fehlende PhysicalResults bleiben offen. Extensions liefern
diese Interop-Telemetrie in dieser Version nicht.

## Mausbefehle im Kamerabild

SeaFrogs per Bluetooth verbinden. MAUS AUS drücken und das passende Android-Gerät
in der Liste auswählen. MAUS AN und MAUS BEREIT abwarten. Der Cursor verschwindet
im Pointer-Capture-Modus. Touch-Bedienung bleibt möglich. Normale Mausereignisse
konsumiert die App, damit sie keine Schaltflächen versehentlich anklicken.
Erst beginnen, wenn die Kamera Bereit zeigt. Zwischen Eingaben mindestens 1 s Pause.

| Testfall | Eingaben | Erwartung |
|---|---|---|
| M01 EINZEL | Klick 5×, Links 6×, Hoch 6×, Rechts 6×, Runter 5× | Genau ein Befehl pro Druck; Klick genau ein JPEG; Links Kamerawechsel; Hoch Zoom; Rechts EV; Runter sichtbarer Zähler und VIDEO_NOT_IMPLEMENTED |
| M02 HALTEN | Jede Richtung und Klick je 3 s halten, loslassen, 1 s warten; dreimal wiederholen | Richtung genau ein Befehl nach Ende der Bewegungsfolge; gehaltenen Klick nicht mehrfach auslösen |
| M03 DOPPEL | Hoch: 3 Doppelpaare mit 500 ms Pause; dann 3 Doppelpaare mit 100 ms Pause | 500-ms-Paare getrennt; 100-ms-Paare können zusammenfallen; beide Ergebnisse protokollieren |
| M04 KOMBINATION | Nach dem Rohdiagnosetest unten je ein Paar im Kamerabild wiederholen | Erkannte gemischte Folge: COMBINATION_BLOCKED, keine Aufnahme oder Einstellung; Abweichungen notieren |
| M05 LEBENSZYKLUS | Maus deaktivieren/aktivieren, Bluetooth trennen/verbinden, HID-Diagnose öffnen/zurück; Kamerawechsel und Klick schnell nacheinander | Keine verzögerten Befehle; fehlendes Capture sichtbar; Befehle bei nicht bereiter Kamera CAMERA_BUSY; nach Wiederverbinden bei Bedarf Maus neu auswählen |

Die App wartet auf 150 ms Signalpause. Sie behandelt Wiederholungen innerhalb
einer Folge als einen Befehl. Ohne Richtungs-Key-up können sehr schnelle
Doppeldrücke wie Halten aussehen. Eine Richtung kann erst nach Loslassen wirken.
Die Kombinationserkennung sieht nur die gelieferten Android-Ereignisse. Wenn
opposite Tasten sich im HID-Gerät gegenseitig aufheben, kann die App ihre
physische Betätigung nicht erkennen. Zeitlich getrennte Folgen zählen getrennt;
sie garantieren keine Blockierung beliebig versetzter Tastenkombinationen.

## Gleichzeitige Tasten: unveränderte Rohdiagnose

HID-Diagnose öffnen. Capture einschalten, Pointer Capture: true bestätigen.
Vor jedem Paar die passende Markierung per Touch setzen; wieder true abwarten.
Je Paar: dreimal kurz gleichzeitig drücken, jeweils 1 s Pause. Danach je 2 s halten:
(1) beide gleichzeitig loslassen, (2) zuerst die erste Taste loslassen, zweite
noch 1 s halten, (3) zuerst die zweite loslassen, erste noch 1 s halten.
Zwischen Versuchen 1 s Pause. Keine weiteren Tasten während derselben Markierung.

| Markierung | Paar |
|---|---|
| LEFT+UP | Links + Hoch |
| RIGHT+UP | Rechts + Hoch |
| LEFT+DOWN | Links + Runter |
| RIGHT+DOWN | Rechts + Runter |
| LEFT+RIGHT | Links + Rechts |
| UP+DOWN | Hoch + Runter |
| LEFT+CLICK | Links + Klick |
| RIGHT+CLICK | Rechts + Klick |
| UP+CLICK | Hoch + Klick |
| DOWN+CLICK | Runter + Klick |

Neutral markieren, 3 s nichts drücken, Export ZIP speichern. Dateiname
hid-kombinationen-capture.zip wählen. Falls Capture abbricht, betroffenen Abschnitt
neu aufnehmen und den Abbruch notieren. Kombinationen erhalten keine eigene
Kamerafunktion, bevor die Aufzeichnung ihre eindeutige Erkennung bestätigt.

## Abgabe und Abnahmekriterien

Im Kamerabild TEST ZIP speichern als kamera-maus-0.5.zip. Original-JPEGs aus
Pictures/SeaFrogs und Vergleichsfotos der Pixel-Kamera in einen zusätzlichen ZIP
packen oder als unveränderte Dateien senden. Drei Dateien reichen als Pakete:
kamera-maus-0.5.zip, hid-kombinationen-capture.zip und originalfotos-0.5.zip.
Dazu kurz notieren: kürzester scharfer Macro-Abstand, verfügbare Extensions,
auffällige Eingaben und welche Pixel-Vergleichsbilder zu welchen Tests gehören.
Testmarkierungen im EXIF und shutterRequest/photoSaved im Protokoll ordnen unsere
JPEGs zu. Der Protokollexport enthält keine Bilddateien automatisch.

Abnahme Kamera: wiederholbar scharfe Bilder ohne starken Crop; Sensor-/Fokusnachweis
soweit vom HAL geliefert; keine Aufnahmefehler; sichtbare und tatsächlich wirksame EV.
Abnahme Maus: keine doppelten Auslösungen, keine Drift, keine unbemerkten Capture-
Verluste, alle zehn Paare dokumentiert. Verlorene Datensätze oder Schreibfehler
machen den jeweiligen Trace als vollständigen Nachweis unbrauchbar.
Video/RAW und Unterwasser-Praxistest bilden spätere getrennte Meilensteine.
