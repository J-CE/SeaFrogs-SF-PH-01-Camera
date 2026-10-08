# Ziel und verbleibende Arbeit nach 0.8.8

Das Produktziel bleibt eine zuverlässige Pixel-8-Unterwasserkamera mit fünf SeaFrogs-Eingaben. Ein Build oder Unit-Test beweist keine Unterwasser-Bildqualität und keinen funktionierenden Nahfokus.

## Umgesetzt

Kamerasteuerung, JPEG/RAW+JPEG, EXIF, UW-/Macro-Sensorauswahl, Zoom/EV,
Foto/Video, stille 4K-Konfiguration, Tauchprofil (Helligkeit/Ausrichtung),
gespeicherte Mausauswahl, Wiederverbindung, reduzierter Steuerungsmodus,
Akku-/Speicher-/Temperaturstatus, unterstützte WB-Presets, editierbare Zyklen.
Modellbasierte WB-Profile flach/mittel/tief und DL08-Flutlicht; einstellbare
UW-Stärke und Abgleich der gemeldeten Gains/Farbmatrix (WB-0.8.2.md).
Aktuelle Belegung: DIVE-0.8.1.md. Neuere WB- und UI-Beschreibungen haben Vorrang:
WB-0.8.2.md und UI-4-3.md. Größte vollständige 4:3-Fotovorschau, kompakte
Bedienung in Randflächen und Setup als Overlay. Aufnahmeoperationen trennen
alte Video-Rückmeldungen; RAW-Startfehler führen über den Abschluss zurück zur
Kamera. Fehlende JPEG-/DNG-Dateien gelten als Aufnahmefehler.

## Nutzerabnahme und nächste Meilensteine (2026-10-08)

Nach 0.8.6 meldet der Nutzer: Alles, was er ohne SeaFrogs-Maus testen kann, funktioniert. Dies ist eine positive Funktionsabnahme am Telefon, kein unabhängiger Beleg jeder Codec-/Sensoreigenschaft oder der Unterwasser-Bildqualität. Nach der gewünschten Setup-Sortierung plant das Projekt keine weitere große Pflichtfunktion.

1. **SeaFrogs-HID:** Links/Oben/Unten/Rechts/Klick, schnelle und wiederholte Eingaben, Links+Oben für App-Steuerung, Rechts+Unten für klassische Maus, Cursor am Rand, Trennung/Wiederverbindung und zuverlässiges Auslösen. Die beiden vereinbarten Umschaltgesten bleiben die einzigen vorgesehenen Kombinationen.
2. **Gehäuse und Wasser:** Freies Kamerafenster für beide Sensoren, Nahfokus hinter dem Gehäusefenster, WB-Farben mit und ohne DL08-Licht, ausreichende Displayhelligkeit und tatsächliche Aufnahmeverzögerung.
3. **Dauerbetrieb:** längere stille 4K30/4K60-Aufnahmen, gespeicherte und abspielbare Dateien, Wärme, Akku und Speicher im realen Betrieb. Bei einem konkreten Fehler nur die betroffene Funktion prüfen; keine erneute allgemeine Diagnoseserie.

Speicherzielauswahl bleibt optional; die aktuellen MediaStore-Ziele funktionieren als Pictures/SeaFrogs und Movies/SeaFrogs. Ein kurzer Praxisvergleich mit der Pixel-Kamera dient der Bildqualitätsbewertung. Mehrbild bleibt eingefroren.

Fokus-Lock, manuelle Kelvin-Werte, Vibrations-/Audiofeedback und weitere Optionen bleiben optional. Keine Aufnahmegeräusche oder Vibrationen sind erforderlich. Videoton bleibt aus. Ein Gerätetest findet später gebündelt statt, nicht nach jeder Codeänderung; Diagnoseexport nur bei einer konkreten offenen Fehlerursache.

## Dauerhafte Fotositzung (0.8.4)

RAW+JPEG und JPEG mit ISO-/Digitalgrenze verwenden im normalen Fotomodus eine offene Camera2-Sitzung. Vorschau und Belichtungsmessung laufen vor dem Klick. Kein Kamera-Neustart und keine feste 1,5-s-Wartezeit pro Foto. Diagnosereihen behalten den unabhängigen Einzelsitzungsweg. Die neue Vorschau-/JPEG-/RAW-Streamkombination und die reale Auslösezeit brauchen noch eine Abnahme am Pixel 8.

## Rückmeldung 0.8.4 und Korrektur 0.8.5

Nutzer meldet rechtsgedrehte Vorschau und Sitzungsabbruch nach gespeichertem Foto mit Grenz-Warnung. Korrigiert: doppelte Sensorrotation entfernt; gespeicherte Aufnahme mit Grenz-Warnung bleibt in der offenen Sitzung. Sensorgrenzen bleiben streng geprüft. Tatsächliche AE-/ISO-/Zeitursache aus dem Screenshot nicht bestimmbar; Anzeige und Metadaten benennen nun die konkrete Abweichung.

## WB-/EV-Korrektur 0.8.6

WB-Statusanzeige und Sitzungsabschluss verhindern veraltete Profilindizes bei Presetwechseln. EV zeigt sofort die gewünschte Stufe; schnelle Folgen verwerfen veraltete Rückmeldungen. Camera2-Requestbestätigung ist von physischer Sensormetadaten-Verfügbarkeit getrennt. AE-Konvergenz bleibt hardwareabhängig.

## Gemeinsamer Setup-Block 0.8.7

Fotoformat und ISO-/Digitalgrenzen sind jetzt einzelne Setup-Einträge neben den übrigen Voreinstellungen; die separate Zeile entfällt. Tauchansicht und 4:3-Fotovorschau behalten ihre Anordnung.

## Oberflächenbereinigung 0.8.8

Entfernt aus der UI: Macrotest, Testabbruch, Kameradaten/Diagnose-ZIP, Qualitätsumschaltung sowie bereits verborgene Mehrbild-Test-/Exportbutton-Instanzen. Diagnosefunktionen bleiben als Quelltext erhalten. Mausauswahl, HID-Diagnose und Neustart bleiben im Setup bis zur Gehäuseabnahme.
