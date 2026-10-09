# Standalone-Benchmark für HDR+ und MotionCam

Wir stellen diesen Testaufbau unter GPL-3.0-only, weil wir MotionCam-Kerne und Ablaufteile nutzen.
Wir halten ihn getrennt vom Android-App-Build. Unsere ausgelieferte Kamera-App bleibt Apache
2.0; seit 0.7 enthält sie den MIT-HDR+-Kern, aber keinen MotionCam-Code. HDR+ hat separat MIT-Lizenz.
Wir bewahren den Hostvergleich als Experiment; die Mehrbildentwicklung bleibt seit 0.8 eingefroren.
`prepare.py` lädt fixierte Originalquellen; ihre Lizenztexte bleiben erhalten.
Die Generatorkopien ändern nur Halides alte auto_schedule/get_auto_schedule()-API
in using_autoscheduler(). Originaldateien bleiben unverändert.

Linux x86-64, Python 3.12, g++ 13; konkrete Python-Versionen in requirements.txt.
Auf dem geprüften Host erzeugen wir mit build_bridge.py eine native Shared Library.

```bash
cd tools/library-benchmark
python -m pip install -r requirements.txt
python prepare.py /path/to/seafrogs-library-1791409700620.zip
python build_generators.py
python generate.py
python build_bridge.py
python run_bench.py MAIN HDR
python run_bench.py MAIN MOTION
python run_bench.py ULTRAWIDE HDR
python run_bench.py ULTRAWIDE MOTION
python measure.py
python run_bench.py MAIN HDR --motion-shift
python run_bench.py MAIN MOTION --motion-shift
```

In `results` speichern wir RAW-Zwischenergebnisse als NPY, JPEGs in voller Auflösung,
Metadaten/Zeiten und metrics.json. Wir halten Fotos lokal und nehmen sie nicht ins Git auf.
Synthetische Translationen liegen separat in results-motion-shift. prepare.py
extrahiert nur Bilder und Bericht mit geprüften relativen Pfaden.
Vor einer Qualitätsauswertung führen wir außerdem tools/validate_library_zip.py aus.

Unsere Methodik, Auswahlentscheidung und Grenzen dokumentieren wir in
[LIBRARY-RESULTS-2026-10-08.md](../../docs/LIBRARY-RESULTS-2026-10-08.md). Die vier Prozesse verwenden vier Halide-
Threads und dieselben fünf Original-RAWs je Kamera. RAW-Fusion und native Ausgabe-
operatoren laufen tatsächlich; Kamera-/Android-App-Automatik der Bibliotheken
gehört nicht zum Hosttest. Angewendete WB/CCM stammen aus CaptureResult. DNG-
GainMaps fließen in die Ausgabe ein; OpcodeList3-Verzeichnungskorrektur fehlt.
Gemeinsamer Finisher: HDR+ mit compression=1/gain=1. MotionCam-Ausgabe:
shadows=2, übrige feste Einstellungen im Quelltext, chromaEps=0.03.
MotionCam liefert BGR, das JPEG erhält RGB. Chroma-/Tonwert-Autotuning ist offen.

Wir nutzen für diesen Test die vorhandenen Daten und fordern keinen neuen Gerätetest an.
