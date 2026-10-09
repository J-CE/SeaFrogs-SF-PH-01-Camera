# Our Android MIT HDR+ kernel

We use the checked-in ARM64 static library and header for normal Android Studio builds.
We install NDK 27.2.12479018 and CMake 3.22.1 when Android Studio requests them.
We intentionally build our app only for arm64-v8a on the Pixel 8.
We retain this experimental backend, while keeping multiframe development frozen since 0.8.

We regenerate the kernel after algorithm changes on a Linux x86-64 host with g++ 13:

```
python -m pip install halide==21.0.0
python tools/native-hdr/generate.py
```

We keep the source algorithm and license in third_party/hdr-plus and generate
for arm-64-android. We use 16 KiB ELF page alignment for the native .so and limit
Halide parallel processing to four threads. We require no MotionCam sources
or code from tools/library-benchmark for generation.

We verify the exact same generator on the host with:

```
python tools/native-hdr/generate.py host /tmp/seafrogs-native-host
g++ -std=c++17 -O2 -shared -fPIC -I/tmp/seafrogs-native-host \
  tools/native-hdr/host_verify.cpp /tmp/seafrogs-native-host/seafrogs_hdr.a \
  -lpthread -ldl -o /tmp/seafrogs-native-host/verify.so
```

Through host_verify.cpp, we expose only the MIT pipeline with the same dimensions
and metadata layout as JNI. We cannot validate Pixel timing, RAM, camera HAL results,
lifecycle or bitmap delivery on Android through this host execution.

We use rawpy, NumPy, Pillow and tifffile for the uploaded-series check. We point
it to extracted input folders LIBRARY_MAIN_RAW_SERIES and LIBRARY_ULTRAWIDE_RAW_SERIES:

```
python tools/native-hdr/verify_uploaded.py --inputs INPUT_FOLDER \
  --report camera-test-report.json --library /tmp/seafrogs-native-host/verify.so \
  --output /tmp/verification
```

We execute each full-resolution pipeline twice and check exact output
repeatability, buffer shape, return status and nonempty image range. We do not
claim a perceptual quality score, and we never include uploaded pictures in git.
