// SPDX-License-Identifier: Apache-2.0
#include <jni.h>
#include <android/bitmap.h>
#include <cstdint>
#include <vector>
#include <mutex>
#include <stdexcept>
#include <string>
#include "seafrogs_hdr.h"
namespace {
std::mutex processing;
thread_local std::string last_error;
void record_error(void *, const char *message) { last_error = message ? message : "Halide error"; }
struct FloatArray {
    JNIEnv *env; jfloatArray array; float *data;
    FloatArray(JNIEnv *e, jfloatArray a, int count):env(e),array(a),data(nullptr) {
        if (!a || e->GetArrayLength(a) != count) throw std::runtime_error("Invalid metadata array");
        data = e->GetFloatArrayElements(a,nullptr);
        if (!data) throw std::runtime_error("Cannot access metadata");
    }
    ~FloatArray() { if(data) env->ReleaseFloatArrayElements(array,data,JNI_ABORT); }
};
halide_buffer_t buffer(void *data, halide_type_t type, int count, halide_dimension_t *dims) {
    halide_buffer_t b{}; b.host = static_cast<uint8_t *>(data); b.type=type; b.dimensions=count; b.dim=dims; return b;
}
}
extern "C" JNIEXPORT void JNICALL
Java_de_jce_seafrogs_NativeHdr_process(JNIEnv *env, jobject, jobject raw, jint w, jint h, jint frames,
    jfloatArray blacks, jint white, jfloatArray shading, jint sw, jint sh, jfloatArray active,
    jfloatArray gains, jint cfa, jfloatArray matrix, jfloat boost, jobject bitmap) {
    std::lock_guard<std::mutex> lock(processing);
    void *pixels=nullptr;
    try {
        if(w<64 || h<64 || w%2 || h%2 || int64_t(w)*h>13000000 || frames!=5 || sw<1 || sh<1 || sw>128 || sh>128)
            throw std::runtime_error("Invalid HDR dimensions");
        auto *samples=env->GetDirectBufferAddress(raw);
        if(!samples || env->GetDirectBufferCapacity(raw)!=int64_t(w)*h*frames*2)
            throw std::runtime_error("Invalid RAW buffer size");
        FloatArray black(env,blacks,4), lsc(env,shading,sw*sh*4), rect(env,active,4), wb(env,gains,4), ccm(env,matrix,9);
        AndroidBitmapInfo info{};
        if(AndroidBitmap_getInfo(env,bitmap,&info)!=ANDROID_BITMAP_RESULT_SUCCESS || info.format!=ANDROID_BITMAP_FORMAT_RGBA_8888 || info.width!=unsigned(w) || info.height!=unsigned(h))
            throw std::runtime_error("Invalid output bitmap");
        std::vector<uint8_t> rgb(size_t(w)*h*3);
        halide_dimension_t rd[]={{0,w,1,0},{0,h,w,0},{0,frames,w*h,0}};
        halide_dimension_t bd[]={{0,4,1,0}};
        halide_dimension_t sd[]={{0,sw,1,0},{0,sh,sw,0},{0,4,sw*sh,0}};
        halide_dimension_t md[]={{0,3,1,0},{0,3,3,0}};
        halide_dimension_t od[]={{0,3,1,0},{0,w,3,0},{0,h,w*3,0}};
        auto r=buffer(samples,{halide_type_uint,16,1},3,rd);
        auto b=buffer(black.data,{halide_type_float,32,1},1,bd);
        auto s=buffer(lsc.data,{halide_type_float,32,1},3,sd);
        auto g=buffer(wb.data,{halide_type_float,32,1},1,bd);
        auto m=buffer(ccm.data,{halide_type_float,32,1},2,md);
        auto o=buffer(rgb.data(),{halide_type_uint,8,1},3,od);
        last_error.clear(); halide_set_error_handler(record_error); halide_set_num_threads(4);
        int result=seafrogs_hdr(&r,&b,uint16_t(white),&s,rect.data[0],rect.data[1],rect.data[2],rect.data[3],&g,cfa,&m,boost,&o);
        if(result) throw std::runtime_error(last_error.empty()?"HDR processing failed":last_error);
        if(AndroidBitmap_lockPixels(env,bitmap,&pixels)!=ANDROID_BITMAP_RESULT_SUCCESS) throw std::runtime_error("Cannot lock bitmap");
        for(int y=0;y<h;++y) {
            auto *line=static_cast<uint8_t *>(pixels)+size_t(y)*info.stride;
            for(int x=0;x<w;++x) {
                size_t i=(size_t(y)*w+x)*3;
                line[x*4]=rgb[i]; line[x*4+1]=rgb[i+1]; line[x*4+2]=rgb[i+2]; line[x*4+3]=255;
            }
        }
        AndroidBitmap_unlockPixels(env,bitmap); pixels=nullptr;
    } catch(const std::exception &error) {
        if(pixels) AndroidBitmap_unlockPixels(env,bitmap);
        if(!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),error.what());
    }
}
