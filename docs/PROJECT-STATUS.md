# Unser Projektstand nach RC1

Wir entwickeln eine zuverlässige Pixel-8-Unterwasserkamera mit fünf SeaFrogs-Eingaben. Mit **1.0.0-rc1** haben wir eine funktionierende Version erreicht. Die positive Geräterückmeldung bestätigt den trockenen Bedienbetrieb einschließlich beider Mausmodi. Unsere Softwareprüfungen und diese Rückmeldung ersetzen keine vermessene Unterwasser-Bildqualität oder einen Langzeittest.

## Was wir umgesetzt haben

Wir steuern Kamera, Zoom, EV, Foto/Video und Auslöser über die fünf Gehäuseeingaben. Mit Links + Hoch wechseln wir zur Kamerasteuerung, mit Rechts + Runter zur klassischen Maus. Seit RC1 lassen wir alle fünf Aufnahmebuttons in beiden Modi und bei geöffnetem Setup sichtbar. Unser Setup-Overlay endet oberhalb der Toolbar; die vollständige 4:3-Fotovorschau bleibt maximal groß.

Wir speichern JPEG oder RAW+JPEG sowie stille Videos mit angeforderter 4K30-/4K60-Konfiguration. Wir wählen Hauptkamera, UW oder Macro, bearbeiten Zoom-/EV-Zyklen und speichern Helligkeit, Ausrichtung, Mausauswahl und Eingabemodus. Wir zeigen Akku, freien Speicher, Wärme sowie Bereitschaft und Aufnahmezustand an.

Wir bieten unterstützte WB-Presets und modellbasierte Profile für flaches, mittleres und tiefes Wasser sowie DL08-Flutlicht an. Wir speichern die UW-Korrekturstärke und vergleichen angeforderte Gains/Farbmatrix mit den gemeldeten Sensorparametern. Die Modellannahmen und Grenzen halten wir in [WB-0.8.2.md](WB-0.8.2.md) fest.

Wir verwenden für normale RAW-/ISO-/Digitalgrenzen-Fotos eine offene Camera2-Sitzung. Dadurch entfällt ein Kamera-Neustart mit fester Wartezeit pro Foto. Wir verhindern veraltete Kamera-/Video-Rückmeldungen, behandeln fehlende JPEG-/DNG-Dateien als Fehler und lassen eine erfolgreich gespeicherte Aufnahme mit Grenz-Warnung in der laufenden Sitzung bestehen. Unsere Korrekturen für Rotation, WB, EV und HID dokumentieren wir in den jeweiligen Versionsprotokollen.

## Was wir vor 1.0 noch in der Praxis prüfen

1. **Gehäuse und Wasser:** Wir prüfen das freie Kamerafenster für beide Sensoren, Nahfokus hinter dem Fenster, Farben mit und ohne DL08, Displaylesbarkeit und tatsächliche Aufnahmeverzögerung.
2. **Dauerbetrieb:** Wir prüfen längere stille 4K30-/4K60-Aufnahmen, abspielbare Dateien, tatsächliche Ausgabeauflösung und FPS, Wärme, Akku und Speicher im realen Betrieb.
3. **Gezielte Fehlerprüfung:** Bei einem konkreten Fehler prüfen wir die betroffene Funktion. Wir verwenden einen Diagnoseexport, wenn er eine offene Fehlerursache klären kann. Einen gebündelten Gerätetest planen wir als gemeinsamen Meilenstein.

Die Gehäusebedienung haben wir anhand der positiven Rückmeldung nach 0.8.10 als funktionierend festgehalten. Damit behandeln wir die zuvor gemeldeten Umschaltabstürze als im aktuellen Bedienbetrieb behoben. Die technische Grenze nicht gelieferter relativer Mausachsen am Bildschirmrand bleibt dokumentiert.

## Was wir mit RC2 ergänzen

Wir bauen mit einem eigenen Produktionsschlüssel, prüfen Änderungen automatisch über GitHub Actions und stellen APK sowie Play-App-Bundle bereit. Wir deaktivieren sämtliche neue HID-Protokollierung einschließlich Eingaben im Absturzbericht. Kamera-/Testberichte und normale Absturzbehandlung bleiben verfügbar. Wir formatieren den Kotlin-Code und benennen Zustandsvariablen eindeutiger, ohne Kamera- oder Eingabelogik umzubauen. Unsere Play-Veröffentlichung und die Einrichtung der GitHub-Release-Secrets stehen noch aus. Details halten wir in [RELEASE.md](RELEASE.md) fest.

## Welche Optimierungen wir jetzt priorisieren

Wir priorisieren einen reproduzierbaren Release-Build, verlässliche Verteilung und anschließend messbare Verbesserungen an Eingabeprotokollierung, Oberfläche, Energiebedarf und Wartbarkeit. Unsere konkrete Reihenfolge und die jeweiligen Nachweise halten wir in [RC1-OPTIMIERUNGEN.md](RC1-OPTIMIERUNGEN.md) fest. Unsere Installationsstrategie beschreiben wir in [INSTALLATION.md](INSTALLATION.md).

Wir behalten Pictures/SeaFrogs und Movies/SeaFrogs als funktionierende Speicherziele. Speicherzielauswahl, Fokus-Lock und manuelle Kelvin-Werte bleiben optionale Erweiterungen. Wir benötigen weder Aufnahmegeräusche noch Vibrationen; Videoton bleibt aus. Für die Bildqualitätsbewertung vergleichen wir dieselbe Szene mit der Pixel-Kamera. Die Mehrbildentwicklung bleibt eingefroren.

## Wie wir den Stand einordnen

Wir verwenden [DIVE-0.8.1.md](DIVE-0.8.1.md), [UI-4-3.md](UI-4-3.md) und [RC1.md](RC1.md) für die aktuelle Bedienung. In älteren Versionsdokumenten bewahren wir damalige Ergebnisse, Testabläufe und offene Punkte. Ihre Aussagen gelten für den jeweils genannten Stand. Die dort beschriebenen früheren Test-/Exportbuttons sind in RC1 teilweise entfernt.
