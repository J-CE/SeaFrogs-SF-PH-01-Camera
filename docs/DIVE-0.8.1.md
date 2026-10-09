# 0.8.1: Tauchprofil und Mausmodi

Wir bedienen unsere Kamera mit fünf Gehäuseeingaben. Diese Übersicht führen wir für RC1 fort; historische Details zu einzelnen Änderungen bleiben in den jeweiligen Versionsdokumenten.

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

Wir wählen die Maus einmal per Touch aus und speichern sie zusammen mit dem Eingabemodus. Bei Trennung behalten wir die gewünschte Steuerung bei und warten auf Wiederverbindung desselben Descriptors. Wir übernehmen keine unbekannte Maus automatisch; die initiale Mausauswahl bleibt notwendig.

## Setup und Tauchansicht

In unserem Tauchprofil speichern wir App-Helligkeit 1–100 % sowie automatische Ausrichtung oder fest Hochformat/Querformat/umgekehrtes Querformat. Wir halten das Display an und richten das Sensorbild bei fester Ausrichtung entsprechend aus. Wir verändern dafür nur die App-Helligkeit.

Ab RC1 lassen wir Kamera, Zoom, EV, Foto/Video und Auslöser in beiden Mausmodi und bei geöffnetem Setup sichtbar. SETUP bleibt daneben erreichbar; sein Overlay endet oberhalb der Toolbar. Wir blenden Systemleisten aus. Im Setup bearbeiten wir Tauchprofil, format-/belichtungsbezogene Einstellungen, Video-FPS, Weißabgleich und Zoom-/EV-Zyklen und erreichen die Diagnose.

Vor dem Tauchgang bearbeiten wir unsere Zyklen: 2–8 eindeutige Werte, Zoom beginnt mit 1 und liegt zwischen 1 und 10, EV beginnt mit 0 und liegt zwischen −5 und +5. Wir überspringen nicht unterstützte Zoomwerte und runden/begrenzen EV auf Kameraschritte. Macro bleibt bei zwei Stufen.

Status: Foto/Video, Objektiv, Zoom, EV, Fotoformat und Verarbeitungsmodus, WB, Akkustand, freier Speicher, Bereitschaft/REC-Dauer. Kritischer Akku ≤10 %, Speicher unter 256 MiB, thermischer Status ab SEVERE und getrenntes Gehäuse erscheinen groß in Rot. Foto/JPEG prüft jetzt ebenfalls eine Mindestreserve von 80 MiB; RAW und Video behalten ihre bestehenden Reserven. Temperaturwarnung erzwingt keinen Aufnahmeabbruch.

## Weißabgleich und unveränderte Grenzen

Auto und gemeldete Camera2-Presets Tageslicht/Bewölkt/Schatten sind auswählbar und gespeichert. Nicht unterstützte Presets nutzen auf einer anderen Route Auto, der Status zeigt das angewendete Profil. Manuelles Preset schaltet Extensions aus. CameraX Preview/JPEG/Video und der direkte RAW/JPEG-Aufnahmeweg erhalten dasselbe angeforderte WB-Preset; physische Sensorwirkung muss das Gerät bestätigen. RAW enthält weiterhin Sensorwerte mit Metadaten, kein eingebranntes WB-JPEG.

Seit 0.8.2 bieten wir modellbasierte Unterwasserprofile flach (0–8 m), mittel (>8–20 m), tief (>20 m) und DL08-Flutlicht an. Für die UW-Profile wählen wir 25/50/75/100 % Stärke, standardmäßig 50 %. Modellannahmen, Sensorübertragung und Aussagegrenzen beschreiben wir in [WB-0.8.2.md](WB-0.8.2.md). Wir speichern über MediaStore in Pictures/SeaFrogs und Movies/SeaFrogs. Wir nehmen Video ohne Ton mit Auto-Belichtung plus EV auf. ISO-/Digitalgrenzen verwenden wir nur für Foto; RAW, Limits und manuelles WB schließen Extensions aus.

Wir halten die positive Geräterückmeldung zu beiden Mausmodi nach 0.8.10 fest. Macro-Nahfokus hinter dem Gehäusefenster, tatsächliche Videoauflösung/-FPS, Unterwasserfarben und Dauerbetrieb prüfen wir weiterhin in der Praxis. Die Mehrbildentwicklung bleibt eingefroren.
