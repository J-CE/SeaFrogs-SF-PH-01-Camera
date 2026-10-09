Historisches Architekturprotokoll. Aktuell gelten nur zwei Macro-Stufen gemäß [DIVE-0.8.1.md](DIVE-0.8.1.md).

# Objektivmodi 0.3.0-lenses

Wir dokumentieren hier die ursprüngliche Auswahl unserer Objektivmodi.

Der Meilenstein 0.4.0 baut diesen Objektivcode erfolgreich. Pixel-8-Prüfung noch offen.

| Modus | Ausgabe | Fokus |
| --- | --- | --- |
| 1× | Standard-Rückkamera, Zoomverhältnis 1 | Kontinuierlicher Foto-AF |
| Macro | Autofokusfähiges Ultraweitwinkel, voller Ausschnitt | Mittiger AF-Neustart, danach kontinuierlicher Foto-AF |
| 0,5× | Ultraweitwinkel, voller Ausschnitt | Normaler Kamera-AF, soweit verfügbar |

0,5× ist die nominelle Benutzerbezeichnung, keine gemessene Brennweitenrelation.
Version 0.4.0 ergänzt Macro-Crops über Faktoren 1/2/4 des UW-Ausschnitts. Es wird keine manuelle Fokusdistanz
und kein Nahfokus garantiert; Macro muss am realen Motiv abgenommen werden.

## Auswahl ohne fest hinterlegte Kamera-IDs

Mit CameraLensCatalog lesen wir öffentliche rückseitige CameraX-Kameras sowie, ab
API 28, deren physische Camera2-Sensoren. JPEG-Ausgabe und optische Metadaten
sind Voraussetzung. Brennweite geteilt durch Sensorbreite dient als Vergleich
des horizontalen Bildfelds. Als Referenz dient der Sensor, dessen Größe und
Pixelzahl den Metadaten der Standard-Rückkamera am nächsten liegen.
Ein um mindestens 20 Prozent geringerer Quotient gilt als breiterer Kandidat.
Das ist eine dokumentierte Heuristik, keine allgemeine Android-Zusage.
Keine Referenz oder kein breiterer Kandidat bedeutet: kein Ultraweitwinkelmodus.

Macro verlangt positive minimale Fokusdistanz sowie AUTO und CONTINUOUS_PICTURE
in den AF-Metadaten. Standalone-Kameras werden bei optisch gleichen Kandidaten
bevorzugt. Physische IDs werden an Preview UND ImageCapture gepinnt.
CameraX steuert dabei die logische Elternkamera; AF-/Zoomwirkung und unterstützte
Streamkombinationen müssen auf der jeweiligen HAL geprüft werden.

## Steuerung und Fehler

PhotoCameraController.cycleLens() wird von der großen UI-Taste verwendet und
kann später unverändert an den HID-Adapter angebunden werden. Keine Annahme
über Gehäuseevents ist enthalten. Nicht verfügbare Schritte behalten die bisherige
Kamera und melden den Grund; beim nächsten Befehl folgt der nächste Zyklusschritt.

Beim Binden oder Zurücksetzen des Zooms versuchen wir bei einem Fehler einmal,
den vorherigen Modus neu zu öffnen. Eine zweite Fehlermeldung fordert Neustart.
Wir ignorieren asynchrone Rückmeldungen alter Sitzungen über Generationen.
Macro-AF nutzt FocusMeteringAction mit zweisekündiger Auto-Cancel-Absicherung.
Nach Abschluss rufen wir cancelFocusAndMetering explizit auf, damit die
vorübergehende AF-Sperre nicht den laufenden Autofokus ersetzt.

Quellen:
- https://developer.android.com/reference/androidx/camera/camera2/interop/Camera2Interop.Extender#setPhysicalCameraId(java.lang.String)
- https://developer.android.com/reference/androidx/camera/core/CameraControl#startFocusAndMetering(androidx.camera.core.FocusMeteringAction)
- https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics
