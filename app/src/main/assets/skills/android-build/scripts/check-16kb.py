#!/usr/bin/env python3
"""Check ELF PT_LOAD alignment for Android 16 KB page-size compatibility.

Usage:
  check-16kb.py <file|dir|apk>...
Exit code 1 if ADIA_16KB_STRICT=1 and any ELF is not 16 KB aligned.
"""
import os
import struct
import sys
import zipfile

PT_LOAD = 1
PAGE = 0x4000


def check_bytes(name, data):
    if data[:4] != b"\x7fELF":
        return None
    # Android's 16 KB requirement applies to 64-bit ELF only. A 32-bit
    # armeabi-v7a/x86 library is expected to report ELFCLASS32 and must not
    # be counted as a failure.
    if data[4] == 1:
        return (name, None, "32-bit ABI; 16 KB rule applies to 64-bit only")
    if data[4] != 2:
        return (name, None, "unknown ELF class")
    endian = "<" if data[5] == 1 else ">"
    phoff = struct.unpack_from(endian + "Q", data, 0x20)[0]
    phentsize = struct.unpack_from(endian + "H", data, 0x36)[0]
    phnum = struct.unpack_from(endian + "H", data, 0x38)[0]
    loads = []
    for i in range(phnum):
        off = phoff + i * phentsize
        if struct.unpack_from(endian + "I", data, off)[0] != PT_LOAD:
            continue
        p_offset = struct.unpack_from(endian + "Q", data, off + 0x08)[0]
        p_vaddr = struct.unpack_from(endian + "Q", data, off + 0x10)[0]
        p_align = struct.unpack_from(endian + "Q", data, off + 0x30)[0]
        loads.append((p_offset, p_vaddr, p_align))
    if not loads:
        return (name, False, "no PT_LOAD")
    ok = all(a >= PAGE and (o % PAGE) == (v % PAGE) for o, v, a in loads)
    worst = min(a for _, _, a in loads)
    return (name, ok, f"min_align=0x{worst:x}")


def check_file(path):
    try:
        with open(path, "rb") as f:
            data = f.read()
    except OSError as e:
        return (path, False, str(e))
    return check_bytes(path, data)


def check_apk(path):
    out = []
    with zipfile.ZipFile(path) as z:
        for info in z.infolist():
            if info.filename.startswith("lib/") and info.filename.endswith(".so"):
                out.append(check_bytes(info.filename, z.read(info)))
    return out


def main():
    args = sys.argv[1:]
    if not args:
        print("usage: check-16kb.py <file|dir|apk>...")
        return 2
    results = []
    for arg in args:
        if arg.endswith(".apk"):
            results.extend(check_apk(arg))
        elif os.path.isdir(arg):
            for base, _, names in os.walk(arg):
                for n in names:
                    if n.endswith(".so"):
                        results.append(check_file(os.path.join(base, n)))
        else:
            results.append(check_file(arg))

    bad = 0
    skipped = 0
    checked = 0
    for item in results:
        if item is None:
            continue
        name, ok, detail = item
        if ok is None:
            skipped += 1
            print(f"SKIP          {detail}  {name}")
            continue
        checked += 1
        if not ok:
            bad += 1
        print(("16KB OK      " if ok else "4KB/UNALIGNED") + f"  {detail}  {name}")
    if bad:
        print(f"\n{bad} native file(s) are not 16 KB aligned.", file=sys.stderr)
        if os.environ.get("ADIA_16KB_STRICT") == "1":
            return 1
    elif skipped:
        print(f"\n{checked} 64-bit native file(s) are 16 KB aligned; "
              f"{skipped} 32-bit/non-ELF entry(ies) skipped.")
    else:
        print("\nAll native files are 16 KB aligned.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
