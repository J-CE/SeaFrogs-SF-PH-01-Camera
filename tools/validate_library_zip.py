#!/usr/bin/env python3
"""Validate acquisition evidence before any library gets a quality score.

No synthetic frame duplication, JPEG-to-RAW conversion or silent partial-burst
fallback. Original DNGs remain untouched. Requires numpy and rawpy.
"""
import argparse
import hashlib
import json
from pathlib import Path
import tempfile
import zipfile

import numpy as np
import rawpy


def matching_settings(a, b):
    if any(a[k] != b[k] for k in ("iso", "postRawBoost", "noiseReduction", "edge")):
        return False
    if abs(a["timeNs"] - b["timeNs"]) > max(20000, abs(b["timeNs"]) * .001):
        return False
    if abs(a["focusDiopters"] - b["focusDiopters"]) > max(.02, abs(b["focusDiopters"]) * .001):
        return False
    return all(abs(x - y) <= max(.005, abs(y) * .005)
        for key in ("wbGains", "wbTransform") for x, y in zip(a[key], b[key]))


def validate(path):
    summary = {"zip": Path(path).name, "status": "INVALID", "series": [], "errors": []}
    try:
        with zipfile.ZipFile(path) as z:
            if len(z.namelist()) != len(set(z.namelist())):
                raise ValueError("Duplicate ZIP entries")
            if sum(i.file_size for i in z.infolist()) > 2_000_000_000:
                raise ValueError("Unexpected uncompressed size")
            if z.testzip() is not None:
                raise ValueError("CRC failure")
            if json.loads(z.read("export-errors.json")):
                raise ValueError("Export contains errors")
            report = json.loads(z.read("camera-test-report.json"))
            series = [r for r in report["results"] if r.get("group") == "LIBRARY" and r.get("frameCountRequested", 1) > 1]
            if len(series) != 2 or {s["lens"] for s in series} != {"MAIN", "ULTRAWIDE"}:
                raise ValueError("Expected MAIN and ULTRAWIDE series")
            for s in series:
                if s["status"] != "SAVED" or len(s.get("seriesFrames", [])) != 5:
                    raise ValueError(s["id"] + ": incomplete or failed series")
                rows, timestamps, hashes, signatures = [], [], [], []
                with tempfile.TemporaryDirectory() as temp:
                    for index, f in enumerate(s["seriesFrames"]):
                        c = f["capture"]
                        if f["index"] != index or c["seriesFrameIndex"] != index:
                            raise ValueError("Frame index mismatch")
                        if not all(c.get(k) is True for k in ["comparisonVerified", "sameExposureVerified", "exposureFrozenVerified", "whiteBalanceFrozenVerified", "focusFrozenVerified"]):
                            raise ValueError("Unverified frame settings")
                        t = c["sensorTimestampNs"]
                        if t != c["rawTimestampNs"] or t != c["jpegTimestampNs"]:
                            raise ValueError("RAW/JPEG/result timestamp mismatch")
                        timestamps.append(t)
                        settings = c["comparisonActualSettings"]
                        if rows and not matching_settings(settings, rows[0]["settings"]):
                            raise ValueError("Applied settings differ within series")
                        name = f"photos/{s['id']}/frame_{index:02d}.dng"
                        payload = z.read(name)
                        digest = hashlib.sha256(payload).hexdigest()
                        rawpath = Path(temp) / f"frame_{index:02d}.dng"
                        rawpath.write_bytes(payload)
                        with rawpy.imread(str(rawpath)) as raw:
                            pixels = raw.raw_image.copy()
                            if pixels.shape != (c["rawHeight"], c["rawWidth"]):
                                raise ValueError("DNG dimensions differ from capture")
                            if raw.raw_pattern is None or raw.color_desc is None:
                                raise ValueError("Missing CFA metadata")
                            signature = (pixels.shape, raw.raw_pattern.tolist(), raw.black_level_per_channel, raw.white_level)
                            if signatures and signature != signatures[0]:
                                raise ValueError("RAW layout or levels changed")
                            signatures.append(signature)
                            hashes.append(hashlib.sha256(pixels.tobytes()).hexdigest())
                        rows.append({"index": index, "dng": name, "sha256": digest,
                                     "sensorTimestampNs": t, "settings": settings})
                if any(b <= a for a, b in zip(timestamps, timestamps[1:])):
                    raise ValueError("Non-monotonic timestamps")
                if len(set(hashes)) != 5:
                    raise ValueError("Duplicate sensor frames")
                span = (timestamps[-1] - timestamps[0]) / 1e9
                summary["series"].append({"id": s["id"], "lens": s["lens"],
                    "logicalId": s["seriesFrames"][0]["capture"]["logicalId"],
                    "physicalId": s["seriesFrames"][0]["capture"]["physicalId"],
                    "frameCount": 5, "spanSeconds": span,
                    "medianIntervalMs": float(np.median(np.diff(timestamps))) / 1e6,
                    "rawLayout": signatures[0], "frames": rows})
            summary["status"] = "VALID_INPUTS_NOT_A_QUALITY_RESULT"
    except Exception as e:
        summary["errors"].append(f"{type(e).__name__}: {e}")
    return summary


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("zip", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = validate(args.zip)
    encoded = json.dumps(result, indent=2)
    if args.output:
        args.output.write_text(encoded + "\n")
    else:
        print(encoded)
    raise SystemExit(0 if not result["errors"] else 1)
