# MIT HDR+ core

Upstream: https://github.com/timothybrooks/hdr-plus
Commit: ef4dd2ca53a51e105ed923557c726b253f05c13b
Copyright 2017 Tim Brooks. License: MIT (LICENSE.md).
Eight algorithm C++/header files remain unmodified upstream copies.
finish.cpp has project modifications: connect chroma denoising, use geometric
per-pass tone factors and bypass tone mapping when compression/gain equal one.
RAW containers/LibRaw, batch tooling and CLI are not included.

Our Apache-2.0 generator in tools/native-hdr adds Camera2 metadata handling,
per-Bayer-position black levels, bilinear lens-shading correction and controlled
tone settings. It swaps green WB channels when shifting Bayer rows to RGGB.
No MotionCam/GPL source is linked into the Android application.

The generated archive includes Halide's MIT runtime. Complete supplied Halide
license: app/src/main/assets/licenses/Halide-LICENSE.txt. HDR+ license is also
packaged as app/src/main/assets/licenses/hdr-plus-MIT.txt.
