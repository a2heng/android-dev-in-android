#!/usr/bin/env python3
"""Fetch the official opencode linux-arm64-musl build into jniLibs."""
import argparse
import os
import shutil
import subprocess
import sys
import tarfile
import time
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.join(ROOT, ".cache", "opencode")
DEST = os.path.join(ROOT, "app", "src", "main", "jniLibs", "arm64-v8a",
                    "libopencode2_bin.so")


def download(url, dest):
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    if os.path.isfile(dest) and os.path.getsize(dest) > 0:
        print("cached", dest)
        return
    print("GET", url)
    part = dest + ".part"
    last = None
    for attempt in range(5):
        try:
            req = urllib.request.Request(
                url, headers={"User-Agent": "android-dev-in-android"})
            with urllib.request.urlopen(req, timeout=180) as src, open(part, "wb") as out:
                shutil.copyfileobj(src, out, 1 << 20)
            os.replace(part, dest)
            return
        except Exception as e:
            last = e
            print(f"attempt {attempt + 1} failed: {e}", file=sys.stderr)
            time.sleep(min(2 ** attempt, 10))
    # GitHub release assets occasionally return 504 from the CDN; curl retries
    # the whole redirect chain more aggressively.
    if shutil.which("curl"):
        print("falling back to curl", file=sys.stderr)
        if os.path.exists(part):
            os.remove(part)
        subprocess.run(["curl", "-fL", "--retry", "10", "--retry-all-errors",
                        "--retry-delay", "2", "-A", "android-dev-in-android",
                        "-o", dest, url], check=True)
        return
    raise SystemExit(f"download failed: {last}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--version", default="2.3.0")
    args = ap.parse_args()

    name = f"opencode-linux-arm64-musl-{args.version}.tar.gz"
    archive = os.path.join(CACHE, name)
    url = ("https://github.com/anomalyco/opencode/releases/download/"
           f"v{args.version}/opencode-linux-arm64-musl.tar.gz")
    download(url, archive)

    os.makedirs(os.path.dirname(DEST), exist_ok=True)
    with tarfile.open(archive, "r:gz") as tar:
        member = next((m for m in tar.getmembers() if m.name in ("opencode", "./opencode")), None)
        if member is None:
            raise SystemExit("opencode binary not found in archive")
        with tar.extractfile(member) as src, open(DEST, "wb") as out:
            shutil.copyfileobj(src, out, 1 << 20)
    os.chmod(DEST, 0o755)
    print("wrote", DEST, os.path.getsize(DEST), "bytes")


if __name__ == "__main__":
    main()
