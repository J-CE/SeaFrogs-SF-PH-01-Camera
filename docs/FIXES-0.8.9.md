# HID-Korrekturen 0.8.9

Mausauswahl und Wechsel zwischen Kamera- und klassischer Maussteuerung ändern die sichtbaren Toolbar-Buttons. GridLayout vergibt beim Messen konkrete Zellpositionen, auch wenn die ursprünglichen Specs UNDEFINED verwenden. Das frühere Reduzieren der Spaltenzahl behielt diese aufgelösten Positionen bei. Spalten außerhalb des neuen Gitters erzeugen IllegalArgumentException. Die Neuanordnung entfernt zuerst die Kinder, setzt frische automatische Zeilen-/Spaltenspecs und fügt dieselben Buttons in derselben Reihenfolge wieder hinzu. Nur CameraScreenLayout bestimmt die Spaltenzahl.

Links + Hoch aktiviert Kamera-Steuerung; Rechts + Runter aktiviert die klassische Maus. Die Erkennung schaltet jetzt während des laufenden Bursts, sobald beide Richtungen vorliegen. Bisher wartete sie auf eine Pause und verschob die Umschaltung bei jeder Wiederholung. Ein verbrauchter Burst unterdrückt weitere Umschaltungen und Kamerabefehle bis 150 ms Ruhe, auch beim Pointer-Capture-Wechsel.

Die Auswahl im Setup aktiviert die Steuerung unmittelbar. Der Button zeigt nun eindeutig STEUERUNG AUS / KLASSISCHE MAUS oder SEAFROGS-MAUS AUSWÄHLEN. Die gespeicherte Geräteauswahl bleibt im klassischen Modus erhalten, damit Links + Hoch weiter erkannt werden kann.

Softwaretests prüfen wiederholte Toolbar-Neuanordnung mit Android GridLayout sowie gehaltene Kombinationen und deren Restereignisse. Die tatsächlichen kombinierten HID-Berichte des Gehäuses und deren Erkennung am Pixel brauchen weiterhin Gerätebestätigung. Im klassischen Modus kann Android an Bildschirmrändern Bewegungsachsen abschneiden, wenn es keine relativen Achsen liefert. Die Mausauswahl im Setup bleibt dann der direkte Rückweg zur Steuerung.

Validierung: 71 Tests bestanden, 0 Fehler und 0 übersprungen. `testDebugUnitTest lintDebug assembleDebug` erfolgreich. APK: `de.jce.seafrogs.test`, versionCode 28, versionName 0.8.9-hid-fix. Signatur identisch mit 0.8.8; apksigner und 16-KiB-zipalign bestanden.
