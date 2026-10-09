# Pixel 8 / SeaFrogs: Testprogramm 0.5

Wir beschreiben hier unser damaliges gemeinsames Testprogramm für JPEG und HID.

Wir bewahren diesen Ablauf als historischen Versionsstand. In RC1 sind die damaligen Test-/Exportbuttons teilweise entfernt; für die heutige Bedienung verwenden wir [DIVE-0.8.1.md](DIVE-0.8.1.md) und [RC1.md](RC1.md).

Wir nehmen zuerst Kameraqualität und Eingabesicherheit im Trockenen ab.
Version 0.5.0-quality-hid. Unsere App kann JPEG-Fotos aufnehmen; Video und RAW fehlen.
Runter zählt und protokolliert den Befehl, schaltet aber keinen Videomodus ein.
Dieser Punkt bleibt ausdrücklich offen und verhindert eine vollständige Produktabnahme.

## Vorbereitung

Wir installieren die APK als Update zu 0.4 und notieren Android-Version, App-Version, Akkustand und
Gehäusezustand. Wir vergleichen zuerst ohne Gehäuse, danach mit Gehäuse.
Wir reinigen Linse und Fenster. Wir verwenden Querformat und stützen das Smartphone ab. Die rechte Tastenleiste lässt sich
bei kleinem Display per Touch scrollen; TESTFALL steht unten.
Wir verwenden ein flaches Motiv mit feinem Text, Stoffstruktur oder Lineal.
Wir halten Beleuchtung und Abstand bei jedem Vergleich konstant. Wir stellen EV 0 ein.
Wir wählen einmal pro Zeile TESTFALL und nehmen drei Fotos auf; dazwischen warten wir auf Bereit.
Wir senden Fotos als unveränderte Originaldateien.

## Kamera, zuerst ohne Maus

STANDARD steht für den bisherigen CameraX-Aufnahmepfad. Ein Druck auf die
Qualitätstaste wechselt nur durch verfügbare Modi: STANDARD, AUTO, HDR, NIGHT.
Der Wechsel setzt Zoom auf 1 und EV auf 0. Bei physisch gepinntem UW/Macro
bleibt STANDARD aktiv; unsere App zeigt den Grund. Diese Einschränkung verhindert,
dass ein Qualitätstest heimlich auf einen anderen Sensor wechselt.

| Testfall | Einstellung und Ablauf | Unser Ergebnis |
|---|---|---|
| K01 MAIN | Hauptkamera 1×, STANDARD, Motiv 50 cm entfernt | Schärfe, Dateigröße, Auflösung, AF/ISO |
| K02 UW | UW ohne zusätzlichen Zoom, gleicher Abstand | Bildwinkel, Schärfe, gemeldete Sensor-ID |
| K03 MACRO NAH | Macro 0,5× ohne Crop; nacheinander 15, 10, 5 und 3 cm Abstand von der Linse; wir warten jeweils 3 s | Kürzester scharfer Abstand, AF-Anzeige, Fokusdistanz |
| K04 MACRO FERN | Macro ohne Crop, wir wechseln dreimal zwischen 5 und 50 cm und warten je 3 s | Folgt der kontinuierliche AF in beide Richtungen? |
| K05 CROP1 | Wir verwenden den scharfen Abstand aus K03; Macro 1× Crop | Detailverlust gegenüber K03 |
| K06 CROP2 | Gleicher Abstand; Macro 2× Crop | Detailverlust gegenüber K03/K05 |
| K07 EXT MAIN | Hauptkamera 1×, jeweils STANDARD und jeden verfügbaren Modus; EV 0 | Details, Rauschen, Farben, Auslöse-/Speicherzeit; zusätzlich dunklere Szene |
| K08 EXT UW | UW: Wir drücken die Qualitätstaste und notieren angebotene Modi oder Sperrmeldung | Sensorzuordnung, Verfügbarkeit; wir vergleichen nur angebotene Modi |
| K09 EXT MACRO | Wie K08, im Macro-Modus ohne Crop | Schärfe/Verfügbarkeit; wir setzen keine Extension-Unterstützung voraus |
| K10 EV | Hauptkamera STANDARD, zyklisch 0/+1/+2/−1/−2/0, pro Stufe ein Foto | Tatsächlicher EV-Wert, Helligkeitsänderung, kompletter Zyklus |

Anschließend testen wir die originale Pixel-Kamera am selben Motiv: Hauptkamera,
Ultraweitwinkel und automatisches Macro. Wir notieren Abstand, Beleuchtung und Ausschnitt.
Bei unterschiedlichem Bildwinkel beurteilen wir den gleichen Motivbereich. Hochgerechnete 12-MP-Dateien beweisen keine
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

Wir verbinden SeaFrogs per Bluetooth, drücken MAUS AUS und wählen das passende Android-Gerät
in der Liste aus. Wir warten auf MAUS AN und MAUS BEREIT. Der Cursor verschwindet
im Pointer-Capture-Modus. Touch-Bedienung bleibt möglich. Normale Mausereignisse
konsumiert unsere App, damit sie keine Schaltflächen versehentlich anklicken.
Wir beginnen erst, wenn die Kamera Bereit zeigt. Wir lassen zwischen Eingaben mindestens 1 s Pause.

