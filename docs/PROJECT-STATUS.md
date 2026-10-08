# Ziel und verbleibende Arbeit nach 0.8.1

Das Produktziel bleibt eine zuverlässige Pixel-8-Unterwasserkamera mit fünf SeaFrogs-Eingaben. Ein Build oder Unit-Test beweist keine Unterwasser-Bildqualität und keinen funktionierenden Nahfokus.

## Umgesetzt

Kamerasteuerung, JPEG/RAW+JPEG, EXIF, UW-/Macro-Sensorauswahl, Zoom/EV,
Foto/Video, stille 4K-Konfiguration, Tauchprofil (Helligkeit/Ausrichtung),
gespeicherte Mausauswahl, Wiederverbindung, reduzierter Steuerungsmodus,
Akku-/Speicher-/Temperaturstatus, unterstützte WB-Presets, editierbare Zyklen.
Aktuelle Belegung und Implementierungsgrenzen: DIVE-0.8.1.md.

## Offen, in dieser Reihenfolge

1. Spätere Geräteabnahme der beiden Maus-Umschaltgesten einschließlich Cursor-Rand und Wiederverbindung. Keine Änderungen der klassischen Android-Maus außerhalb des App-Fensters.
2. Macro-Nahfokus sowie tatsächliche UW-Wirkung von Zoom/AF/EV bestätigen; falls notwendig gezielte Korrektur statt neuer allgemeiner Testserie.
3. Video 4K30 abnehmen, anschließend 4K60 und UW/Macro; kodierte Auflösung/FPS und finalisierte Datei zählen, nicht angeforderte Parameter.
4. Unterwasser-WB flach/mittel/tief und Videolicht definieren und implementieren. Ein Tageslicht-Preset ersetzt diese Arbeit nicht.
5. Speicherzielauswahl, sofern weiterhin gewünscht. Aktuell feste, galeriefähige MediaStore-Ordner.
6. Kurzer Praxisvergleich mit Pixel-Kamera, anschließend Entscheidung über den ausreichend guten Produktions-Aufnahmeweg. Mehrbild bleibt bis zu neuer ausdrücklicher Entscheidung eingefroren.

Fokus-Lock, manuelle Kelvin-Werte, Vibrations-/Audiofeedback und weitere Optionen bleiben optional. Keine Aufnahmegeräusche oder Vibrationen sind erforderlich. Videoton bleibt aus. Ein Gerätetest findet später gebündelt statt, nicht nach jeder Codeänderung; Diagnoseexport nur bei einer konkreten offenen Fehlerursache.
