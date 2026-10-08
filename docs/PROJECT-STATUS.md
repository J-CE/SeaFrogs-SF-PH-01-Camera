# Ziel und verbleibende Arbeit nach 0.8.5

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

## Offen, in dieser Reihenfolge

1. Spätere Geräteabnahme der beiden Maus-Umschaltgesten einschließlich Cursor-Rand und Wiederverbindung. Keine Änderungen der klassischen Android-Maus außerhalb des App-Fensters.
2. Macro-Nahfokus sowie tatsächliche UW-Wirkung von Zoom/AF/EV bestätigen; falls notwendig gezielte Korrektur statt neuer allgemeiner Testserie.
3. Video 4K30 abnehmen, anschließend 4K60 und UW/Macro; kodierte Auflösung/FPS und finalisierte Datei zählen, nicht angeforderte Parameter.
4. Gerätewirkung und Farbergebnis der implementierten WB-Profile bestätigen. Reines Absorptionsmodell und nominales DL08-5000-K-Modell sind noch keine Kalibrierung realer Tauchbedingungen.
5. Speicherzielauswahl, sofern weiterhin gewünscht. Aktuell feste, galeriefähige MediaStore-Ordner.
6. Kurzer Praxisvergleich mit Pixel-Kamera, anschließend Entscheidung über den ausreichend guten Produktions-Aufnahmeweg. Mehrbild bleibt bis zu neuer ausdrücklicher Entscheidung eingefroren.

Fokus-Lock, manuelle Kelvin-Werte, Vibrations-/Audiofeedback und weitere Optionen bleiben optional. Keine Aufnahmegeräusche oder Vibrationen sind erforderlich. Videoton bleibt aus. Ein Gerätetest findet später gebündelt statt, nicht nach jeder Codeänderung; Diagnoseexport nur bei einer konkreten offenen Fehlerursache.

## Dauerhafte Fotositzung (0.8.4)

RAW+JPEG und JPEG mit ISO-/Digitalgrenze verwenden im normalen Fotomodus eine offene Camera2-Sitzung. Vorschau und Belichtungsmessung laufen vor dem Klick. Kein Kamera-Neustart und keine feste 1,5-s-Wartezeit pro Foto. Diagnosereihen behalten den unabhängigen Einzelsitzungsweg. Die neue Vorschau-/JPEG-/RAW-Streamkombination und die reale Auslösezeit brauchen noch eine Abnahme am Pixel 8.

## Rückmeldung 0.8.4 und Korrektur 0.8.5

Nutzer meldet rechtsgedrehte Vorschau und Sitzungsabbruch nach gespeichertem Foto mit Grenz-Warnung. Korrigiert: doppelte Sensorrotation entfernt; gespeicherte Aufnahme mit Grenz-Warnung bleibt in der offenen Sitzung. Sensorgrenzen bleiben streng geprüft. Tatsächliche AE-/ISO-/Zeitursache aus dem Screenshot nicht bestimmbar; Anzeige und Metadaten benennen nun die konkrete Abweichung.
