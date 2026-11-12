"""Drive the phone for screen checks.

  python tools/ui.py shot NAME            screenshot to D:/FrameGlass/shots/NAME.png
  python tools/ui.py tap TEXT [NAME]      tap the element with this text or description, optional shot
  python tools/ui.py xy X Y [NAME]        tap a point, optional shot
  python tools/ui.py dump                 list visible text and descriptions with their bounds
  python tools/ui.py key KEY [NAME]       press a key such as BACK
  python tools/ui.py swipe X1 Y1 X2 Y2 [MS] [NAME]

Every command stops if another app is on screen.
"""
import pathlib
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
ADB = r"D:\dev\android-sdk\platform-tools\adb.exe"
SHOTS = pathlib.Path(r"D:\FrameGlass\shots")


def adb(*a):
    return subprocess.run([ADB, *a], capture_output=True, text=True, encoding="utf-8", errors="replace").stdout


def shot(name):
    SHOTS.mkdir(exist_ok=True)
    out = SHOTS / f"{name}.png"
    with out.open("wb") as f:
        subprocess.run([ADB, "exec-out", "screencap", "-p"], stdout=f)
    print("shot", out)


def nodes():
    adb("shell", "uiautomator", "dump", "--compressed", "/sdcard/fg-ui.xml")
    xml = adb("exec-out", "cat", "/sdcard/fg-ui.xml")
    root = ET.fromstring(xml[xml.index("<"):])
    out = []
    for n in root.iter("node"):
        t = n.get("text") or n.get("content-desc") or ""
        b = re.findall(r"\d+", n.get("bounds") or "")
        if len(b) == 4:
            x1, y1, x2, y2 = map(int, b)
            out.append((t, (x1 + x2) // 2, (y1 + y2) // 2, n.get("clickable"), n.get("class", "").split(".")[-1], (x1, y1, x2, y2)))
    return out


def guard():
    """Every command acts on Frame only. A blind tap must never land in another app."""
    text = adb("shell", "dumpsys", "activity", "activities")
    top = re.search(r"topResumedActivity=\S+ \S+ (\S+?)/", text)
    package = top.group(1) if top else "unknown"
    if not package.startswith("com.frameglass.player"):
        sys.exit(f"stopped: {package} is on screen, not Frame")


def main():
    cmd = sys.argv[1]
    args = sys.argv[2:]
    guard()
    if cmd == "shot":
        shot(args[0])
    elif cmd == "dump":
        for t, x, y, c, k, b in nodes():
            if t:
                print(f"{t[:60]!r} ({x},{y}) {'click' if c == 'true' else ''} {k}")
    elif cmd == "tap":
        # A word can also sit inside a longer hint on another control, so an exact match wins.
        plain = lambda text: re.sub("[⁦-⁩]", "", text).strip().lower()
        everything = nodes()
        hits = [n for n in everything if plain(n[0]) == plain(args[0])]
        if not hits:
            hits = [n for n in everything if plain(args[0]) in plain(n[0])]
        if not hits:
            sys.exit(f"not found: {args[0]}")
        _, x, y, *_ = hits[0]
        adb("shell", "input", "tap", str(x), str(y))
        print("tapped", hits[0][0][:50], x, y)
        if len(args) > 1:
            time.sleep(1.6)
            shot(args[1])
    elif cmd == "xy":
        adb("shell", "input", "tap", args[0], args[1])
        if len(args) > 2:
            time.sleep(1.6)
            shot(args[2])
    elif cmd == "key":
        adb("shell", "input", "keyevent", args[0])
        if len(args) > 1:
            time.sleep(1.2)
            shot(args[1])
    elif cmd == "swipe":
        ms = args[4] if len(args) > 4 else "300"
        adb("shell", "input", "swipe", args[0], args[1], args[2], args[3], ms)
        if len(args) > 5:
            time.sleep(1.2)
            shot(args[5])
    else:
        sys.exit("unknown command")


if __name__ == "__main__":
    main()
