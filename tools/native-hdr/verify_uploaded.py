# SPDX-License-Identifier: Apache-2.0
import ctypes as ct, numpy as np, rawpy, tifffile, struct, json, time, hashlib
from pathlib import Path
from PIL import Image
import argparse
parser=argparse.ArgumentParser()
parser.add_argument('--inputs',type=Path,required=True)
parser.add_argument('--report',type=Path,required=True)
parser.add_argument('--library',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True)
args=parser.parse_args();args.output.mkdir(parents=True,exist_ok=True)
lib=ct.CDLL(str(args.library));P=ct.c_void_p;I=ct.c_int;F=ct.c_float
lib.verify.argtypes=[P,I,I,P,ct.c_uint16,P,I,I,P,P,I,P,F,P]
report=json.loads(args.report.read_text())
summary=[]
for lens in ('MAIN','ULTRAWIDE'):
 path=args.inputs/('LIBRARY_'+lens+'_RAW_SERIES')
 frames=[]
 for p in sorted(path.glob('*.dng')):
  with rawpy.imread(str(p)) as r:
   frames.append(r.raw_image.copy()); black=np.array([r.black_level_per_channel[int(k)] for k in r.raw_pattern.ravel()],'f4'); white=r.white_level
   active=np.array([r.sizes.left_margin,r.sizes.top_margin,r.sizes.width,r.sizes.height],'f4')
 raw=np.ascontiguousarray(frames,'u2');n,h,w=raw.shape
 settings=next(s for s in report['results'] if s['id']=='LIBRARY_'+lens+'_RAW_SERIES')['seriesFrames'][0]['capture']['comparisonActualSettings']
 wb=np.array(settings['wbGains'],'f4');ccm=np.array(settings['wbTransform'],'f4')
 with tifffile.TiffFile(path/'frame_00.dng') as t:data=t.pages[0].tags[51009].value
 count=struct.unpack_from('>I',data)[0];pos=4;maps={}
 for i in range(count):
  op,version,flags,size=struct.unpack_from('>IIII',data,pos);pos+=16;p=data[pos:pos+size];pos+=size
  assert op==9
  area=struct.unpack_from('>8I',p);mh,mw=struct.unpack_from('>2I',p,32)
  maps[(area[0]%2)*2+area[1]%2]=np.frombuffer(p[76:],'>f4').astype('f4').reshape(mh,mw)
 lsc=np.ascontiguousarray([maps[i] for i in range(4)],'f4')
 output=np.empty((h,w,3),'u1');call_args=[w,h,black.ctypes.data,white,lsc.ctypes.data,mw,mh,active.ctypes.data,wb.ctypes.data,4 if lens=='MAIN' else 1,ccm.ctypes.data,settings['postRawBoost']/100,output.ctypes.data]
 t=time.perf_counter();rc=lib.verify(raw.ctypes.data,*call_args);elapsed=time.perf_counter()-t
 assert rc==0 and output.std()>10 and np.isfinite(output).all()
 digest=hashlib.sha256(output).hexdigest()
 Image.fromarray(output).resize((816,614)).save(args.output/(lens+'.jpg'))
 rc=lib.verify(raw.ctypes.data,*call_args);assert rc==0 and hashlib.sha256(output).hexdigest()==digest
 summary.append({'lens':lens,'shape':list(output.shape),'returnCode':rc,'repeatedOutputIdentical':True,'hostSeconds':elapsed,'sha256':digest,'medianRgb':np.median(output[::8,::8],axis=(0,1)).tolist(),'blackParity':black.tolist(),'lscSize':[mw,mh],'white':white})
print(json.dumps(summary,indent=2));(args.output/'verification.json').write_text(json.dumps(summary,indent=2))
