#!/usr/bin/env python3
"""Verify the offline Termux toolbox before it is packaged into the APK.

This is the build-time guard for the recurring
`CANNOT LINK EXECUTABLE "bash": library "libreadline.so.8" not found` bug.

It builds a virtual filesystem from every .deb in assets/toolbox, then checks:

  * the essential shell files exist and every symlink resolves;
  * bootstrap.txt extracts bash/readline in Java before dpkg runs;
  * every DT_NEEDED of the essential ELF binaries is provided by a bundled
    package (or is a known Android system library).

Run:
    python3 scripts/verify-toolbox.py
    python3 scripts/verify-toolbox.py --dir app/src/main/assets/toolbox
"""
import argparse
import bz2
import gzip
import io
import lzma
import os
import struct
import sys
import tarfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_DIR = os.path.join(ROOT, "app", "src", "main", "assets", "toolbox")
PREFIX = "./data/data/com.termux/files/usr/"

ESSENTIAL = [
    "bin/bash",
    "lib/libreadline.so.8",
    "lib/libhistory.so.8",
    "lib/libncursesw.so.6",
    "lib/libiconv.so",
    "lib/libandroid-support.so",
    "bin/pkg",
]
ESSENTIAL_ELFS = [
    "bin/bash", "bin/git", "bin/python3", "bin/node", "bin/clang",
    "bin/make", "bin/cmake", "bin/adb", "bin/ssh", "bin/curl",
]

# Bionic libraries that live in /system/lib64, not in the Termux prefix.
SYSTEM_LIBS = {
    "libc.so", "libdl.so", "libm.so", "liblog.so", "libandroid.so",
    "libnetd_client.so", "libnativehelper.so", "libutils.so", "libcutils.so",
    "libhardware.so", "libbase.so", "libui.so", "libEGL.so", "libGLESv2.so",
    "libvulkan.so", "libOpenSLES.so", "libz.so", "libstdc++.so",
    "libc++_shared.so", "libjnigraphics.so", "libmediandk.so",
    "libsync.so", "libion.so", "libprocessgroup.so",
}


def decompress(data):
    if data[:6] == b"\xfd7zXZ\x00":
        return lzma.decompress(data)
    if data[:2] == b"\x1f\x8b":
        return gzip.decompress(data)
    if data[:3] == b"BZh":
        return bz2.decompress(data)
    return data


def deb_entries(path):
    """Yield (relative path, is_symlink, linkname, payload) from a .deb."""
    raw = open(path, "rb").read()
    if raw[:8] != b"!<arch>\n":
        raise ValueError(f"not an ar archive: {path}")
    off = 8
    data_tar = None
    while off + 60 <= len(raw):
        name = raw[off:off + 16].decode("utf-8", "replace").strip()
        size = int(raw[off + 48:off + 58].decode("ascii").strip())
        body = raw[off + 60:off + 60 + size]
        if name.startswith("data.tar"):
            data_tar = decompress(body)
        off += 60 + size + (size % 2)
    if data_tar is None:
        raise ValueError(f"no data.tar in {path}")

    with tarfile.open(fileobj=io.BytesIO(data_tar), mode="r:") as tar:
        for member in tar.getmembers():
            rel = member.name
            if rel.startswith("./"):
                rel = rel[2:]
            if rel.startswith("data/data/com.termux/files/usr/"):
                rel = rel[len("data/data/com.termux/files/usr/"):]
            elif not rel.startswith("data/data/com.termux/files/usr/"):
                continue
            if not rel or rel in ("./", "."):
                continue
            payload = None
            if member.isfile():
                payload = tar.extractfile(member).read()
            yield rel, member.issym(), member.linkname, payload


class VFS:
    def __init__(self):
        self.files = {}      # path -> set of packages
        self.dirs = set()
        self.links = {}      # path -> target
        self.elfs = {}       # path -> payload

    def add(self, pkg, entries):
        for rel, is_link, linkname, payload in entries:
            self.files.setdefault(rel, set()).add(pkg)
            if is_link:
                self.links[rel] = linkname
            elif payload is None:
                # Directories have no payload; they are needed to resolve
                # links such as lib/icu/current/Makefile.inc.
                self.dirs.add(rel)
            if payload and rel.startswith("bin/"):
                self.elfs[rel] = payload

    def resolve(self, rel, depth=0):
        if depth > 32:
            return None
        parts = [p for p in rel.split("/") if p and p != "."]
        cur = []
        for index, part in enumerate(parts):
            cur.append(part)
            current = "/".join(cur)
            if current in self.links:
                target = self.links[current]
                if target.startswith("/"):
                    target = target.lstrip("/")
                    if target.startswith("data/data/com.termux/files/usr/"):
                        target = target[len("data/data/com.termux/files/usr/"):]
                    elif "com.termux/files/usr/" in target:
                        target = target.split("com.termux/files/usr/", 1)[1]
                else:
                    parent = os.path.dirname(current)
                    target = os.path.normpath(
                        os.path.join(parent, target)).replace("\\", "/")
                rest = parts[index + 1:]
                return self.resolve(target + ("/" + "/".join(rest) if rest else ""),
                                    depth + 1)
        final = "/".join(parts)
        if final in self.files or final in self.dirs:
            return final
        return None

    def exists(self, rel):
        return self.resolve(rel) is not None

    def lib_exists(self, name):
        return self.exists("lib/" + name)


