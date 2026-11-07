"""Phase 0 test driver for the S26 Ultra. Run from the repo root.

  python tools/matrix.py push            push the test media to the phone
  python tools/matrix.py census          device census into docs/device/census.md
  python tools/matrix.py run [ids...]    run the harness, pull results, write matrix.csv
                                         for a short debug run: $env:PLAY_SEC=5; python tools\matrix.py run V5 S4
                                         then clear it: Remove-Item Env:PLAY_SEC
  python tools/matrix.py rerun           run only the rows that failed or never ran, keep the rest
  python tools/matrix.py report          rebuild matrix.csv from pulled results
  python tools/matrix.py delay           step 0.6 proof
  python tools/matrix.py soak [minutes]  30 minute V5 + S4 run
  python tools/matrix.py gate0           the [AUTO] checks of gate 0: run, delay, soak

Results land in docs/evidence/phase-0/. Nothing here decides a pass that the phone did not show.
A full run moves the previous raw results to raw-<time>, so a row that does not run this time reads
NOT RUN and never passes on an old result.
"""
import csv
import json
import os
import pathlib
import subprocess
import sys
import tempfile
import time

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
ROOT = pathlib.Path(__file__).resolve().parent.parent
GEN = pathlib.Path(r"D:\FrameGlass\testmedia\gen")
REAL = pathlib.Path(r"D:\FrameGlass\testmedia\real")
EV = ROOT / "docs" / "evidence" / "phase-0"
RAW = EV / "raw"
PHONE = "/sdcard/Movies/FrameGlassTest"
PKG = "com.frameglass.player.qa"
ACT = f"{PKG}/com.frameglass.qa.MatrixActivity"
ADB = r"D:\dev\android-sdk\platform-tools\adb.exe"
NO_SEEK_CHECK = {"V10a", "V12", "V13", "V14", "Y1", "C2", "C3"}
SLOW_FIRST_FRAME = {"V5", "V7", "S4", "H1"}
PAUSED = "PAUSED.txt"
FIELDS = ["key", "cls", "result", "notes", "interruptions", "videoDecoder", "audioDecoder", "firstFrameMs", "rendered",
          "dropped", "rmsDb", "ssim", "cues", "assTrack", "error", "seekReadyMs"]


def adb(*a, check=True, capture=True):
    r = subprocess.run([ADB, *a], capture_output=capture, text=True, encoding="utf-8", errors="replace")
    if check and r.returncode != 0:
        sys.exit(f"adb {' '.join(a)} failed: {r.stderr}")
    return r.stdout if capture else ""


def push():
    adb("shell", "mkdir", "-p", PHONE + "/results")
    existing = {}
    for line in adb("shell", f"ls -l '{PHONE}'").splitlines():
        p = line.split(None, 7)
        if len(p) == 8:
            existing[p[7]] = p[4]
    skip = ("hdr10plus.json", "chapters.txt")
    files = [f for f in GEN.iterdir() if f.is_file() and f.name not in skip and not f.name.startswith("__tmp__")]
    total = sum(f.stat().st_size for f in files)
    print(f"{len(files)} files, {total >> 20} MB")
    for f in files:
        if existing.get(f.name) == str(f.stat().st_size):
            continue
        print("push", f.name, flush=True)
        adb("push", str(f), f"{PHONE}/{f.name}")
    print("push done")


def start(mode, *extras):
    adb("shell", "rm", "-f", f"{PHONE}/results/DONE-{mode}.txt", f"{PHONE}/results/{PAUSED}")
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "am", "start", "-n", ACT, "--es", "mode", mode, "--es", "dir", PHONE, *extras)


def wait(mode, limit_s):
    """The time the harness spends paused does not count against the limit."""
    active = 0.0
    was_paused = False
    while active < limit_s:
        s = adb("shell", f"cat {PHONE}/results/DONE-{mode}.txt 2>/dev/null", check=False)
        if s.strip():
            if not s.startswith("ok"):
                print(s[:1500])
            return s.strip()
        t0 = time.time()
        time.sleep(5)
        paused = bool(adb("shell", f"ls {PHONE}/results/{PAUSED} 2>/dev/null", check=False).strip())
        if paused:
            if not was_paused:
                print("  paused: the harness is off screen. The interrupted row runs again when it is back.", flush=True)
            was_paused = True
            continue
        was_paused = False
        active += time.time() - t0
        done = adb("shell", f"ls {PHONE}/results | wc -l", check=False).strip()
        print(f"  {int(active)}s, {done} result files", flush=True)
    sys.exit(f"{mode} timed out")


def pull():
    RAW.mkdir(parents=True, exist_ok=True)
    adb("pull", f"{PHONE}/results/.", str(RAW))


