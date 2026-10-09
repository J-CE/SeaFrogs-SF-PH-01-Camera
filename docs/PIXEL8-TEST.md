# Meilenstein 1: HID auf Pixel 8

Wir beschreiben hier unsere ursprüngliche HID-Rohdiagnose, bevor wir die Gehäusebelegung am Gerät bestätigt hatten.

Wir bewahren diesen Ablauf als historischen Versionsstand. In RC1 sind die damaligen Test-/Exportbuttons teilweise entfernt; für die heutige Bedienung verwenden wir [DIVE-0.8.1.md](DIVE-0.8.1.md) und [RC1.md](RC1.md).

Status: Hardwaretest offen. Keine simulierten Ergebnisse eintragen.

1. Wir installieren die Debug-APK, koppeln das Gehäuse und öffnen unsere App. Wir notieren Modell, Android-Version
   und Gehäusezustand außerhalb des Exports und prüfen die Geräteliste.
2. Im Normal-Modus markieren wir „Neutral“. Wir drücken fünf Sekunden lang nichts.
3. Wir markieren „Links“, drücken einmal kurz Links und lassen los. Wir warten zwei Sekunden. Wir wiederholen dies fünfmal. Danach halten wir Links drei Sekunden, lassen los
   und warten drei Sekunden. Zum Schluss drücken wir fünfmal schnell einzeln.
4. Wir wiederholen Schritt 3 für Rechts, Hoch, Runter und Klick. Wir setzen Markierungen immer
   per Touch, damit die Mausereignisse des Gehäuses isoliert bleiben.
5. Wir exportieren die ZIP. Wir benennen den Export ausdrücklich als „normal“.
6. Wir aktivieren Capture. Status muss tatsächlich „true“ anzeigen. Wir wiederholen die Schritte 2 bis 5
   und benennen den Export als „capture“.
7. Im Capture-Modus trennen wir die Bluetooth-Verbindung und stellen sie wieder her. Wir beobachten Capture-
   und Geräteänderungen. Wir verlassen unsere App kurz und kehren zurück.
   Wir drücken erneut eine Taste. Wir exportieren eine separate ZIP.
8. Optional testen wir am Bildschirmrand normale Mausbewegungen. Wir prüfen, ob weitere
   Tastendrücke noch Events erzeugen. Danach führen wir denselben Test in Capture durch.

## Auswertungsfragen

- Welche Geräte-ID und Quelle liefern die Gehäusetasten?
- Welche Event-Routen, Actions, Achsen und KeyCodes treten auf?
- Welche Werte liefern kurzes Drücken, Halten und Loslassen?
- Sendet Loslassen eine Gegenbewegung oder Rücksetzung? Mit welcher Verzögerung?
- Entsteht ein physischer Klick als Touch, Button-Action, KeyEvent oder Kombination?
- Gibt es wiederholte Klicks, historische Samples oder simultane Achsen?
- Bleibt Capture nach Fokusverlust und Wiederverbindung nutzbar?
- Enthält summary.txt 0 verlorene Datensätze und 0 Schreibfehler?

Abnahmekriterium: eindeutig zuordenbare Aufzeichnungen für alle fünf Tasten in
beiden Modi, einschließlich Halten und Loslassen. Erst dann bestätigen wir das Mapping.
Falls Capture scheitert, dokumentieren wir den Fehler und bewerten den normalen
Modus anhand seiner tatsächlich aufgezeichneten Ereignisse.
