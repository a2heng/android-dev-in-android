#!/usr/bin/env python3
"""Build the offline Termux toolbox (assets/toolbox) from a Termux mirror."""
import argparse
import gzip
import os
import re
import shutil
import sys
import time
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_OUT = os.path.join(ROOT, "app", "src", "main", "assets", "toolbox")
MIRRORS = [
    "https://mirrors.tuna.tsinghua.edu.cn/termux/apt/termux-main",
    "https://mirrors.ustc.edu.cn/termux/apt/termux-main",
    "https://mirrors.bfsu.edu.cn/termux/apt/termux-main",
    "https://packages.termux.dev/apt/termux-main",
]
DEFAULT_SET = [
    "bash", "coreutils", "curl", "git", "python", "python-pip", "nodejs",
    "openssh", "make", "cmake", "ninja", "pkg-config", "binutils", "clang",
    "wget", "openjdk-21",
]


def fetch(url):
    last = None
    for attempt in range(5):
        try:
            req = urllib.request.Request(
                url, headers={"User-Agent": "android-dev-in-android"})
            with urllib.request.urlopen(req, timeout=180) as r:
                return r.read()
        except Exception as e:
            last = e
            print(f"fetch failed ({attempt + 1}/5): {url}: {e}", file=sys.stderr)
            time.sleep(min(2 ** attempt, 10))
    raise last


def load_index(repo):
    data = fetch(repo + "/dists/stable/main/binary-aarch64/Packages.gz")
    text = gzip.decompress(data).decode("utf-8", "replace")
    pkgs = {}
    for stanza in text.split("\n\n"):
        fields = {}
        for line in stanza.splitlines():
            if line[:1] in (" ", "\t"):
                continue
            if ":" in line:
                k, v = line.split(":", 1)
                fields[k.strip()] = v.strip()
        if "Package" in fields:
            pkgs[fields["Package"]] = fields
    return pkgs


def dep_alts(value):
    for part in (value or "").split(","):
        part = part.strip()
        if not part:
            continue
        yield [re.sub(r"\s*\(.*?\)", "", x).strip() for x in part.split("|")]


def resolve(pkgs, roots):
    seen, order = set(), []

    def visit(name):
        if name in seen:
            return
        meta = pkgs.get(name)
        if meta is None:
            print("warning: missing package", name, file=sys.stderr)
            return
        seen.add(name)
        for alts in dep_alts(meta.get("Pre-Depends", "")):
            pick = next((a for a in alts if a in pkgs), None)
            if pick:
                visit(pick)
        for alts in dep_alts(meta.get("Depends", "")):
            pick = next((a for a in alts if a in pkgs), None)
            if pick:
                visit(pick)
        order.append(name)

    for root in roots:
        visit(root)
    return order


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=DEFAULT_OUT)
    ap.add_argument("--repo", default=MIRRORS[0])
    ap.add_argument("--no-jdk", action="store_true")
    args = ap.parse_args()

    roots = list(DEFAULT_SET)
    if args.no_jdk:
        roots.remove("openjdk-21")

    repos = [args.repo] + [m for m in MIRRORS if m != args.repo]
    last = None
    for repo in repos:
        try:
            print("index:", repo)
            pkgs = load_index(repo)
            break
        except Exception as e:
            last = e
            print("failed:", repo, e, file=sys.stderr)
    else:
        raise SystemExit(f"could not load any mirror: {last}")

    all_pkgs = resolve(pkgs, roots)
    boot_pkgs = resolve(pkgs, ["apt", "dpkg"])

    os.makedirs(args.out, exist_ok=True)
    total = 0
    for name in all_pkgs:
        meta = pkgs[name]
        dest = os.path.join(args.out, name + ".deb")
        if os.path.isfile(dest) and os.path.getsize(dest) > 0:
            total += os.path.getsize(dest)
            continue
        rel = meta["Filename"]
        url = repo + "/" + rel
        print("GET", name, meta.get("Version", ""))
        data = fetch(url)
        with open(dest, "wb") as f:
            f.write(data)
        total += len(data)

    with open(os.path.join(args.out, "bootstrap.txt"), "w", encoding="utf-8", newline="\n") as f:
        for name in boot_pkgs:
            f.write(name + "\n")

    with open(os.path.join(args.out, "packages.txt"), "w", encoding="utf-8", newline="\n") as f:
        for name in sorted(all_pkgs):
            f.write(f"{name}\t{pkgs[name].get('Version', '')}\n")

    print(f"packages: {len(all_pkgs)}  bootstrap: {len(boot_pkgs)}  size: {total / 1048576:.1f} MiB")
    print("out:", args.out)


if __name__ == "__main__":
    main()