| Testfall | Eingaben | Erwartung |
|---|---|---|
| M01 EINZEL | Klick 5×, Links 6×, Hoch 6×, Rechts 6×, Runter 5× | Genau ein Befehl pro Druck; Klick genau ein JPEG; Links Kamerawechsel; Hoch Zoom; Rechts EV; Runter sichtbarer Zähler und VIDEO_NOT_IMPLEMENTED |
| M02 HALTEN | Wir halten jede Richtung und Klick je 3 s, lassen los und warten 1 s; wir wiederholen dies dreimal | Richtung genau ein Befehl nach Ende der Bewegungsfolge; gehaltenen Klick nicht mehrfach auslösen |
| M03 DOPPEL | Hoch: 3 Doppelpaare mit 500 ms Pause; dann 3 Doppelpaare mit 100 ms Pause | 500-ms-Paare getrennt; 100-ms-Paare können zusammenfallen; wir protokollieren beide Ergebnisse |
| M04 KOMBINATION | Nach dem Rohdiagnosetest unten wiederholen wir je ein Paar im Kamerabild | Erkannte gemischte Folge: COMBINATION_BLOCKED, keine Aufnahme oder Einstellung; wir notieren Abweichungen |
| M05 LEBENSZYKLUS | Maus deaktivieren/aktivieren, Bluetooth trennen/verbinden, HID-Diagnose öffnen/zurück; Kamerawechsel und Klick schnell nacheinander | Keine verzögerten Befehle; fehlendes Capture sichtbar; Befehle bei nicht bereiter Kamera CAMERA_BUSY; nach Wiederverbinden wählen wir bei Bedarf die Maus neu aus |

Unsere App wartet auf 150 ms Signalpause. Sie behandelt Wiederholungen innerhalb
einer Folge als einen Befehl. Ohne Richtungs-Key-up können sehr schnelle
Doppeldrücke wie Halten aussehen. Eine Richtung kann erst nach Loslassen wirken.
Die Kombinationserkennung sieht nur die gelieferten Android-Ereignisse. Wenn
opposite Tasten sich im HID-Gerät gegenseitig aufheben, kann unsere App ihre
physische Betätigung nicht erkennen. Zeitlich getrennte Folgen zählen getrennt;
sie garantieren keine Blockierung beliebig versetzter Tastenkombinationen.

## Gleichzeitige Tasten: unveränderte Rohdiagnose

Wir öffnen die HID-Diagnose, schalten Capture ein und prüfen Pointer Capture: true.
Vor jedem Paar setzen wir die passende Markierung per Touch und warten wieder auf true.
Je Paar drücken wir dreimal kurz gleichzeitig mit jeweils 1 s Pause. Danach halten wir je 2 s:
(1) Wir lassen beide gleichzeitig los. (2) Wir lassen zuerst die erste Taste los und halten die zweite
noch 1 s. (3) Wir lassen zuerst die zweite los und halten die erste noch 1 s.
Zwischen Versuchen lassen wir 1 s Pause. Während derselben Markierung drücken wir keine weiteren Tasten.

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

Wir markieren Neutral, drücken 3 s nichts und speichern Export ZIP unter dem Dateinamen
hid-kombinationen-capture.zip. Falls Capture abbricht, nehmen wir den betroffenen Abschnitt
neu auf und notieren den Abbruch. Kombinationen erhalten keine eigene
Kamerafunktion, bevor die Aufzeichnung ihre eindeutige Erkennung bestätigt.

## Abgabe und Abnahmekriterien

Im Kamerabild speichern wir TEST ZIP als kamera-maus-0.5.zip. Wir packen Original-JPEGs aus
Pictures/SeaFrogs und Vergleichsfotos der Pixel-Kamera in eine zusätzliche ZIP
oder senden sie als unveränderte Dateien. Drei Dateien reichen als Pakete:
kamera-maus-0.5.zip, hid-kombinationen-capture.zip und originalfotos-0.5.zip.
Dazu notieren wir kurz: kürzester scharfer Macro-Abstand, verfügbare Extensions,
auffällige Eingaben und welche Pixel-Vergleichsbilder zu welchen Tests gehören.
Testmarkierungen im EXIF und shutterRequest/photoSaved im Protokoll ordnen unsere
JPEGs zu. Der Protokollexport enthält keine Bilddateien automatisch.

Abnahme Kamera: wiederholbar scharfe Bilder ohne starken Crop; Sensor-/Fokusnachweis
soweit vom HAL geliefert; keine Aufnahmefehler; sichtbare und tatsächlich wirksame EV.
Abnahme Maus: keine doppelten Auslösungen, keine Drift, keine unbemerkten Capture-
Verluste, alle zehn Paare dokumentiert. Verlorene Datensätze oder Schreibfehler
machen den jeweiligen Trace als vollständigen Nachweis unbrauchbar.
Video/RAW und Unterwasser-Praxistest bilden spätere getrennte Meilensteine.
