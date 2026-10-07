// SPDX-License-Identifier: GPL-3.0-only
#include <Halide.h>
#include "finish.h"
class FinishOnly : public Halide::Generator<FinishOnly> {
public:
 Input<Halide::Buffer<uint16_t>> input{"input",2};
 Input<uint16_t> black{"black"},white{"white"};
 Input<float> r{"r"},g0{"g0"},g1{"g1"},b{"b"};
 Input<int> cfa{"cfa"}; Input<Halide::Buffer<float>> ccm{"ccm",2};
 Input<float> compression{"compression"},gain{"gain"}; Output<Halide::Buffer<uint8_t>> output{"output",3};
 void generate(){Halide::Func raw=Halide::BoundaryConditions::mirror_interior(input);output=finish(raw,input.width(),input.height(),black,white,CompiletimeWhiteBalance{r,g0,g1,b},cfa,ccm,compression,gain);}
};
HALIDE_REGISTER_GENERATOR(FinishOnly, finish_only)
