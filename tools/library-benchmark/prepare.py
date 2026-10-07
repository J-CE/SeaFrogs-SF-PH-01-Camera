# SPDX-License-Identifier: GPL-3.0-only
"""Fetch pinned upstream sources and copy the validated input ZIP safely."""
from pathlib import Path
import argparse, json, zipfile, tarfile, io, urllib.request
BASE = Path(__file__).resolve().parent
PINS = [("timothybrooks/hdr-plus", "ef4dd2ca53a51e105ed923557c726b253f05c13b", "hdr-plus-master"),
        ("f0enix/motioncam", "cc7f7c9cad5234bc939699b1ab1ffbc4bdfd6690", "motioncam-main")]

def upstream():
    for repo, sha, name in PINS:
        destination = BASE / "upstream" / name
        if destination.exists():
            continue
        url = f"https://codeload.github.com/{repo}/tar.gz/{sha}"
        data = urllib.request.urlopen(url, timeout=120).read()
        with tarfile.open(fileobj=io.BytesIO(data)) as archive:
            members = archive.getmembers()
            prefix = members[0].name.split("/")[0]
            for member in members:
                if not member.isfile():
                    continue
                relative = Path(member.name).relative_to(prefix)
                if ".." in relative.parts:
                    raise ValueError("Unexpected upstream path")
                target = destination / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(archive.extractfile(member).read())

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("zip", type=Path)
    args = parser.parse_args()
    upstream()
    with zipfile.ZipFile(args.zip) as archive:
        if archive.testzip() is not None:
            raise ValueError("Input ZIP CRC failure")
        (BASE / "input-report.json").write_bytes(archive.read("camera-test-report.json"))
        for name in archive.namelist():
            if not name.startswith("photos/"):
                continue
            relative = Path(name).relative_to("photos")
            if ".." in relative.parts:
                raise ValueError("Unexpected input path")
            target = BASE / "inputs" / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(archive.read(name))
