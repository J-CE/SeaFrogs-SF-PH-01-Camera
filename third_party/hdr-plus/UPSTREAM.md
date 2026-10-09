# Our MIT HDR+ core provenance

We use upstream sources from https://github.com/timothybrooks/hdr-plus
at commit ef4dd2ca53a51e105ed923557c726b253f05c13b.
Copyright 2017 Tim Brooks. License: MIT (LICENSE.md).
We retain eight algorithm C++/header files as unmodified upstream copies.
In finish.cpp, we connect chroma denoising, use geometric per-pass tone factors
and bypass tone mapping when compression/gain equal one.
We do not include the upstream RAW containers/LibRaw, batch tooling or CLI.

In our Apache-2.0 generator in tools/native-hdr, we add Camera2 metadata handling,
per-Bayer-position black levels, bilinear lens-shading correction and controlled
tone settings. We swap green WB channels when shifting Bayer rows to RGGB.
We link no MotionCam/GPL source into our Android application.

We include Halide's MIT runtime in the generated archive. We retain the complete
supplied Halide license in app/src/main/assets/licenses/Halide-LICENSE.txt and
package the HDR+ license as app/src/main/assets/licenses/hdr-plus-MIT.txt.

In 0.7.2, we filter chroma after the output CCM in finish.cpp, scale bilateral
variance for 16-bit values, retain float differences and safely preserve
chroma when all neighbors are rejected. In our Apache generator, we preserve
integer headroom by deferring boost/WB/LSC normalization gain to the CCM.
We keep this experimental backend in the source tree; multiframe development
has remained frozen since 0.8.