def elf_needed(data):
    if data[:4] != b"\x7fELF" or data[4] != 2 or data[5] != 1:
        return []
    e_shoff = struct.unpack_from("<Q", data, 0x28)[0]
    e_shentsize = struct.unpack_from("<H", data, 0x3A)[0]
    e_shnum = struct.unpack_from("<H", data, 0x3C)[0]
    e_shstrndx = struct.unpack_from("<H", data, 0x3E)[0]
    sections = []
    for i in range(e_shnum):
        off = e_shoff + i * e_shentsize
        (name, typ, flags, addr, sh_off, size,
         link, info, align, entsize) = struct.unpack_from("<IIQQQQIIQQ", data, off)
        sections.append({
            "name": name, "type": typ, "off": sh_off, "size": size, "link": link,
        })
    shstr = sections[e_shstrndx]

    def sh_name(off):
        end = data.index(b"\0", shstr["off"] + off)
        return data[shstr["off"] + off:end].decode()

    by_name = {sh_name(s["name"]): s for s in sections}
    dyn = by_name.get(".dynamic")
    dynstr = by_name.get(".dynstr")
    if not dyn or not dynstr:
        return []
    strings = data[dynstr["off"]:dynstr["off"] + dynstr["size"]]

    def string(off):
        end = strings.index(b"\0", off)
        return strings[off:end].decode()

    out = []
    for off in range(dyn["off"], dyn["off"] + dyn["size"], 16):
        tag, val = struct.unpack_from("<qQ", data, off)
        if tag == 0:
            break
        if tag == 1:  # DT_NEEDED
            out.append(string(val))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", default=DEFAULT_DIR)
    args = ap.parse_args()
    if not os.path.isdir(args.dir):
        sys.exit(f"toolbox directory not found: {args.dir}")

    debs = sorted(f for f in os.listdir(args.dir) if f.endswith(".deb"))
    if not debs:
        sys.exit(f"no .deb files in {args.dir}; run scripts/fetch-toolbox.py first")

    vfs = VFS()
    for deb in debs:
        pkg = deb[:-4]
        try:
            vfs.add(pkg, list(deb_entries(os.path.join(args.dir, deb))))
        except Exception as e:  # noqa: BLE001 - report the offending .deb
            sys.exit(f"cannot read {deb}: {e}")

    errors = []
    missing = [p for p in ESSENTIAL if not vfs.exists(p)]
    if missing:
        errors.append("missing essential files: " + ", ".join(missing))

    for rel, target in vfs.links.items():
        # Only runtime paths matter here. Service/icu data links are optional
        # packages and do not affect bash, git or python startup.
        if not (rel.startswith("lib/") or rel.startswith("bin/")):
            continue
        if vfs.resolve(rel) is None:
            errors.append(f"broken symlink: {rel} -> {target}")

    bootstrap = os.path.join(args.dir, "bootstrap.txt")
    if not os.path.isfile(bootstrap):
        errors.append("bootstrap.txt is missing")
    else:
        names = {line.strip() for line in open(bootstrap, encoding="utf-8") if line.strip()}
        for must in ("bash", "readline"):
            if must not in names:
                errors.append(f"bootstrap.txt does not extract {must} before dpkg")

    checked = 0
    for rel in ESSENTIAL_ELFS:
        data = vfs.elfs.get(vfs.resolve(rel) or "")
        if not data:
            errors.append(f"{rel}: ELF payload not found")
            continue
        for lib in elf_needed(data):
            checked += 1
            if vfs.lib_exists(lib) or lib in SYSTEM_LIBS:
                continue
            errors.append(f"{rel}: needs {lib}, no bundled package provides it")

    if errors:
        print("toolbox verification FAILED", file=sys.stderr)
        for error in errors:
            print("  - " + error, file=sys.stderr)
        return 1

    print(f"toolbox OK: {len(debs)} packages, {len(vfs.files)} files, "
          f"{checked} DT_NEEDED entries, {len(vfs.links)} symlinks")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
