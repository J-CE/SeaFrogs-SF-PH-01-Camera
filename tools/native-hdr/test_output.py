# SPDX-License-Identifier: Apache-2.0
import argparse
import ctypes as c,numpy as np,json
p = argparse.ArgumentParser(); p.add_argument('--library', required=True); args = p.parse_args()
l=c.CDLL(args.library);P=c.c_void_p;I=c.c_int;l.verify.argtypes=[P,I,I,P,c.c_uint16,P,I,I,P,P,I,P,c.c_float,P]
def render(rgb,wb,m,boost=1):
 w=h=256;a=np.zeros((5,h,w),dtype='u2');a[:,::2,::2]=round(rgb[0]*10000);a[:,::2,1::2]=round(rgb[1]*10000);a[:,1::2,::2]=round(rgb[1]*10000);a[:,1::2,1::2]=round(rgb[2]*10000)
 black=np.zeros(4,'f4');sh=np.ones(4,'f4');active=np.array([0,0,w,h],'f4');g=np.array(wb,'f4');m=np.array(m,'f4');o=np.empty((h,w,3),'u1')
 rc=l.verify(a.ctypes.data,w,h,black.ctypes.data,10000,sh.ctypes.data,1,1,active.ctypes.data,g.ctypes.data,1,m.ctypes.data,boost,o.ctypes.data);assert rc==0
 return o[64:-64,64:-64].astype('i4')
i=np.eye(3).ravel();a=render([.2,.2,.2],[1,1,1,1],i);assert (a[:,:,0]==a[:,:,1]).all() and (a[:,:,1]==a[:,:,2]).all()
b=render([.8,.6,.2],[1.2,1,1,3],np.diag([.25,.5,.5]).ravel(),2);ref=render([.48,.6,.6],[1,1,1,1],i);delta=int(np.max(np.abs(b-ref)));assert delta<=2,(delta,b[0,0],ref[0,0]);print(json.dumps({'neutralRgb':a[0,0].tolist(),'headroomActualRgb':b[0,0].tolist(),'headroomReferenceRgb':ref[0,0].tolist(),'maxError':delta,'passed':True}))
