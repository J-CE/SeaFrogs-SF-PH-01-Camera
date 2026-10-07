# Standalone-Benchmark für HDR+ und MotionCam

Dieser Testaufbau hat GPL-3.0-only, weil er MotionCam-Kerne und Ablaufteile nutzt.
Er gehört nicht zum Android-App-Build. Die ausgelieferte Kamera-App bleibt Apache
2.0 und enthält bisher keine der beiden Engines. HDR+ hat separat MIT-Lizenz.
`prepare.py` lädt fixierte Originalquellen; ihre Lizenztexte bleiben erhalten.
Die Generatorkopien ändern nur Halides alte auto_schedule/get_auto_schedule()-API
in using_autoscheduler(). Originaldateien bleiben unverändert.

Linux x86-64, Python 3.12, g++ 13; konkrete Python-Versionen in requirements.txt.
Auf dem geprüften Host erzeugt build_bridge.py eine native Shared Library.

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

`results` enthält RAW-Zwischenergebnisse als NPY, JPEGs in voller Auflösung,
Metadaten/Zeiten und metrics.json. Fotos bleiben lokal und gehören nicht ins Git.
Synthetische Translationen liegen separat in results-motion-shift. prepare.py
extrahiert nur Bilder und Bericht mit geprüften relativen Pfaden.
Vor einer Qualitätsauswertung ist außerdem tools/validate_library_zip.py auszuführen.

Methodik, Auswahlentscheidung und Grenzen stehen in
../../docs/LIBRARY-RESULTS-2026-10-08.md. Die vier Prozesse verwenden vier Halide-
Threads und dieselben fünf Original-RAWs je Kamera. RAW-Fusion und native Ausgabe-
operatoren laufen tatsächlich; Kamera-/Android-App-Automatik der Bibliotheken
gehört nicht zum Hosttest. Angewendete WB/CCM stammen aus CaptureResult. DNG-
GainMaps fließen in die Ausgabe ein; OpcodeList3-Verzeichnungskorrektur fehlt.
Gemeinsamer Finisher: HDR+ mit compression=1/gain=1. MotionCam-Ausgabe:
shadows=2, übrige feste Einstellungen im Quelltext, chromaEps=0.03.
MotionCam liefert BGR, das JPEG erhält RGB. Chroma-/Tonwert-Autotuning ist offen.

Der Test nutzt die vorhandenen Daten und fordert keinen neuen Gerätetest an.
