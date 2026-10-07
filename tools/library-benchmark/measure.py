# SPDX-License-Identifier: GPL-3.0-only
from pathlib import Path
import rawpy,numpy as np,cv2,json
b=Path(__file__).resolve().parent;out=b/'results';all_results={}
for lens in ['MAIN','ULTRAWIDE']:
 folder=b/'inputs'/('LIBRARY_'+lens+'_RAW_SERIES')
 with rawpy.imread(str(folder/'frame_00.dng')) as r:
  ref=r.raw_image.copy().astype('f4');pattern=r.raw_pattern.copy()
 variants={'SINGLE':ref,'HDR':np.load(out/(lens+'_HDR_RAW.npy')).astype('f4'),'MOTION_TEMPORAL':np.load(out/(lens+'_MOTION_TEMPORAL.npy')).astype('f4'),'MOTION_SPATIAL':np.load(out/(lens+'_MOTION_RAW.npy')).astype('f4')}
 stack=[]
 for p in sorted(folder.glob('*.dng')):
  with rawpy.imread(str(p)) as r:stack.append(r.raw_image.copy().astype('f4'))
 median=np.median(np.stack(stack),axis=0);gy,gx=np.argwhere(pattern==1)[0]
 def plane(a):return a[gy::2,gx::2]
 med=plane(median);single=plane(ref);h,w=med.shape
 # Choose homogeneous tiles using only median input frames, not any candidate output.
 candidates=[]
 for y in range(80,h-80-64,64):
  for x in range(80,w-80-64,64):
   p=med[y:y+64,x:x+64];mean=float(p.mean())
   if mean<85 or mean>750:continue
   residual=p-cv2.GaussianBlur(p,(0,0),3)
   candidates.append((float(np.std(residual)),x,y))
 chosen=sorted(candidates)[:32]
 output={'method':'Median high-pass standard deviation in 32 independently selected flat green-plane tiles; strong-edge gradient ratio relative to raw reference; not ground-truth SNR/MTF','tiles':[[x,y,64,64] for _,x,y in chosen],'metrics':{}}
 smooth=cv2.GaussianBlur(single,(0,0),.7)
 gref=np.hypot(cv2.Sobel(smooth,cv2.CV_32F,1,0),cv2.Sobel(smooth,cv2.CV_32F,0,1));mask=gref>np.percentile(gref,98);mask[:80]=False;mask[-80:]=False;mask[:,:80]=False;mask[:,-80:]=False
 for name,a in variants.items():
  p=plane(a);values=[]
  for _,x,y in chosen:
   tile=p[y:y+64,x:x+64];values.append(float(np.std(tile-cv2.GaussianBlur(tile,(0,0),3))))
  sm=cv2.GaussianBlur(p,(0,0),.7);g=np.hypot(cv2.Sobel(sm,cv2.CV_32F,1,0),cv2.Sobel(sm,cv2.CV_32F,0,1))
  output['metrics'][name]={'flatResidualStdDN':float(np.median(values)),'strongEdgeGradientRelative':float(g[mask].mean()/gref[mask].mean()),'meanBiasDN':float(np.median(p-single))}
 base=output['metrics']['SINGLE']['flatResidualStdDN']
 for m in output['metrics'].values():m['flatResidualReductionPercent']=100*(1-m['flatResidualStdDN']/base)
 all_results[lens]=output
 print(lens,json.dumps(output['metrics']))
(out/'metrics.json').write_text(json.dumps(all_results,indent=2))
