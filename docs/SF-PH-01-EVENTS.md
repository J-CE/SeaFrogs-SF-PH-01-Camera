# SF-PH-01 Pro: Ereignisvertrag

Status: **nicht gemessen**. Die Tastenbelegung ist eine Produktanforderung,
kein bestätigter HID-Vertrag.

| Physische Eingabe | Gemessene Android-Ereignisse | Spätere Funktion |
|---|---|---|
| Links | offen | Macro / 0,5× / 1× |
| Hoch | offen | Zoomzyklus / Macro-Crop |
| Runter | offen | Foto / Video |
| Klick | offen | Foto / Video Start/Stop |
| Rechts | offen | EV-Zyklus |
| Loslassen | offen | Wiederfreigabe, kein Kamerabefehl |

Protokollformat: ein JSON-Objekt pro Zeile. sequence ordnet Callback-Beobachtungen;
receivedWallTimeMs bezeichnet Empfangszeit, receivedUptimeMs und eventTimeMs
verwenden Android-Uptime. Historische Samples tragen eigene eventTimeMs.
source und deviceId bleiben numerisch; Snapshot-Einträge erklären die Geräte.
Die routes generic/touch/captured bezeichnen den Beobachtungspunkt.
Touch-Eingaben laufen danach weiter zur Plattformoberfläche. Mausereignisse
konsumiert die Activity, damit Gehäuseklicks keine Diagnosebuttons betätigen. Capture-Ereignisse
konsumiert die Eingabefläche. marker ist eine manuelle Testannotation.

Motion-Samples enthalten pro Pointer ID, Werkzeugtyp und benannte Achsenwerte.
Die Achsen X, Y, VSCROLL, HSCROLL, RELATIVE_X und RELATIVE_Y enthalten auch
Nullwerte. Alle anderen Achsen erscheinen bei einem Wert ungleich 0.
Es gibt weder geschätzte neutrale Cursorposition noch automatische Entprellung.
