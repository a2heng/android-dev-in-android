#!/usr/bin/env python3
"""Build assets/toolchain/toolchain.zip (offline SDK + Gradle + cache)."""
import argparse
import os
import shutil
import stat
import subprocess
import sys
import time
import urllib.request
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.join(ROOT, ".cache", "toolchain")
DEFAULT_OUT = os.path.join(ROOT, "app", "src", "main", "assets", "toolchain", "toolchain.zip")
PLATFORM_URL = "https://dl.google.com/android/repository/platform-35_r02.zip"
TOOLS_URL = ("https://github.com/lzhiyong/android-sdk-tools/releases/download/"
             "35.0.2/android-sdk-tools-static-aarch64.zip")
GRADLE_URL = ("https://github.com/gradle/gradle-distributions/releases/download/"
              "v{version}/gradle-{version}-bin.zip")


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
            with urllib.request.urlopen(req, timeout=300) as src, open(part, "wb") as out:
                shutil.copyfileobj(src, out, 1 << 20)
            os.replace(part, dest)
            return
        except Exception as e:
            last = e
            print(f"attempt {attempt + 1} failed: {e}", file=sys.stderr)
            time.sleep(min(2 ** attempt, 10))
    if shutil.which("curl"):
        print("falling back to curl", file=sys.stderr)
        if os.path.exists(part):
            os.remove(part)
        subprocess.run(["curl", "-fL", "--retry", "10", "--retry-all-errors",
                        "--retry-delay", "2", "-A", "android-dev-in-android",
                        "-o", dest, url], check=True)
        return
    raise SystemExit(f"download failed: {last}")


def extract(zip_path, dest):
    os.makedirs(dest, exist_ok=True)
    with zipfile.ZipFile(zip_path) as z:
        z.extractall(dest)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--platform", default="35")
    ap.add_argument("--gradle", default="9.8.1")
    ap.add_argument("--gradle-cache", default=os.path.expanduser("~/.gradle/caches/modules-2"))
    ap.add_argument("--out", default=DEFAULT_OUT)
    ap.add_argument("--keep", action="store_true", help="keep the unpacked staging tree")
    args = ap.parse_args()

    platform_zip = os.path.join(CACHE, f"platform-{args.platform}_r02.zip")
    tools_zip = os.path.join(CACHE, "android-sdk-tools-static-aarch64.zip")
    gradle_zip = os.path.join(CACHE, f"gradle-{args.gradle}-bin.zip")

    download(PLATFORM_URL if args.platform == "35" else
             f"https://dl.google.com/android/repository/platform-{args.platform}_r01.zip",
             platform_zip)
    download(TOOLS_URL, tools_zip)
    download(GRADLE_URL.format(version=args.gradle), gradle_zip)

    stage = os.path.join(CACHE, "stage")
    shutil.rmtree(stage, ignore_errors=True)
    sdk = os.path.join(stage, "android-sdk")
    bt = os.path.join(sdk, "build-tools", "35.0.2")
    os.makedirs(bt, exist_ok=True)

    print("platform...")
    extract(platform_zip, os.path.join(sdk, "platforms"))

    print("build-tools...")
    tmp_tools = os.path.join(stage, ".tools")
    extract(tools_zip, tmp_tools)
    # Copy every aarch64 tool, not just aapt2: AGP also expects aapt, aidl,
    # dexdump, split-select and zipalign, and checks source.properties.
    src_bt = os.path.join(tmp_tools, "build-tools")
    for name in os.listdir(src_bt):
        src = os.path.join(src_bt, name)
        if os.path.isfile(src):
            shutil.copy2(src, os.path.join(bt, name))
    # On-device adb for the android-debug skill (aarch64, static).
    pt_src = os.path.join(tmp_tools, "platform-tools", "adb")
    if os.path.isfile(pt_src):
        pt_dir = os.path.join(stage, "platform-tools")
        os.makedirs(pt_dir, exist_ok=True)
        shutil.copy2(pt_src, os.path.join(pt_dir, "adb"))
        os.chmod(os.path.join(pt_dir, "adb"), 0o755)
    with open(os.path.join(bt, "source.properties"), "w", encoding="utf-8") as f:
        f.write("Pkg.Desc=Android SDK Build-Tools 35.0.2\n")
        f.write("Pkg.Revision=35.0.2\n")
    # AGP needs this platform-independent jar for Java 8 lambda desugaring.
    core_stubs = os.path.join(ROOT, "third_party", "android-build-tools",
                              "core-lambda-stubs.jar")
    if os.path.isfile(core_stubs):
        shutil.copy2(core_stubs, os.path.join(bt, "core-lambda-stubs.jar"))
    else:
        print("warning: missing", core_stubs)

    # Prefer the 16 KB-aligned aapt2 (supports SDK 35/36/37 resources).
    aapt2_16k = os.path.join(ROOT, "third_party", "android-build-tools",
                             "aapt2-aarch64-16kb")
    if os.path.isfile(aapt2_16k):
        shutil.copy2(aapt2_16k, os.path.join(bt, "aapt2"))
        os.chmod(os.path.join(bt, "aapt2"), 0o755)
    else:
        print("warning: missing", aapt2_16k)

    print("gradle...")
    gradle_dir = os.path.join(stage, "gradle")
    os.makedirs(gradle_dir, exist_ok=True)
    extract(gradle_zip, gradle_dir)
    # The zip contains gradle-<version>/
    if not os.path.isdir(os.path.join(gradle_dir, f"gradle-{args.gradle}")):
        for name in os.listdir(gradle_dir):
            if name.startswith("gradle-"):
                break

    print("cache...")
    cache_dst = os.path.join(stage, "gradle-home", "caches", "modules-2")
    if os.path.isdir(args.gradle_cache):
        shutil.copytree(args.gradle_cache, cache_dst)
    else:
        print("warning: gradle cache not found:", args.gradle_cache)
        os.makedirs(cache_dst, exist_ok=True)
    with open(os.path.join(stage, "gradle-home", "gradle.properties"), "w",
              encoding="utf-8") as f:
        f.write("org.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=1g\n")
        f.write("org.gradle.daemon=false\n")
        f.write("org.gradle.offline=true\n")

    shutil.rmtree(tmp_tools, ignore_errors=True)

    print("zip...")
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    if os.path.exists(args.out):
        os.remove(args.out)
    count = 0
    with zipfile.ZipFile(args.out, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for base, _, files in os.walk(stage):
            for name in files:
                full = os.path.join(base, name)
                rel = os.path.relpath(full, stage).replace(os.sep, "/")
                info = zipfile.ZipInfo.from_file(full, rel)
                info.compress_type = zipfile.ZIP_DEFLATED
                if (name.endswith(".sh") or "/bin/" in "/" + rel
                        or "build-tools/" in rel or "platform-tools/" in rel
                        or name in ("aapt2", "zipalign", "gradle")):
                    info.external_attr = (stat.S_IFREG | 0o755) << 16
                else:
                    info.external_attr = (stat.S_IFREG | 0o644) << 16
                with open(full, "rb") as src, z.open(info, "w") as dst:
                    shutil.copyfileobj(src, dst, 1 << 20)
                count += 1

    if not args.keep:
        shutil.rmtree(stage, ignore_errors=True)
    print("entries:", count, "size:", os.path.getsize(args.out))


if __name__ == "__main__":
    main()
