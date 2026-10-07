#!/usr/bin/env python3
"""Rebuild checked-in ARM64 AOT kernel; pip install halide==21.0.0 first."""
from pathlib import Path
import subprocess, tempfile, halide
root = Path(__file__).resolve().parents[2]
sdk = Path(halide.__file__).parent
sources = root / 'third_party/hdr-plus'
with tempfile.TemporaryDirectory() as folder:
    generator = Path(folder) / 'generator'
    subprocess.run(['g++', '-std=c++17', '-O2', '-I'+str(sdk/'include'), '-I'+str(sources),
        str(Path(__file__).with_name('generator.cpp')),
        *[str(sources / f'{name}.cpp') for name in ('align','merge','finish','util')],
        str(sdk/'lib64/libHalide_GenGen.a'), '-L'+str(sdk/'lib64'), '-lHalide',
        '-Wl,-rpath,'+str(sdk/'lib64'), '-lpthread', '-ldl', '-o', str(generator)], check=True)
    import sys
    target = sys.argv[1] if len(sys.argv)>1 else 'arm-64-android'
    out = Path(sys.argv[2]) if len(sys.argv)>2 else root/'app/src/main/cpp/generated/arm64-v8a'
    out.mkdir(parents=True, exist_ok=True)
    subprocess.run([str(generator), '-g', 'seafrogs_hdr', '-f', 'seafrogs_hdr', '-e', 'static_library,h',
        '-o', str(out), 'target='+target], check=True)
