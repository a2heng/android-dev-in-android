#!/usr/bin/env python3
"""Resolve Maven coordinates into the shared Adia Maven mirror.

Examples:
  resolve_maven.py com.squareup.okhttp3:okhttp:4.12.0
  resolve_maven.py androidx.room:room-runtime:2.6.1 --no-deps
  resolve_maven.py com.google.code.gson:gson:2.11.0 --repositories https://maven.aliyun.com/repository/public

The mirror is $ADIA_M2 (default $HOME/toolchain/m2), in standard Maven
layout, so Maven, sbt, javac, kotlinc, scripts and Gradle can all reuse it.
"""
import argparse
import hashlib
import os
import sys
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET

HOME = os.environ.get("HOME", "")
M2 = os.environ.get("ADIA_M2") or os.path.join(HOME, "toolchain", "m2")
GOOGLE = "https://dl.google.com/dl/android/maven2"
CENTRAL = "https://repo1.maven.org/maven2"
DEFAULT_REPOS = [GOOGLE, CENTRAL]
UA = "Adia-android-lib-cache/1.0"
SKIP_SCOPES = {"test", "provided", "system", "import"}
VISITED = set()


def log(msg):
    print(f"[lib-cache] {msg}", flush=True)


def http(url, timeout=60):
    last = None
    for attempt in range(4):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=timeout) as response:
                return response.read()
        except urllib.error.HTTPError as error:
            if error.code in (404, 410):
                return None
            last = error
        except Exception as error:  # network reset, proxy, DNS
            last = error
        time.sleep(min(2 ** attempt, 8))
    raise RuntimeError(f"GET {url} failed: {last}")


def gav_path(group, artifact, version):
    return os.path.join(M2, *group.split("."), artifact, version)


