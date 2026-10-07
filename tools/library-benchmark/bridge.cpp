// SPDX-License-Identifier: GPL-3.0-only
// MotionCam fusion/wavelet sequencing follows f0enix/motioncam ImageProcessor.cpp.
// HDR+ functions come from timothybrooks/hdr-plus under its MIT license.
#include <HalideBuffer.h>
#include <vector>
#include <cmath>
#include <algorithm>
#include "seafrogs_hdrplus_merge.h"
#include "sf_hdr_pipeline.h"
#include "sf_finish.h"
#include "sf_motion_fuse.h"
#include "sf_forward.h"
#include "sf_inverse.h"
#include "sf_motion_post.h"
using Halide::Runtime::Buffer;
extern "C" int bench_hdr_merge(uint16_t* input,int w,int h,int n,uint16_t* output){
 Buffer<uint16_t> in(input,w,h,n),out(output,w,h);return seafrogs_hdrplus_merge(in,out);
}
extern "C" int bench_finish(uint16_t* input,int w,int h,uint16_t black,uint16_t white,float* wb,int cfa,float* matrix,float compression,float gain,uint8_t* output){
 Buffer<uint16_t> in(input,w,h);Buffer<float> ccm(matrix,3,3);Buffer<uint8_t> out(output,3,w,h);
 return sf_finish(in,black,white,wb[0],wb[1],wb[2],wb[3],cfa,ccm,compression,gain,out);
}
extern "C" int bench_motion_fuse(uint16_t* ref,uint16_t* cur,float* pending,float* flow,int w,int h,int white,float dw){
 Buffer<uint16_t> a(ref,w,h,4),b(cur,w,h,4);Buffer<float> acc(pending,w,h,4);auto f=Buffer<float>::make_interleaved(flow,w,h,2);
 return sf_motion_fuse(a,b,acc,f,w,h,white,400.0f,dw,acc);
}
extern "C" int bench_motion_spatial(uint16_t* input,int w,int h,float weight,uint16_t* output,float* sigmas){
 Buffer<uint16_t> in(input,w,h,4),out(output,w,h,4);
 for(int c=0;c<4;c++){
  std::vector<Buffer<float>> v;int x=w,y=h;for(int level=0;level<6;level++){x/=2;y/=2;v.emplace_back(x,y,4,4);}
  int rc=sf_forward(in,w,h,c,v[0],v[1],v[2],v[3],v[4],v[5]);if(rc)return rc;
  const size_t count=v[0].width()*v[0].height();float* hh=v[0].data()+3*v[0].stride(2);std::vector<float> med(count);
  for(size_t i=0;i<count;i++)med[i]=std::abs(hh[i]);std::nth_element(med.begin(),med.begin()+count/2,med.end());
  float sigma=med[count/2]/0.6745f;sigmas[c]=sigma;auto plane=out.sliced(2,c);
  rc=sf_inverse(v[0],v[1],v[2],v[3],v[4],v[5],weight*sigma,false,1,1.0f,plane);if(rc)return rc;
 }return 0;
}
extern "C" int bench_motion_post(uint16_t* input,int w,int h,int arrangement,float* neutral,float* ctopcs,float* pcstosrgb,float* shading,int sw,int sh,float* settings,uint8_t* output){
 Buffer<uint16_t> in(input,w,h,4);auto a=in.sliced(2,0),b=in.sliced(2,1),c=in.sliced(2,2),d=in.sliced(2,3);
 Buffer<uint16_t> hdr(16,16,3);hdr.fill(0);Buffer<uint8_t> mask(16,16);mask.fill(0);
 Buffer<float> cp(ctopcs,3,3),ps(pcstosrgb,3,3),lsc(shading,sw,sh,4);
 auto s0=lsc.sliced(2,0),s1=lsc.sliced(2,1),s2=lsc.sliced(2,2),s3=lsc.sliced(2,3);
 auto out=Buffer<uint8_t>::make_interleaved(output,w*2,h*2,3);
 return sf_motion_post(a,b,c,d,hdr,mask,1.0f,neutral[0],neutral[1],neutral[2],cp,ps,s0,s1,s2,s3,16384,arrangement,2.2f,settings[0],0.25f,0.0f,0.0f,1.0f,0.5f,10.0f,10.0f,1.05f,settings[1],1.3f,1.25f,settings[2],settings[3],out);
}
