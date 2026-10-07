// SPDX-License-Identifier: Apache-2.0
// Host entry point for testing the exact Android generator, without JNI.
#include "seafrogs_hdr.h"
#include <cstdint>
#include <cstdio>
static void error(void *, const char *message) { fprintf(stderr,"%s\n",message); }
static halide_buffer_t buffer(void *data, halide_type_t type, int n, halide_dimension_t *dims) {
    halide_buffer_t b{}; b.host=static_cast<uint8_t *>(data); b.type=type; b.dimensions=n; b.dim=dims; return b;
}
extern "C" int verify(uint16_t *raw,int w,int h,float *black,uint16_t white,float *lsc,int sw,int sh,
    float *active,float *gains,int cfa,float *matrix,float boost,uint8_t *output) {
    halide_dimension_t rd[]={{0,w,1,0},{0,h,w,0},{0,5,w*h,0}}, bd[]={{0,4,1,0}},
        sd[]={{0,sw,1,0},{0,sh,sw,0},{0,4,sw*sh,0}}, md[]={{0,3,1,0},{0,3,3,0}},
        od[]={{0,3,1,0},{0,w,3,0},{0,h,w*3,0}};
    auto r=buffer(raw,{halide_type_uint,16,1},3,rd),b=buffer(black,{halide_type_float,32,1},1,bd),
        s=buffer(lsc,{halide_type_float,32,1},3,sd),g=buffer(gains,{halide_type_float,32,1},1,bd),
        m=buffer(matrix,{halide_type_float,32,1},2,md),o=buffer(output,{halide_type_uint,8,1},3,od);
    halide_set_error_handler(error); halide_set_num_threads(4);
    return seafrogs_hdr(&r,&b,white,&s,active[0],active[1],active[2],active[3],&g,cfa,&m,boost,&o);
}
