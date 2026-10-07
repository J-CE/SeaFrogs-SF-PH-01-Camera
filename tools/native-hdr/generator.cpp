// SPDX-License-Identifier: Apache-2.0
// Android adapter for the MIT-licensed Tim Brooks HDR+ algorithms.
#include <Halide.h>
#include "align.h"
#include "merge.h"
#include "finish.h"
using namespace Halide;
class SeaFrogsHdr : public Generator<SeaFrogsHdr> {
public:
    Input<Buffer<uint16_t>> raw{"raw", 3};
    Input<Buffer<float>> black{"black", 1}; // Bayer parity, not color order
    Input<uint16_t> white{"white"};
    Input<Buffer<float>> shading{"shading", 3}; // x, y, Bayer parity
    Input<float> active_left{"active_left"}, active_top{"active_top"};
    Input<float> active_width{"active_width"}, active_height{"active_height"};
    Input<Buffer<float>> wb{"wb", 1}; // R, G-even, G-odd, B
    Input<int> cfa{"cfa"};
    Input<Buffer<float>> matrix{"matrix", 2};
    Input<float> boost{"boost"};
    Output<Buffer<uint8_t>> output{"output", 3};
    void generate() {
        Var x, y;
        Func alignment = align(raw, raw.width(), raw.height());
        Func merged = merge(raw, raw.width(), raw.height(), raw.dim(2).extent(), alignment);
        Expr parity = (x & 1) + 2 * (y & 1);
        Expr gx = clamp((cast<float>(x) - active_left) / max(active_width - 1.f, 1.f), 0.f, 1.f) * (shading.dim(0).extent() - 1);
        Expr gy = clamp((cast<float>(y) - active_top) / max(active_height - 1.f, 1.f), 0.f, 1.f) * (shading.dim(1).extent() - 1);
        Expr x0 = cast<int>(floor(gx)), y0 = cast<int>(floor(gy));
        Expr x1 = min(x0 + 1, shading.dim(0).extent() - 1), y1 = min(y0 + 1, shading.dim(1).extent() - 1);
        Expr correction = lerp(lerp(shading(x0, y0, parity), shading(x1, y0, parity), gx - x0),
                               lerp(shading(x0, y1, parity), shading(x1, y1, parity), gx - x0), gy - y0);
        Func corrected("corrected");
        corrected(x,y) = cast<uint16_t>(clamp((cast<float>(merged(x,y)) - black(parity)) * correction * boost *
                                            65535.f / (white - black(parity)), 0.f, 65535.f));
        corrected.compute_root().parallel(y).vectorize(x,16);
        Expr shifted_rows = cfa == 3 || cfa == 4;
        CompiletimeWhiteBalance balance{wb(0),select(shifted_rows,wb(2),wb(1)),select(shifted_rows,wb(1),wb(2)),wb(3)};
        // Controlled tone curve: no aggressive shadow amplification.
        output = finish(corrected, raw.width(), raw.height(), 0, 65535, balance, cfa, matrix, 1.f, 1.f);
    }
};
HALIDE_REGISTER_GENERATOR(SeaFrogsHdr, seafrogs_hdr)
