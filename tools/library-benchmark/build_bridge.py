# SPDX-License-Identifier: GPL-3.0-only
import halide,subprocess
from pathlib import Path
h=Path(halide.__file__).parent;b=Path(__file__).resolve().parent;r=(b / 'upstream/hdr-plus-master/src');o=b/'generated'
common=['g++','-std=c++20','-O2',f'-I{h}/include']
subprocess.run([*common,f'-I{r}',str(b/'finish_generator.cpp'),str(r/'finish.cpp'),str(r/'util.cpp'),str(h/'lib64/libHalide_GenGen.a'),f'-L{h}/lib64',f'-Wl,-rpath,{h}/lib64','-lHalide','-lpthread','-ldl','-o',str(b/'finish-generator')],check=True)
subprocess.run([str(b/'finish-generator'),'-g','finish_only','-f','sf_finish','-e','static_library,h','-o',str(o),'target=host'],check=True)
subprocess.run([*common,'-shared','-fPIC',f'-I{o}',f'-I{o}',str(b/'bridge.cpp'),str(o/'seafrogs_hdrplus_merge.a'),*[str(o/(n+'.a')) for n in ['sf_hdr_pipeline','sf_finish','sf_motion_fuse','sf_forward','sf_inverse','sf_motion_post']],'-lpthread','-ldl','-o',str(b/'libbench.so')],check=True)
print('BRIDGE_BUILT',flush=True)
