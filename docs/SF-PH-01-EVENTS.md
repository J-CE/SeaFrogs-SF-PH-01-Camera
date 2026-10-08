Aktuelle Bedienung: [DIVE-0.8.1.md](DIVE-0.8.1.md). Die folgende Messung beschreibt den damaligen 0.5-Stand.

# SF-PH-01 Pro: gemessene Einzelereignisse

2026-10-07, Pixel 8. Quellen: capture.zip (287 Ereignisse) und normal.zip
(239 Ereignisse); keine gemeldeten Schreibfehler oder verworfenen Datensätze.
Diese Zahlen betreffen vollständige App-Traces, keine Bluetooth-HID-Rohreports.

| Taste | Pointer Capture | Funktion in 0.5 |
|---|---|---|
| Links | X −16 | Macro / UW / Hauptkamera |
| Rechts | X +15 | EV 0/+1/+2/−1/−2 |
| Hoch | Y −16 | Zoom-/Cropzyklus |
| Runter | Y +15 | Diagnosezähler, Video fehlt |
| Klick | DOWN/BUTTON_PRESS, danach BUTTON_RELEASE/UP | Foto einmal |

Kurze Richtungsdrücke liefern ein oder zwei Bewegungen. Halten wiederholt
Bewegungen ungefähr alle 90–114 ms. Es gibt kein beobachtetes Richtungs-Key-up,
keine Richtungs-Buttonbits, keine Scroll- oder KeyEvent-Zuordnung. Zentraler
Klick erzeugt mehrere Android-Ereignisse, aber nur eine physische Betätigung.

Im normalen Modus erreichen Cursorwerte Bildschirmränder; automatische
Rücksetzung ist nicht nachgewiesen. Pointer Capture liefert relative Eingaben
und macht Cursorposition und Warp überflüssig. Die App verarbeitet nur das per
Touch ausgewählte Mausgerät im bestätigten Capture-Modus.

Der 0.5-Adapter gruppiert Signale bis 150 ms Pause. Eine einzige Richtung oder
ein Klick ergibt einen Befehl. Mehrere Richtungen bzw. Richtung plus Klick
blockieren den Befehl. Fenster-/Capture-Verlust verwirft wartende Eingaben.
Schnelle Doppeldrücke und Halten können ohne Key-up ununterscheidbar sein.
Gegensätzliche gleichzeitig gedrückte Tasten können bereits geräteseitig
Bewegungen aufheben. Die zehn Kombinationen sind noch NICHT gemessen.

Das verbindliche Testprogramm steht in TESTPROGRAMM-0.5.md. Rohereignisse,
Testmarkierungen, ausgewähltes Gerät und abgeleitete Befehle bleiben im ZIP
nachvollziehbar. Historische MotionEvent-Samples tragen eigene Zeitstempel.
