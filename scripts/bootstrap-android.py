#!/usr/bin/env python3
"""Fetch Google's pinned SDK components into this project, without changing the host SDK."""
import concurrent.futures
import hashlib
import json
import pathlib
import shutil
import stat
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
SDK = ROOT / ".tools" / "android"


def install(item):
    name, package = item
    destination = SDK / package["folder"]
    marker = destination / ".airmouse-installed"
    if marker.exists():
        print(f"Ready: {name}", flush=True)
        return
    cache = ROOT / ".tools" / "downloads"
    cache.mkdir(parents=True, exist_ok=True)
    archive = cache / package["url"].rsplit("/", 1)[1]
    if not archive.exists():
        print(f"Downloading {name} from Google…", flush=True)
        temporary = archive.with_suffix(".partial")
        urllib.request.urlretrieve(package["url"], temporary)
        temporary.replace(archive)
    checksum = hashlib.sha1(archive.read_bytes()).hexdigest()
    if checksum != package["sha1"]:
        archive.unlink()
        raise RuntimeError(f"Checksum mismatch for {name}; retry the download")
    temporary = cache / (destination.name + "-extracted")
    if temporary.exists():
        shutil.rmtree(temporary)
    temporary.mkdir()
    with zipfile.ZipFile(archive) as zipped:
        for entry in zipped.infolist():
            target = (temporary / entry.filename).resolve()
            if not target.is_relative_to(temporary.resolve()):
                raise RuntimeError("Unsafe path in SDK archive")
            zipped.extract(entry, temporary)
            mode = entry.external_attr >> 16
            if mode & stat.S_IXUSR and target.is_file():
                target.chmod(mode)
    children = list(temporary.iterdir())
    source = children[0] if len(children) == 1 and children[0].is_dir() else temporary
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.exists():
        shutil.rmtree(destination)
    shutil.move(str(source), str(destination))
    marker.write_text(package["sha1"] + "\n")
    print(f"Installed {name}", flush=True)


if __name__ == "__main__":
    packages = json.loads((ROOT / "scripts/android-sdk-packages.json").read_text())
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        list(pool.map(install, packages.items()))
