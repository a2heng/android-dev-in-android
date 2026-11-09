#!/usr/bin/env python3
"""Report ELF PT_LOAD alignment for Android 16 KB page-size compatibility."""
import struct
import sys
import os

PT_LOAD = 1
PAGE_16K = 0x4000


def check(path):
    try:
        with open(path, "rb") as f:
            data = f.read()
    except OSError as e:
        return f"{path}: cannot read: {e}"
    if data[:4] != b"\x7fELF":
        return f"{path}: not an ELF file"
    if data[4] != 2:
        return f"{path}: not ELF64"
    endian = "<" if data[5] == 1 else ">"
    e_phoff = struct.unpack_from(endian + "Q", data, 0x20)[0]
    e_phentsize = struct.unpack_from(endian + "H", data, 0x36)[0]
    e_phnum = struct.unpack_from(endian + "H", data, 0x38)[0]
    loads = []
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        p_type = struct.unpack_from(endian + "I", data, off)[0]
        if p_type != PT_LOAD:
            continue
        p_offset = struct.unpack_from(endian + "Q", data, off + 0x08)[0]
        p_vaddr = struct.unpack_from(endian + "Q", data, off + 0x10)[0]
        p_align = struct.unpack_from(endian + "Q", data, off + 0x30)[0]
        loads.append((p_offset, p_vaddr, p_align))
    if not loads:
        return f"{path}: no PT_LOAD segments"
    worst = min(a for _, _, a in loads)
    aligned = all(a >= PAGE_16K and (o % PAGE_16K) == (v % PAGE_16K)
                  for o, v, a in loads)
    state = "16KB OK" if aligned else "4KB/unaligned"
    aligns = ",".join(hex(a) for _, _, a in loads)
    return f"{state:12} min_align={hex(worst):8} loads=[{aligns}] {path}"


def main():
    args = sys.argv[1:]
    if not args:
        print("usage: check-elf-align.py <file>...")
        return 1
    files = []
    for arg in args:
        if os.path.isdir(arg):
            for base, _, names in os.walk(arg):
                for n in names:
                    if n.endswith(".so") or "." not in n:
                        files.append(os.path.join(base, n))
        else:
            files.append(arg)
    for f in files:
        print(check(f))
    return 0


if __name__ == "__main__":
    sys.exit(main())
