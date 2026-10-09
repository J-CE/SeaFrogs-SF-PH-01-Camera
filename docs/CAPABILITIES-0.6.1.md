# Kamera-Fähigkeitsbericht 0.6.1

Wir erfassen in diesem Meilenstein die angebotenen Kamerafähigkeiten und trennen sie von erfolgreich geprüften Aufnahmen.

Wir bewahren diesen Ablauf als historischen Versionsstand. In RC1 sind die damaligen Test-/Exportbuttons teilweise entfernt; für die heutige Bedienung verwenden wir [DIVE-0.8.1.md](DIVE-0.8.1.md) und [RC1.md](RC1.md).

## Einmaliger Ablauf auf dem Pixel 8

1. Wir installieren die APK als Update; wir behalten die vorhandenen App-Daten.
2. Wir öffnen die Kameraansicht und erlauben bei Bedarf den Kamerazugriff.
3. Wir warten, bis aus „KAMERADATEN …“ die Taste „TEST ZIP“ wird.
4. Wir drücken TEST ZIP, speichern die Datei und laden die ZIP zur Auswertung hoch.

Kein neues Motiv, Normaltest oder Macrotest nötig. Die Abfrage läuft automatisch
auf einem Hintergrundthread einmal pro Kamera-Activity. Sie öffnet keine
zusätzlichen Kameras und führt keine Aufnahmen aus. Vorhandene automatisierte
Testergebnisse bleiben beim Update erhalten und werden weiterhin mit exportiert,
sofern ihre Originalfotos noch über MediaStore erreichbar sind.

## Inhalt von camera-capabilities.json

- Öffentliche Kamera-IDs und physische IDs mit ihren logischen Eltern.
- JPEG, RAW_SENSOR, RAW10, RAW12, YUV und PRIVATE: angebotene normale und
  High-Resolution-Größen mit Pixelzahl, Megapixeln, minimaler Frame-Dauer und
  Ausgabe-Stall-Dauer. Nicht angebotene Formate sind explizit markiert.
- Ab API 31 zusätzlich die Maximum-Resolution-Stream-Map und Sensorflächen,
  sofern der Treiber diese meldet. Fehlende Werte sind null und kein Beweis
  für eine bestimmte Auflösungsgrenze.
- RAW-/Manual-Sensor-/Manual-Postprocessing-Fähigkeit, Hardware-Level,
  Sensorflächen, ISO- und Belichtungszeitgrenzen, maximaler analoger ISO-Wert.
- EV-Indexbereich und exakte rationale Schrittweite, AE-/AWB-/AF-Modi,
  FPS-Bereiche, AWB-Lock, Fokusdistanz und ihre Kalibrierung, Brennweiten,
  Blenden, Stabilisierung, Rauschminderungsmodi und Zoomgrenzen.
- Namen der verfügbaren Request- und Characteristics-Keys. Numerische Modus-
  und Fähigkeitswerte entsprechen den Android-Camera2-Konstanten.
- Geräte-/Android-/App-Version und Fehler je Kamera bzw. abgefragtem Feld.

## Aussagegrenzen

Das ist eine **Abfrage angebotener Metadaten**, keine erfolgreiche RAW-Aufnahme.
Unsere App speichert weiterhin ausschließlich JPEG. Eine DNG-Aufnahme muss später
die gewählte Größe, ihre CaptureResult-Metadaten und eine passende Sensor-Pixel-
Mode-/Stream-Konfiguration tatsächlich bestätigen. Eine physische Kamera-ID kann
Metadaten liefern, ohne eigenständig geöffnet werden zu können.

Auflösungslisten einzelner Ausgaben garantieren nicht, dass jede Kombination
aus Vorschau, JPEG, RAW und Video gleichzeitig funktioniert. Extensions haben
zusätzliche Einschränkungen; dieser Bericht beschreibt die zugrunde liegenden
Camera2-Kameras und keine eigene Extension-Stream-Verhandlung.

Aus dem 0.6.0-Gerätetest: MAIN STANDARD 4080×3072, MAIN NIGHT 2560×1920,
physisch gepinntes UW/Macro 1920×1920. Bei UW/Macro meldete CameraX trotzdem
4080×3072 als konfigurierte Größe. 0.6.1 sammelt die Daten zur Klärung dieser
Abweichung; sie behebt die Ausgabegröße oder Macro-Fokuswartezeit noch nicht.

Referenzen:
- https://developer.android.com/reference/android/hardware/camera2/params/StreamConfigurationMap
- https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics
