"""Download the build toolchain sequentially, with checksums, and no windows.

Run: python tools/bootstrap_downloads.py
Each archive lands in its own empty folder under D:\\dev\\_downloads.
"""
import hashlib
import json
import pathlib
import re
import sys
import urllib.request

ROOT = pathlib.Path(r"D:\dev\_downloads")
UA = {"User-Agent": "frame-bootstrap"}


def fetch(url):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()


def download(url, dest, algo, expected):
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists() and expected:
        h = hashlib.new(algo)
        with dest.open("rb") as f:
            for chunk in iter(lambda: f.read(1 << 20), b""):
                h.update(chunk)
        if h.hexdigest().lower() == expected.lower():
            print(f"skip, already verified: {dest.name}", flush=True)
            return
    print(f"get {url}", flush=True)
    h = hashlib.new(algo)
    req = urllib.request.Request(url, headers=UA)
    got = 0
    with urllib.request.urlopen(req, timeout=120) as r, dest.open("wb") as f:
        while True:
            chunk = r.read(1 << 20)
            if not chunk:
                break
            f.write(chunk)
            h.update(chunk)
            got += len(chunk)
            if got % (1 << 26) < (1 << 20):
                print(f"  {got >> 20} MB", flush=True)
    digest = h.hexdigest().lower()
    if expected and digest != expected.lower():
        dest.unlink()
        sys.exit(f"CHECKSUM MISMATCH {dest.name}: {digest} != {expected}")
    print(f"ok {dest.name} {got >> 20} MB {algo} {digest}", flush=True)


def main():
    # Temurin 21: exact release and SHA-256 from the Adoptium API
    meta = json.loads(fetch(
        "https://api.adoptium.net/v3/assets/latest/21/hotspot"
        "?os=windows&architecture=x64&image_type=jdk&vendor=eclipse"))
    pkg = meta[0]["binary"]["package"]
    print("temurin", meta[0]["version"]["openjdk_version"], flush=True)
    download(pkg["link"], ROOT / "jdk" / pkg["name"], "sha256", pkg["checksum"])

    # Android command-line tools: SHA-1 from Google's repository index
    name = "commandlinetools-win-15859902_latest.zip"
    xml = fetch("https://dl.google.com/android/repository/repository2-3.xml").decode("utf-8")
    m = re.search(r"<checksum[^>]*>([0-9a-f]{40})</checksum>\s*<url>" + re.escape(name), xml)
    if not m:
        m = re.search(r"([0-9a-f]{40})</sha1>\s*</complete>\s*<url>" + re.escape(name), xml)
    if not m:
        sys.exit("cmdline-tools checksum not found in repository index")
    download("https://dl.google.com/android/repository/" + name,
             ROOT / "cmdline" / name, "sha1", m.group(1))

    # Gradle: SHA-256 published beside the zip
    name = "gradle-9.8.2-bin.zip"
    sha = fetch("https://services.gradle.org/distributions/" + name + ".sha256").decode().strip()
    download("https://services.gradle.org/distributions/" + name,
             ROOT / "gradle" / name, "sha256", sha)
    print("ALL DOWNLOADS OK", flush=True)


if __name__ == "__main__":
    main()