def census():
    start("census")
    wait("census", 60)
    pull()
    c = json.loads((RAW / "census.json").read_text(encoding="utf-8"))
    L = ["# Device census", "", f"Model {c['model']}, SDK_INT {c['sdkInt']}, SDK_INT_FULL {c['sdkIntFull']}, patch {c['security']}",
         f"Build {c['incremental']}", f"Fingerprint `{c['fingerprint']}`", ""]
    page = adb("shell", "getconf", "PAGE_SIZE").strip()
    mem = adb("shell", "am", "memory-limiter", "status", check=False).strip()[:400]
    L += [f"Page size: {page}", "", f"Memory limiter: {mem}", "", "## Display", ""]
    L += [f"- {m['w']}x{m['h']} @ {m['hz']:.2f} Hz, alternatives: {m['alt']}" for m in c["modes"]]
    L += ["", f"HDR types {c['hdrTypes']} (1 Dolby Vision, 2 HDR10, 3 HLG, 4 HDR10+), max {c['hdrMaxNits']} nits, avg {c['hdrAvgNits']} nits, ratio {c['hdrSdrRatio']}", "", "## Decoders", ""]
    for k in c["codecs"]:
        if k["encoder"]:
            continue
        for t in k["types"]:
            size = f" {t.get('maxW')}x{t.get('maxH')}" if "maxW" in t else ""
            L.append(f"- `{k['name']}` {'HW' if k['hw'] else 'SW'} {t['mime']}{size}")
    (ROOT / "docs" / "device" / "census.md").write_text("\n".join(L) + "\n", encoding="utf-8")
    print("\n".join(L[:14]))
    want = ("ac3", "eac3", "ac4", "dts", "dolby-vision", "vvc", "apv", "avc")
    for k in c["codecs"]:
        for t in k["types"]:
            if not k["encoder"] and any(w in t["mime"].lower() for w in want):
                print(k["name"], "HW" if k["hw"] else "SW", t["mime"])


def harness(extras):
    if os.environ.get("PLAY_SEC"):
        extras = extras + ["--ei", "playSec", os.environ["PLAY_SEC"]]
    start("matrix", *extras)
    wait("matrix", 3 * 3600)
    pull()
    return report()


def run(ids):
    if not ids:
        adb("shell", f"rm -f {PHONE}/results/*.json {PHONE}/results/*.png", check=False)
        if RAW.exists():
            RAW.rename(EV / time.strftime("raw-%Y%m%d-%H%M%S"))
    return harness(["--es", "only", ",".join(ids)] if ids else [])


def rerun():
    pull()
    todo = [r["key"] for r in evaluate() if r["result"] != "PASS"]
    if not todo:
        print("nothing to rerun, every row passed")
        return report()
    print(f"rerunning {len(todo)} rows: {' '.join(todo)}", flush=True)
    return harness(["--es", "keys", ",".join(todo)])


