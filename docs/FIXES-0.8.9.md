# HID-Korrekturen 0.8.9

Wir dokumentieren hier unseren ersten Korrekturschritt für Mausauswahl und Umschaltgesten. Den nachfolgenden Übergangsfix beschreiben wir in [FIXES-0.8.10.md](FIXES-0.8.10.md).

Mausauswahl und Wechsel zwischen Kamera- und klassischer Maussteuerung ändern die sichtbaren Toolbar-Buttons. GridLayout vergibt beim Messen konkrete Zellpositionen, auch wenn die ursprünglichen Specs UNDEFINED verwenden. Das frühere Reduzieren der Spaltenzahl behielt diese aufgelösten Positionen bei. Spalten außerhalb des neuen Gitters erzeugen IllegalArgumentException. Für die damalige Neuanordnung entfernen wir zuerst die Kinder, setzen frische automatische Zeilen-/Spaltenspecs und fügen dieselben Buttons in derselben Reihenfolge wieder hinzu. Nur CameraScreenLayout bestimmt die Spaltenzahl.

Links + Hoch aktiviert Kamera-Steuerung; Rechts + Runter aktiviert die klassische Maus. Wir schalten jetzt während des laufenden Bursts, sobald beide Richtungen vorliegen. Bisher wartete sie auf eine Pause und verschob die Umschaltung bei jeder Wiederholung. Ein verbrauchter Burst unterdrückt weitere Umschaltungen und Kamerabefehle bis 150 ms Ruhe, auch beim Pointer-Capture-Wechsel.

Die Auswahl im Setup aktiviert die Steuerung unmittelbar. Der Button zeigt nun eindeutig STEUERUNG AUS / KLASSISCHE MAUS oder SEAFROGS-MAUS AUSWÄHLEN. Die gespeicherte Geräteauswahl bleibt im klassischen Modus erhalten, damit Links + Hoch weiter erkannt werden kann.

Mit Softwaretests prüfen wir wiederholte Toolbar-Neuanordnung mit Android GridLayout sowie gehaltene Kombinationen und deren Restereignisse. Die tatsächlichen kombinierten HID-Berichte des Gehäuses und deren Erkennung am Pixel brauchen weiterhin Gerätebestätigung. Im klassischen Modus kann Android an Bildschirmrändern Bewegungsachsen abschneiden, wenn es keine relativen Achsen liefert. Die Mausauswahl im Setup bleibt dann der direkte Rückweg zur Steuerung.

Bei unserer Validierung bestanden 71 Tests, 0 Fehler und 0 übersprungen. `testDebugUnitTest lintDebug assembleDebug` erfolgreich. APK: `de.jce.seafrogs.test`, versionCode 28, versionName 0.8.9-hid-fix. Signatur identisch mit 0.8.8; apksigner und 16-KiB-zipalign bestanden.
