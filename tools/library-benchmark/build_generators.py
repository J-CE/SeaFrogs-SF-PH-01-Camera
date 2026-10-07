# SPDX-License-Identifier: GPL-3.0-only
from pathlib import Path
import subprocess,halide,concurrent.futures,re
h=Path(halide.__file__).parent
base=(Path(__file__).resolve().parent / 'upstream'); bench=Path(__file__).resolve().parent
(bench/'generators').mkdir(exist_ok=True)
def compile_one(name,srcs,inc):
 cmd=['g++','-std=c++20','-O2',f'-I{h}/include',f'-I{inc}',*[str(p) for p in srcs],str(h/'lib64/libHalide_GenGen.a'),f'-L{h}/lib64',f'-Wl,-rpath,{h}/lib64','-lHalide','-lpthread','-ldl','-o',str(bench/name)]
 with (bench/(name+'.log')).open('w') as log:r=subprocess.run(cmd,stdout=log,stderr=log)
 print(name,r.returncode,flush=True)
 return r.returncode
m=base/'motioncam-main/libMotionCam/libMotionCam/generators'
for n in ['DenoiseGenerator.cpp','PostProcessGenerator.cpp']:
 s=(m/n).read_text().replace('get_auto_schedule()','using_autoscheduler()')
 s=re.sub(r'\bauto_schedule\b','using_autoscheduler()',s)
 (bench/'generators'/n).write_text(s)
r=base/'hdr-plus-master/src'
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
 jobs=[pool.submit(compile_one,'hdr-merge-generator',[r/n for n in ['align_and_merge_generator.cpp','align.cpp','merge.cpp','util.cpp']],r),pool.submit(compile_one,'hdr-generator',[r/n for n in ['hdrplus_pipeline_generator.cpp','align.cpp','merge.cpp','finish.cpp','util.cpp']],r),pool.submit(compile_one,'motion-denoise-generator',[bench/'generators/DenoiseGenerator.cpp'],m),pool.submit(compile_one,'motion-post-generator',[bench/'generators/PostProcessGenerator.cpp'],m)]
 results=[j.result() for j in jobs]
 print('returncodes',results,flush=True)
 if any(results):raise RuntimeError('Native generator compilation failed')
