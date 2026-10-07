# Android MIT HDR+ kernel

Normal Android Studio builds use the checked-in ARM64 static library and header.
Install NDK 27.2.12479018 and CMake 3.22.1 when Android Studio requests them.
The app intentionally builds only arm64-v8a for the Pixel 8.

To regenerate after changing the algorithm (Linux x86-64 host, g++ 13):

```
python -m pip install halide==21.0.0
python tools/native-hdr/generate.py
```

Source algorithm and license: third_party/hdr-plus. Generated target:
arm-64-android. Native .so uses 16 KiB ELF page alignment. Halide limits its
parallel processing to four threads. Generation requires no MotionCam sources
or code from tools/library-benchmark.

For host verification of the exact same generator:

```
python tools/native-hdr/generate.py host /tmp/seafrogs-native-host
g++ -std=c++17 -O2 -shared -fPIC -I/tmp/seafrogs-native-host \
  tools/native-hdr/host_verify.cpp /tmp/seafrogs-native-host/seafrogs_hdr.a \
  -lpthread -ldl -o /tmp/seafrogs-native-host/verify.so
```

host_verify.cpp exposes only the MIT pipeline with the same dimensions and
metadata layout as JNI. This host execution cannot validate Pixel timing, RAM,
camera HAL results, lifecycle or bitmap delivery on Android.

The uploaded-series check uses rawpy, NumPy, Pillow and tifffile. Point it to
extracted input folders LIBRARY_MAIN_RAW_SERIES and LIBRARY_ULTRAWIDE_RAW_SERIES:

```
python tools/native-hdr/verify_uploaded.py --inputs INPUT_FOLDER \
  --report camera-test-report.json --library /tmp/seafrogs-native-host/verify.so \
  --output /tmp/verification
```

It executes each full-resolution pipeline twice and checks exact output
repeatability, buffer shape, return status and nonempty image range. It does not
claim a perceptual quality score. Uploaded pictures are never included in git.