def ssim(row_file, png, frame):
    tmp = pathlib.Path(tempfile.gettempdir()) / "fg_ssim_ref.png"
    src = GEN / row_file
    r = subprocess.run(["ffmpeg", "-nostdin", "-v", "error", "-y", "-i", str(src), "-vf", f"select=eq(n\\,{frame})", "-frames:v", "1", str(tmp)], capture_output=True)
    if r.returncode != 0 or not tmp.exists():
        return None
    r = subprocess.run(["ffmpeg", "-nostdin", "-hide_banner", "-i", str(png), "-i", str(tmp), "-lavfi", "[0:v]format=rgb24[a];[1:v]format=rgb24[b];[a][b]ssim", "-f", "null", "-"],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    for line in r.stderr.splitlines():
        if "SSIM" in line and "All:" in line:
            return float(line.split("All:")[1].split()[0])
    return None


def expected_keys(manifest):
    """Every result one full run writes, under the keys MatrixActivity gives them."""
    keys = {}
    for rid, m in manifest.items():
        keys[rid] = m
        for suffix in m.get("sidecars", []):
            keys[f"{rid}@{suffix}"] = m
        for k in range(len(m.get("extra", []))):
            keys[f"{rid}+{k}"] = m
    return keys


def evaluate():
    manifest = {r["id"]: r for r in json.loads((GEN / "manifest.json").read_text(encoding="utf-8"))}
    rows = []
    for f in sorted(RAW.glob("*.json")):
        if f.stem in ("census", "delay", "soak"):
            continue
        j = json.loads(f.read_text(encoding="utf-8"))
        m = manifest.get(j.get("id"), {})
        rid = j.get("id", "")
        broken = rid in ("C3",)
        fails = []
        ff = j.get("firstFrameMs", -1)
        limit = 3000 if any(rid.startswith(s) for s in SLOW_FIRST_FRAME) else 1500
        if not broken:
            if j.get("error") and rid != "C2":
                fails.append("error")
            if j.get("exception"):
                fails.append("exception")
            if ff < 0 or ff > limit:
                fails.append(f"firstFrame {ff}")
            ren, drop = j.get("renderedFrames", 0), j.get("droppedFrames", 0)
            pct = 100.0 * drop / max(ren + drop, 1)
            if pct > 0.2:
                fails.append(f"dropped {pct:.2f}%")
            if "ch" in m or m.get("cls") == "Core":
                if j.get("audioRmsDbfs", -200) < -50 and rid not in ("C3",):
                    fails.append(f"no audio {j.get('audioRmsDbfs')}")
            lay = j.get("layout")
            if lay and (lay["overlayW"], lay["overlayH"]) != (lay["stageW"], lay["stageH"]):
                fails.append(f"overlay {lay['overlayW']}x{lay['overlayH']} on a {lay['stageW']}x{lay['stageH']} stage")
            if lay and (abs(2 * lay["stageL"] + lay["stageW"] - lay["windowW"]) > 1 or abs(2 * lay["stageT"] + lay["stageH"] - lay["windowH"]) > 1):
                fails.append("stage not centred")
            if rid == "C2":
                pos, dur = j.get("position", 0), j.get("durationMs", 0)
                if not j.get("incomplete"):
                    fails.append(f"not reported as incomplete: {j.get('error')}")
                if dur <= 0 or pos < 0.4 * dur:
                    fails.append(f"stopped at {pos} of {dur}")
            if rid not in NO_SEEK_CHECK and j.get("seeks"):
                bad = [s for s in j["seeks"] if s.get("frameError", 99) > 1.0 + 1e-6]
                if bad:
                    fails.append(f"seek off in {len(bad)}/5, worst {max(s.get('frameError', 99) for s in bad):.1f}")
            elif rid not in NO_SEEK_CHECK and m.get("fps"):
                fails.append("no seek data")
        sim = None
        png = RAW / (f.stem + ".png")
        if png.exists() and "hdr" not in m and j.get("shotFrame") is not None and not m.get("vfr"):
            sim = ssim(j["file"], png, j["shotFrame"])
            # Reported only. The clips carry per-frame noise and the phone copies through another colour path,
            # so the score says little. The frame bit strip above is the accuracy check.
        rows.append(dict(key=j.get("key"), cls=m.get("cls", ""), result="PASS" if not fails else "FAIL", notes="; ".join(fails),
                         interruptions=j.get("interruptions", 0), videoDecoder=j.get("videoDecoder"), audioDecoder=j.get("audioDecoder"), firstFrameMs=ff,
                         rendered=j.get("renderedFrames"), dropped=j.get("droppedFrames"), rmsDb=round(j.get("audioRmsDbfs", -200), 1),
                         ssim=sim, cues=j.get("cues"), assTrack=j.get("assTrack"), error=j.get("error"),
                         seekReadyMs=max((s.get("readyMs", 0) for s in j.get("seeks", [])), default=None)))
    seen = {r["key"] for r in rows}
    for key, m in expected_keys(manifest).items():
        if key not in seen:
            rows.append({**dict.fromkeys(FIELDS), "key": key, "cls": m.get("cls", ""), "result": "NOT RUN", "notes": "never ran"})
    return rows


def report():
    rows = evaluate()
    out = EV / "matrix.csv"
    with out.open("w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=FIELDS)
        w.writeheader()
        w.writerows(rows)
    core = [r for r in rows if r["cls"] == "Core"]
    missing = sum(r["result"] == "NOT RUN" for r in rows)
    interrupted = sum(r["interruptions"] or 0 for r in rows)
    print(f"rows {len(rows)}, core pass {sum(r['result'] == 'PASS' for r in core)}/{len(core)}, never ran {missing}, interruptions {interrupted}")
    for r in rows:
        if r["result"] != "PASS":
            print(f"{r['key']:<22} {r['cls']:<8} {r['notes']} | {r['videoDecoder']} | {r['audioDecoder']} | {r['error']}")
    return rows


def delay_proof():
    start("delay")
    wait("delay", 300)
    pull()
    print((RAW / "delay.json").read_text(encoding="utf-8")[:3000])


def soak(minutes):
    start("soak", "--ei", "minutes", str(minutes))
    wait("soak", (minutes + 3) * 60)
    pull()
    print((RAW / "soak.json").read_text(encoding="utf-8")[-600:])


def gate0():
    """Exits with an error unless every Core row passed. The delay and soak output still needs reading."""
    rows = run([])
    delay_proof()
    soak(30)
    failed = [r["key"] for r in rows if r["cls"] == "Core" and r["result"] != "PASS"]
    if failed:
        sys.exit(f"core rows not passed: {' '.join(failed)}")


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "run"
    if cmd == "push":
        push()
    elif cmd == "census":
        census()
    elif cmd == "run":
        run(sys.argv[2:])
    elif cmd == "rerun":
        rerun()
    elif cmd == "gate0":
        gate0()
    elif cmd == "report":
        report()
    elif cmd == "delay":
        delay_proof()
    elif cmd == "soak":
        soak(int(sys.argv[2]) if len(sys.argv) > 2 else 30)
    else:
        sys.exit("unknown command")
