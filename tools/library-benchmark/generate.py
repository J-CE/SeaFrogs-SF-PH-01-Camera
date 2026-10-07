# SPDX-License-Identifier: GPL-3.0-only
from pathlib import Path
import subprocess,concurrent.futures
b=Path(__file__).resolve().parent;o=b/'generated'
o.mkdir(exist_ok=True)
jobs=[('hdr-merge-generator','align_and_merge','seafrogs_hdrplus_merge',[]),('hdr-generator','hdrplus_pipeline','sf_hdr_pipeline',[]),('motion-denoise-generator','denoise_generator','sf_motion_fuse',['input0.type=uint16','input1.type=uint16','pendingOutput.type=float32','output.type=float32']),('motion-denoise-generator','forward_transform_generator','sf_forward',['input.type=uint16','levels=6']),('motion-denoise-generator','inverse_transform_generator','sf_inverse',['input.size=6']),('motion-post-generator','postprocess_generator','sf_motion_post',[])]
def run(j):
 exe,g,f,params=j
 cmd=[str(b/exe),'-g',g,'-f',f,'-e','static_library,h','-o',str(o),'target=host',*params]
 with (b/(f+'-generate.log')).open('w') as log:r=subprocess.run(cmd,stdout=log,stderr=log)
 print(f,r.returncode,flush=True)
 if r.returncode:raise subprocess.CalledProcessError(r.returncode,cmd)
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as p:
 list(p.map(run,jobs))