def file_sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as source:
        for block in iter(lambda: source.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def index_line(coordinate, path):
    index = os.path.join(M2, ".adia-index.tsv")
    if not os.path.exists(index):
        os.makedirs(M2, exist_ok=True)
        with open(index, "w", encoding="utf-8") as out:
            out.write("coordinate\tfile\tsha256\n")
    with open(index, "r", encoding="utf-8") as source:
        if path in source.read():
            return
    with open(index, "a", encoding="utf-8") as out:
        out.write(f"{coordinate}\t{path}\t{file_sha256(path)}\n")


def save(group, artifact, version, filename, data, coordinate):
    dest = os.path.join(gav_path(group, artifact, version), filename)
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    if os.path.exists(dest) and os.path.getsize(dest) > 0:
        log(f"cached {coordinate} -> {dest}")
        return dest
    tmp = dest + ".part"
    with open(tmp, "wb") as out:
        out.write(data)
    os.replace(tmp, dest)
    index_line(coordinate, dest)
    log(f"saved {coordinate} -> {dest}")
    return dest


def check_sha1(url, data):
    try:
        reference = http(url + ".sha1", timeout=20)
    except Exception:
        return
    if not reference:
        return
    expected = reference.decode("utf-8", "replace").split()[0].strip().lower()
    actual = hashlib.sha1(data).hexdigest()
    if len(expected) == 40 and expected != actual:
        log(f"WARNING sha1 mismatch for {url} (expected {expected}, got {actual})")


def parse_coordinate(value):
    parts = value.split(":")
    if len(parts) < 3:
        raise SystemExit(f"bad coordinate (need group:artifact:version): {value}")
    group, artifact, version = parts[:3]
    packaging = parts[3] if len(parts) > 3 and parts[3] else "jar"
    classifier = parts[4] if len(parts) > 4 and parts[4] else ""
    return group, artifact, version, packaging, classifier


def prop(value, properties):
    if not value:
        return value
    for _ in range(6):
        match = None
        start = value.find("${")
        if start < 0:
            break
        end = value.find("}", start + 2)
        if end < 0:
            break
        name = value[start + 2:end]
        replacement = properties.get(name)
        if replacement is None:
            match = value
            break
        value = value[:start] + replacement + value[end + 1:]
    return value


def parse_pom(data):
    root = ET.fromstring(data)
    ns = ""
    if root.tag.startswith("{"):
        ns = root.tag[1:root.tag.find("}")]
    def tag(name):
        return f"{{{ns}}}{name}" if ns else name
    def text(node, name):
        child = node.find(tag(name)) if node is not None else None
        return child.text.strip() if child is not None and child.text else None
    parent = root.find(tag("parent"))
    info = {
        "group": text(root, "groupId") or text(parent, "groupId"),
        "artifact": text(root, "artifactId"),
        "version": text(root, "version") or text(parent, "version"),
        "packaging": text(root, "packaging") or "jar",
        "parent": {
            "group": text(parent, "groupId"),
            "artifact": text(parent, "artifactId"),
            "version": text(parent, "version"),
        } if parent is not None else None,
        "properties": {},
        "managed": {},
        "dependencies": [],
    }
    for node in root.findall(tag("properties")):
        for item in list(node):
            key = item.tag.split("}")[-1]
            if item.text:
                info["properties"][key] = item.text.strip()
    management = root.find(tag("dependencyManagement"))
    if management is not None:
        deps = management.find(tag("dependencies"))
        if deps is not None:
            for dep in deps.findall(tag("dependency")):
                key = f"{text(dep, 'groupId')}:{text(dep, 'artifactId')}"
                info["managed"][key] = {
                    "version": text(dep, "version"),
                    "type": text(dep, "type"),
                    "classifier": text(dep, "classifier"),
                    "scope": text(dep, "scope"),
                }
    deps = root.find(tag("dependencies"))
    if deps is not None:
        info["dependencies"] = list(deps.findall(tag("dependency")))
    return info


def load_pom(group, artifact, version, repos):
    coordinate = f"{group}:{artifact}:{version}"
    cached = os.path.join(gav_path(group, artifact, version), f"{artifact}-{version}.pom")
    if os.path.exists(cached):
        with open(cached, "rb") as source:
            return parse_pom(source.read())
    for repo in repos:
        url = f"{repo}/{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.pom"
        data = http(url)
        if data:
            save(group, artifact, version, f"{artifact}-{version}.pom", data, coordinate)
            return parse_pom(data)
    return None


def merged_properties(info, repos, depth=0):
    properties = dict(info.get("properties") or {})
    parent = info.get("parent")
    if parent and parent.get("group") and parent.get("artifact") and parent.get("version") and depth < 6:
        parent_info = load_pom(parent["group"], parent["artifact"], parent["version"], repos)
        if parent_info:
            properties.update(merged_properties(parent_info, repos, depth + 1))
            for key, value in (parent_info.get("managed") or {}).items():
                managed = info.setdefault("managed", {})
                managed.setdefault(key, value)
    for key in ("project.groupId", "project.artifactId", "project.version", "pom.groupId", "pom.version"):
        default = {
            "project.groupId": info.get("group"),
            "pom.groupId": info.get("group"),
            "project.artifactId": info.get("artifact"),
            "project.version": info.get("version"),
            "pom.version": info.get("version"),
        }[key]
        properties.setdefault(key, default)
    return properties


def resolve(group, artifact, version, packaging, classifier, deps, repos, depth=0):
    coordinate = f"{group}:{artifact}:{version}"
    if (coordinate, classifier, packaging) in VISITED:
        return
    VISITED.add((coordinate, classifier, packaging))

    info = load_pom(group, artifact, version, repos)
    if info and not classifier:
        packaging = info.get("packaging") or packaging
    if info and deps and depth < 8:
        properties = merged_properties(info, repos)
        for dep in info.get("dependencies", []):
            def text(name):
                node = dep.find(name)
                if node is None:
                    # namespace-aware lookup
                    for child in dep:
                        if child.tag.split("}")[-1] == name:
                            return child.text.strip() if child.text else None
                return node.text.strip() if node is not None and node.text else None
            dg = prop(text("groupId"), properties)
            da = prop(text("artifactId"), properties)
            dv = prop(text("version"), properties)
            dtype = prop(text("type"), properties) or "jar"
            dclassifier = prop(text("classifier"), properties) or ""
            scope = prop(text("scope"), properties) or "compile"
            optional = (text("optional") or "").lower() == "true"
            if not dg or not da:
                continue
            if scope in SKIP_SCOPES or optional:
                continue
            if not dv:
                managed = (info.get("managed") or {}).get(f"{dg}:{da}") or {}
                dv = prop(managed.get("version"), properties)
                dtype = prop(managed.get("type"), properties) or dtype
                dclassifier = prop(managed.get("classifier"), properties) or dclassifier
            if not dv:
                log(f"skip {dg}:{da} (version comes from a BOM; warm with Gradle instead)")
                continue
            resolve(dg, da, dv, dtype, dclassifier, True, repos, depth + 1)

    ext = "aar" if packaging == "aar" or (info and info.get("packaging") == "aar") else "jar"
    if packaging == "pom":
        return
    name = f"{artifact}-{version}{'-' + classifier if classifier else ''}.{ext}"
    dest = os.path.join(gav_path(group, artifact, version), name)
    if os.path.exists(dest) and os.path.getsize(dest) > 0:
        log(f"cached {coordinate} -> {dest}")
        return
    for repo in repos:
        url = f"{repo}/{group.replace('.', '/')}/{artifact}/{version}/{name}"
        data = http(url)
        if data:
            check_sha1(url, data)
            save(group, artifact, version, name, data, coordinate)
            return
    raise SystemExit(f"could not find {coordinate} ({name}) in {', '.join(repos)}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("coordinates", nargs="+", help="group:artifact:version[:packaging[:classifier]]")
    parser.add_argument("--no-deps", action="store_true", help="only the given artifact")
    parser.add_argument("--repositories", default="", help="extra comma-separated Maven base URLs")
    parser.add_argument("--m2", default="", help="override the mirror directory")
    args = parser.parse_args()

    global M2
    if args.m2:
        M2 = args.m2
    os.makedirs(M2, exist_ok=True)

    repos = list(DEFAULT_REPOS)
    for extra in args.repositories.split(","):
        extra = extra.strip().rstrip("/")
        if extra and extra not in repos:
            repos.append(extra)
    for extra in os.environ.get("ADIA_MAVEN_REPOS", "").split(","):
        extra = extra.strip().rstrip("/")
        if extra and extra not in repos:
            repos.append(extra)
    log(f"mirror: {M2}")
    log(f"repos:  {', '.join(repos)}")

    for coordinate in args.coordinates:
        group, artifact, version, packaging, classifier = parse_coordinate(coordinate)
        resolve(group, artifact, version, packaging, classifier, not args.no_deps, repos)

    log("done")


if __name__ == "__main__":
    main()
