# SPDX-License-Identifier: GPL-3.0-only
import os
os.environ['HL_NUM_THREADS']='4'
from pathlib import Path
import numpy as np,rawpy,cv2,ctypes,json,time,struct,argparse,resource,tifffile
from PIL import Image
cv2.setNumThreads(4)
b=Path(__file__).resolve().parent;out=b/'results';out.mkdir(exist_ok=True)
lib=ctypes.CDLL(str(b/'libbench.so'))
P=ctypes.c_void_p;I=ctypes.c_int;F=ctypes.c_float;U=ctypes.c_uint16
lib.bench_hdr_merge.argtypes=[P,I,I,I,P]
lib.bench_finish.argtypes=[P,I,I,U,U,P,I,P,F,F,P]
lib.bench_motion_fuse.argtypes=[P,P,P,P,I,I,I,F]
lib.bench_motion_spatial.argtypes=[P,I,I,F,P,P]
lib.bench_motion_post.argtypes=[P,I,I,I,P,P,P,P,I,I,P,P]
def ptr(a):return a.ctypes.data_as(P)
def call(f,*args):
 rc=f(*args)
 if rc:raise RuntimeError(f'{f.__name__} return {rc}')
def planes(raw):return np.ascontiguousarray(np.stack([raw[0::2,0::2],raw[0::2,1::2],raw[1::2,0::2],raw[1::2,1::2]]))
def mosaic(p):
 c,h,w=p.shape;o=np.empty((h*2,w*2),np.float32)
 for i in range(4):o[i//2::2,i%2::2]=p[i]
 return o

def gainmaps(path):
 with tifffile.TiffFile(path) as t:data=t.pages[0].tags[51009].value
 count=struct.unpack_from('>I',data)[0];pos=4;maps={}
 for i in range(count):
  op,version,flags,size=struct.unpack_from('>IIII',data,pos);pos+=16;payload=data[pos:pos+size];pos+=size
  if op!=9:raise ValueError('Unexpected opcode')
  area=struct.unpack_from('>8I',payload);vh=struct.unpack_from('>2I',payload,32);spaces=struct.unpack_from('>4d',payload,40);mp=struct.unpack_from('>I',payload,72)[0]
  if mp!=1:raise ValueError('Expected one gain map plane')
  maps[(area[0]%2,area[1]%2)]={'map':np.frombuffer(payload[76:],'>f4').astype(np.float32).reshape(vh),'spacing':spaces,'area':area}
 return maps

def shading(maps,H,W):
 out=np.empty((4,H//2,W//2),np.float32)
 for y in range(2):
  for x in range(2):
   m=maps[(y,x)];v,h=m['map'].shape;sv,sh,ov,oh=m['spacing']
   # DNG GainMap positions refer to the full-image normalized coordinate system.
   xx=((np.arange(W//2)*2+x)/W-oh)/sh;yy=((np.arange(H//2)*2+y)/H-ov)/sv
   mx,my=np.meshgrid(xx.astype('f4'),yy.astype('f4'));out[y*2+x]=cv2.remap(m['map'],mx,my,cv2.INTER_LINEAR,borderMode=cv2.BORDER_REPLICATE)
 return out

def finish(raw,wb,cfa,ccm,lsc,gain=1.0):
 H,W=raw.shape
 corrected=mosaic(63+np.maximum(planes(raw).astype('f4')-63,0)*lsc)
 corrected=np.ascontiguousarray(np.clip(np.rint(corrected),0,1023).astype('u2'))
 output=np.empty((H,W,3),'u1')
 call(lib.bench_finish,ptr(corrected),W,H,63,1023,ptr(wb),cfa,ptr(ccm),1.0,gain,ptr(output))
 return output

parser=argparse.ArgumentParser();parser.add_argument('lens',choices=['MAIN','ULTRAWIDE']);parser.add_argument('engine',choices=['HDR','MOTION']);parser.add_argument('--motion-shift',action='store_true');args=parser.parse_args()
if args.motion_shift:
 out=b/'results-motion-shift';out.mkdir(exist_ok=True)
lens=args.lens;engine=args.engine;name='LIBRARY_'+lens+'_RAW_SERIES';path=b/'inputs'/name
report=json.loads((b / 'input-report.json').read_text());step=next(x for x in report['results'] if x['id']==name)
settings=step['seriesFrames'][0]['capture']['comparisonActualSettings'];wg=settings['wbGains'];wb=np.array([wg[0],wg[1],wg[2],wg[3]],'f4');ccm=np.array(settings['wbTransform'],'f4').reshape(3,3)
frames=[]
for p in sorted(path.glob('*.dng')):
 with rawpy.imread(str(p)) as r:frames.append(r.raw_image.copy());pattern=r.raw_pattern.copy();desc=r.color_desc;ap=float(Image.open(path/'frame_00.jpg').getexif().get_ifd(34665)[33437])
raws=np.ascontiguousarray(frames,'u2');N,H,W=raws.shape
shifts=[(0,0),(4,0),(0,4),(-4,-4),(12,8)] if args.motion_shift else [(0,0)]*N
if args.motion_shift:
 for i,(dx,dy) in enumerate(shifts):
  raws[i]=cv2.warpAffine(raws[i],np.array([[1,0,dx],[0,1,dy]],'f4'),(W,H),flags=cv2.INTER_NEAREST,borderMode=cv2.BORDER_REFLECT_101)
colors=[[chr(desc[c]) for c in row] for row in pattern];cfa=4 if colors==[['G','B'],['R','G']] else 1;arrangement=2 if cfa==4 else 0
maps=gainmaps(path/'frame_00.dng');lsc=shading(maps,H,W)
meta={'lens':lens,'engine':engine,'inputShape':list(raws.shape),'cfa':colors,'wb':wb.tolist(),'ccm':ccm.tolist(),'threads':4,'halide':__import__('importlib.metadata',fromlist=['version']).version('halide'),'cv2':cv2.__version__,'timings':{},'syntheticShiftsSensorPixels':shifts,'aperture':ap,'settings':settings,'lscRange':[float(lsc.min()),float(lsc.max())]}
t0=time.perf_counter()
if engine=='HDR':
 merged=np.empty((H,W),'u2');call(lib.bench_hdr_merge,ptr(raws),W,H,N,ptr(merged));meta['timings']['mergeSeconds']=time.perf_counter()-t0
else:
 pp=np.stack([planes(r) for r in raws]);hh,ww=H//2,W//2;ph=((hh+63)//64)*64;pw=((ww+63)//64)*64;dy=(ph-hh)//2;dx=(pw-ww)//2
 pp=np.ascontiguousarray(np.pad(pp,((0,0),(0,0),(dy,ph-hh-dy),(dx,pw-ww-dx)),mode='reflect'))
 previews=np.clip(np.power(np.clip((pp.astype('f4').mean(axis=1)-63)/960,0,1),1/2.2)*255,0,255).astype('u1')
 acc=np.zeros(pp[0].shape,'f4');ev=np.log2(ap*ap/(settings['timeNs']/1e9))-np.log2(settings['iso']/100);dw=float(np.clip(-ev+16,1,32));flowmeans=[];flows=0.;fuses=0.
 for i in range(1,N):
  optical=cv2.DISOpticalFlow_create(cv2.DISOPTICAL_FLOW_PRESET_FAST);optical.setPatchSize(16);optical.setPatchStride(8)
  t=time.perf_counter();flow=optical.calc(previews[0],previews[i],None);flows+=time.perf_counter()-t
  flowmeans.append([float(np.median(flow[...,0])),float(np.median(flow[...,1]))])
  t=time.perf_counter();call(lib.bench_motion_fuse,ptr(pp[0]),ptr(pp[i]),ptr(acc),ptr(flow),pw,ph,1023,dw);fuses+=time.perf_counter()-t
 inp=np.clip((acc/(N-1)-63)*(16384/960),0,16384).astype('u2');temporal=inp[:,dy:dy+hh,dx:dx+ww]
 np.save(out/(lens+'_MOTION_TEMPORAL.npy'),mosaic(temporal.astype('f4')*960/16384+63))
 spatial=np.empty_like(inp);sigmas=np.empty(4,'f4');t=time.perf_counter();call(lib.bench_motion_spatial,ptr(inp),pw,ph,1.,ptr(spatial),ptr(sigmas));spatialSeconds=time.perf_counter()-t
 spatialCrop=spatial[:,dy:dy+hh,dx:dx+ww];merged=mosaic(spatialCrop.astype('f4')*960/16384+63)
 meta['timings'].update({'flowSeconds':flows,'fusionSeconds':fuses,'spatialSeconds':spatialSeconds,'mergeSeconds':time.perf_counter()-t0});meta.update({'noiseSigma16k':sigmas.tolist(),'flowMedianPixels':flowmeans,'ev':float(ev),'differenceWeight':dw,'paddedShape':[ph,pw]})
 # Native MotionCam finish uses the same measured Camera2 color transform/WB,
 # expressed in MotionCam's D50 PCS; retain its original tone/demosaic operators.
 sp=np.array([[.4361,.3851,.1431],[.2225,.7169,.0606],[.0139,.0971,.7141]],'f4');sp=np.diag(np.array([.9642,1,.8249])/sp.sum(axis=1))@sp
 ps=np.ascontiguousarray(np.linalg.inv(sp),'f4');cp=np.ascontiguousarray(sp@ccm@np.diag([wb[0],1,wb[3]]),'f4');neutral=np.array([1/wb[0],1,1/wb[3]],'f4')
 # Gain maps are R,G0,G1,B, whereas inputs stay in sensor position order.
 canonical={0:maps[next(k for k in maps if pattern[k]==0)]['map'],1:maps[next(k for k in maps if pattern[k]==1)]['map'],2:maps[next(k for k in maps if pattern[k]==3)]['map'],3:maps[next(k for k in maps if pattern[k]==2)]['map']}
 shmaps=np.ascontiguousarray(np.stack([canonical[i] for i in range(4)]),'f4');sh,sw=shmaps.shape[1:]
 native=np.empty((H,W,3),'u1');postInput=np.ascontiguousarray(spatialCrop);postSettings=np.array([2.,2.5,max(4,1.5*ev+4),.03],'f4')
 t=time.perf_counter();call(lib.bench_motion_post,ptr(postInput),ww,hh,arrangement,ptr(neutral),ptr(cp),ptr(ps),ptr(shmaps),sw,sh,ptr(postSettings),ptr(native));meta['timings']['nativeFinishSeconds']=time.perf_counter()-t
 Image.fromarray(native[...,::-1]).save(out/(lens+'_MOTION_NATIVE.jpg'),quality=98,subsampling=0)
np.save(out/(lens+'_'+engine+'_RAW.npy'),merged)
t=time.perf_counter();rgb=finish(merged,wb,cfa,ccm,lsc);meta['timings']['commonFinishSeconds']=time.perf_counter()-t
Image.fromarray(rgb).save(out/(lens+'_'+engine+'_COMMON.jpg'),quality=98,subsampling=0)
if engine=='HDR':
 t=time.perf_counter();single=finish(raws[0],wb,cfa,ccm,lsc);meta['timings']['singleFinishSeconds']=time.perf_counter()-t;Image.fromarray(single).save(out/(lens+'_SINGLE_COMMON.jpg'),quality=98,subsampling=0)
meta['peakRssMiB']=resource.getrusage(resource.RUSAGE_SELF).ru_maxrss/1024
(out/(lens+'_'+engine+'.json')).write_text(json.dumps(meta,indent=2));print(json.dumps(meta),flush=True)
