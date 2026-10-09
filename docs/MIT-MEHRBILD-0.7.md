# MIT-Mehrbildmodus 0.7.0

Wir dokumentieren hier unseren experimentellen MIT-Mehrbildmodus aus 0.7.0.

Wir bewahren diesen Ablauf als historischen Versionsstand. In RC1 sind die damaligen Test-/Exportbuttons teilweise entfernt; für die heutige Bedienung verwenden wir [DIVE-0.8.1.md](DIVE-0.8.1.md) und [RC1.md](RC1.md).

## Ein einfacher Gerätetest

1. Wir installieren die APK als Update. Wir stützen das Handy ab und stellen ein Buch oder feinen Druck
   ungefähr 50 cm entfernt bei gleichbleibendem Licht auf.
2. Wir drücken **MEHRBILD TEST**, dann START und warten. Wir betätigen während
   des Tests keine Gehäusetasten. Unsere App prüft MAIN, UW und MAIN mit ISO 800 automatisch.
3. Wir drücken **TEST ZIP**, wählen das Ziel aus, warten auf **Export gespeichert** und
   laden die ZIP hoch. Es enthält maximal sechs JPEGs plus Messdaten, keine DNGs.

Die JPEG-Paare heißen *_standard.jpg und *_mehrbild.jpg. STANDARD ist das
erste JPEG aus derselben fixierten Aufnahme. MEHRBILD verarbeitet die fünf
RAW-Sensorframes. Ein Rückfall ist FAILED im Bericht, keine erfolgreiche Fusion.
Das Referenz-JPEG bleibt auch dann gespeichert. Keine Qualitätsnote wird erfunden.

## Freie Aufnahmen

Über die vorhandene Qualitätstaste wählen wir MEHRBILD; STANDARD bleibt der Start-
Standard bis zur Geräteabnahme. Ein Auslösen nimmt fünf Bilder auf, danach folgt
Verarbeitung. Währenddessen ist die Vorschau pausiert und die Eingabe gesperrt.
Wir halten das Smartphone ruhig. Bei Erfolg liegen normales JPEG und *_MEHRBILD.jpg in
Pictures/SeaFrogs. RAW+JPEG speichert zusätzlich das erste unveränderte DNG.
Unsere App behält keine fünf DNGs pro freiem Foto. Der Modus ist auch für Macro/UW
vorgesehen; echter Macro-Nahfokus muss gesondert am nahen Motiv bestätigt werden.

LEFT: MAIN → MACRO → UW. UP: MAIN 1/1,5/3/5×, UW 1/1,5/2/3×, Macro 0,5×/1×
Crop. RIGHT: EV 0/+1/+2/−1/−2. CLICK: Foto. DOWN: weiterhin Diagnose, kein Video.
Keine neuen Doppeltasten. Bestehende Tastenbelegung/Entprellung bleiben erhalten.

## Verarbeitung und Grenzen

Tim Brooks' MIT HDR+ align/merge/finish, eigener Android-Adapter. Fünf gleich
belichtete Frames, ISO/Zeit/WB/Fokus fixiert und je Aufnahme anhand der echten
physischen CaptureResults geprüft. RAW-Pixel werden unter Beachtung von
rowStride/pixelStride kopiert. Maximal fünf Frames, höchstens 13 MP, mindestens
700 MiB verfügbarer Systemspeicher vor Pufferallocation. Speichermangel wird
als Rückfall gemeldet; das ist noch keine gemessene Android-Speicherverbrauchszahl.

Schwarzpegel, Weißpegel, CFA, WB-Gains, Farbmatrix, post-RAW gain und LensShadingMap
kommen aus Result/Characteristics. Fehlende/ungültige Werte stoppen die Fusion.
Objektivkorrektur wird bilinear über die aktive Sensorfläche angewendet.
Tonwerte: compression=1, gain=1; aktuelle post-RAW-Verstärkung separat.
EXIF übernimmt vorhandene ISO/Zeit/Blende/Brennweite vom Referenz-JPEG; UserComment
belegt den Mehrbildweg, Zeitstempel, Ergebnis/Rückfall und Ausgabeabmessungen.

Digitaler Zoom wird nach der Fusion einmal gecroppt, ohne Hochskalierung. Höhere
Zoomstufen liefern entsprechend weniger echte Pixel. Keine 50-MP-Freigabe.
Keine Rekonstruktion ausgebrannter Highlights durch Belichtungsreihen, keine
proprietäre Google-Pipeline. UW-Verzeichnungskorrektur und bewegte Unterwasser-
Motive sind noch nicht abgenommen. Bewegungsartefakte bleiben ein Testpunkt.

Als nächsten Meilenstein werten wir das echte Pixel-8-Ergebnis dieses kleinen Tests aus:
nativeFusionApplied, Sensor/Metadatenzuordnung, ISO-Grenze, Aufnahme-/Verarbeitungs-
dauer, Ausgabefarbe, Auflösung, RAM/Temperatur. Danach Nahfokus und bewegtes Motiv.

Android-Metadatenreferenz: https://developer.android.com/reference/android/hardware/camera2/CaptureResult
