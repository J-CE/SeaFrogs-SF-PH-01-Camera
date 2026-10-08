# 0.8.1: Tauchprofil und Mausmodi

## Verbindliche Bedienung

| Eingabe | Wirkung |
|---|---|
| Links | Hauptkamera → Macro → UW |
| Hoch | Hauptkamera 1/1,5/3/5; UW 1/1,5/3; Macro Sensorfaktor 1/2, Anzeige 0,5×/1× Crop |
| Rechts | EV 0/+1/+2/−1/−2 |
| Runter | Foto/Video wechseln, gesperrt bei REC |
| Klick | Foto, Video Start/Stop |
| Links + Hoch | Kamerasteuerung aktivieren |
| Rechts + Runter | Klassische Maus aktivieren |

Ab 0.8.9 gelten die beiden Umschaltgesten sofort beim Empfang beider Richtungen, unabhängig von deren Reihenfolge. Restereignisse derselben Kombination bleiben bis 150 ms Ruhe gesperrt. Sie lösen keine Kamerabefehle aus. Gegensätzliche Richtungen bleiben ohne App-Funktion. Das Protokoll liefert kein Richtungs-Key-up: sehr schnell aufeinanderfolgende Einzelrichtungen können daher wie eine Kombination aussehen. Im klassischen Modus muss Android die Bewegung an das App-Fenster liefern; relative Achsen werden bevorzugt, sonst Positionsdifferenzen. Ohne relative Achsen können Cursor-Ränder eine Richtung abschneiden. Kein Warp, keine globalen Eingriffe und keine garantierte Kombinationserkennung am Bildschirmrand. Touch im SETUP bleibt als Rückweg verfügbar.

Die einmal per Touch gewählte Maus und der Eingabemodus bleiben gespeichert. Bei Trennung bleibt die gewünschte Steuerung aktiv und wartet auf Wiederverbindung desselben Descriptors. Die App übernimmt keine unbekannte Maus automatisch. Initiale Mausauswahl bleibt notwendig.

## Setup und Tauchansicht

Gespeichertes Tauchprofil: App-Helligkeit 1–100 %, automatische Ausrichtung oder fest Hochformat/Querformat/umgekehrtes Querformat. Display bleibt an. Sensorbildrotation folgt bei fester Ausrichtung der Einstellung. Kein System-Helligkeitswechsel.

Ab RC1 bleiben Kamera, Zoom, EV, Foto/Video und Auslöser in beiden Mausmodi und bei geöffnetem Setup sichtbar. SETUP bleibt daneben erreichbar; sein Overlay endet oberhalb der Toolbar. Systemleisten bleiben ausgeblendet. Setup enthält Tauchprofil, format-/belichtungsbezogene Einstellungen, Video-FPS, Weißabgleich, Zoom-/EV-Zyklen und Diagnose.

Zyklen sind vor dem Tauchgang editierbar: 2–8 eindeutige Werte, Zoom beginnt mit 1 und liegt zwischen 1 und 10, EV beginnt mit 0 und liegt zwischen −5 und +5. Der Controller überspringt nicht unterstützte Zoomwerte und rundet/begrenzt EV auf Kameraschritte. Macro bleibt bei zwei Stufen.

Status: Foto/Video, Objektiv, Zoom, EV, Fotoformat und Verarbeitungsmodus, WB, Akkustand, freier Speicher, Bereitschaft/REC-Dauer. Kritischer Akku ≤10 %, Speicher unter 256 MiB, thermischer Status ab SEVERE und getrenntes Gehäuse erscheinen groß in Rot. Foto/JPEG prüft jetzt ebenfalls eine Mindestreserve von 80 MiB; RAW und Video behalten ihre bestehenden Reserven. Temperaturwarnung erzwingt keinen Aufnahmeabbruch.

## Weißabgleich und unveränderte Grenzen

Auto und gemeldete Camera2-Presets Tageslicht/Bewölkt/Schatten sind auswählbar und gespeichert. Nicht unterstützte Presets nutzen auf einer anderen Route Auto, der Status zeigt das angewendete Profil. Manuelles Preset schaltet Extensions aus. CameraX Preview/JPEG/Video und der direkte RAW/JPEG-Aufnahmeweg erhalten dasselbe angeforderte WB-Preset; physische Sensorwirkung muss das Gerät bestätigen. RAW enthält weiterhin Sensorwerte mit Metadaten, kein eingebranntes WB-JPEG.

Unterwasser flach/mittel/tief und Videolicht sind **noch nicht definiert oder implementiert**. Es gibt keine erfundenen Tiefen-/Kelvinwerte. Speicherziel bleibt fest MediaStore: Pictures/SeaFrogs und Movies/SeaFrogs. Video bleibt ohne Ton, mit Auto-Belichtung plus EV. ISO-/Digitalgrenzen gelten nur für Foto; RAW, Limits und manuelles WB schließen Extensions aus.

Macro-Nahfokus, Videoauflösung/-FPS und Mausmodi benötigen spätere Geräteabnahme. Keine neue Mehrbildentwicklung und kein angeforderter Geräte-/ZIP-Test für diesen Meilenstein.
