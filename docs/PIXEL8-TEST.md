# Meilenstein 1: HID auf Pixel 8

Status: Hardwaretest offen. Keine simulierten Ergebnisse eintragen.

1. Debug-APK installieren, Gehäuse koppeln und App öffnen. Modell, Android-Version
   und Gehäusezustand außerhalb des Exports notieren. Geräteliste prüfen.
2. Im Normal-Modus „Neutral“ markieren. Fünf Sekunden nichts drücken.
3. „Links“ markieren, einmal kurz Links drücken und loslassen. Zwei Sekunden
   warten. Fünfmal wiederholen. Danach Links drei Sekunden halten, loslassen
   und drei Sekunden warten. Zum Schluss fünf schnelle einzelne Tastendrücke.
4. Schritt 3 für Rechts, Hoch, Runter und Klick wiederholen. Markierungen immer
   per Touch setzen, damit die Mausereignisse des Gehäuses isoliert bleiben.
5. ZIP exportieren. Export ausdrücklich als „normal“ benennen.
6. Capture aktivieren. Status muss tatsächlich „true“ anzeigen. Schritte 2 bis 5
   wiederholen und Export als „capture“ benennen.
7. Im Capture-Modus Bluetooth-Verbindung trennen und wiederherstellen. Capture-
   und Geräteänderungen beobachten. App kurz verlassen und zurückkehren.
   Erneut eine Taste drücken. Separates ZIP exportieren.
8. Optional am Bildschirmrand normale Mausbewegungen testen. Prüfen, ob weitere
   Tastendrücke noch Events erzeugen. Danach denselben Test in Capture durchführen.

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
beiden Modi, einschließlich Halten und Loslassen. Erst dann das Mapping bestätigen.
Falls Capture scheitert, dokumentieren wir den Fehler und bewerten den normalen
Modus anhand seiner tatsächlich aufgezeichneten Ereignisse.
