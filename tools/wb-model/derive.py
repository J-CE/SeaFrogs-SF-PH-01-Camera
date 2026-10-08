#!/usr/bin/env python3
"""Reproduce whitepoints.json from versioned spectral inputs. Requires numpy.
Model assumptions belong to docs/WB-0.8.2.md; no measured sea-water calibration.
"""
import hashlib
import json
from pathlib import Path
import tempfile
import urllib.request
import numpy as np

SOURCES = {
    'water.dat': ('https://omlc.org/spectra/water/data/pope97.dat', '5c92c6a1e4be125cc9464fab33490ce4eb883fca7e84a21b0ca2b906cb35c8a7'),
    'd65.csv': ('https://files.cie.co.at/Publications-datasets/CIE_std_illum_D65.csv', 'e76f210bffff3d552ef7113025da5f325d5dfec200dd4b878b1a2f3a507032cb'),
    'xyz.csv': ('https://files.cie.co.at/Publications-datasets/CIE_xyz_1931_2deg.csv', 'fa663e3535a7e0763a745993a1f0a192eb0275ac46ad2d1befd7626841e713c1'),
}

def derive(directory):
    water = []
    for line in (directory/'water.dat').read_text(errors='replace').splitlines():
        try:
            wavelength, coefficient = map(float, line.split())
            water.append((wavelength, coefficient*100))  # 1/cm to 1/m
        except ValueError:
            pass
    water = np.array(water)
    wavelength = np.arange(380, 701)
    absorption = np.interp(wavelength, water[:, 0], water[:, 1])
    daylight = np.loadtxt(directory/'d65.csv', delimiter=',')
    observer = np.loadtxt(directory/'xyz.csv', delimiter=',')
    spectrum = np.interp(wavelength, daylight[:, 0], daylight[:, 1])
    matching = np.array([np.interp(wavelength, observer[:, 0], observer[:, i]) for i in (1, 2, 3)]).T
    def white(power):
        xyz = (power[:, None]*matching).sum(axis=0)
        return (xyz/xyz[1]).tolist()
    profiles = {key: white(spectrum*np.exp(-absorption*path)) for key, path in [('SHALLOW', 5), ('MEDIUM', 15), ('DEEP', 26)]}
    nominal_led = 1/(wavelength.astype(float)**5*np.expm1(1.438776877e7/(wavelength*5000)))
    profiles['DL08'] = white(nominal_led)
    return {'license': 'CC BY-SA 4.0 (derived CIE data)', 'model': 'D65 * exp(-PopeFry absorption * path), 380–700 nm, CIE 1931 2deg',
            'representativeDepthsM': {'SHALLOW': 4, 'MEDIUM': 14, 'DEEP': 25}, 'subjectDistanceM': 1,
            'base': white(spectrum), 'profiles': profiles}

if __name__ == '__main__':
    with tempfile.TemporaryDirectory(prefix='seafrogs-wb-') as temporary:
        directory = Path(temporary)
        for name, (url, expected) in SOURCES.items():
            data = urllib.request.urlopen(url, timeout=30).read()
            if hashlib.sha256(data).hexdigest() != expected:
                raise ValueError('Spectral input changed: '+name)
            (directory/name).write_bytes(data)
        print(json.dumps(derive(directory), indent=2))
