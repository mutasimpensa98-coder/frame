"""Generate the Phase 0 media test matrix (brief section 5, step 0.3).

Run: python tools/make_testmedia.py            all rows, skips finished files
     python tools/make_testmedia.py V1 S3      only rows whose id starts with these
Output: D:\\FrameGlass\\testmedia\\gen plus manifest.json for the harness.
"""
import json
import os
import pathlib
import subprocess
import sys
import time

OUT = pathlib.Path(r"D:\FrameGlass\testmedia\gen")
FONTS = pathlib.Path(r"D:\FrameGlass\testmedia\fonts")
FF = ["ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error", "-y"]
FONT = "C\\:/Windows/Fonts/consola.ttf"
SR = 48000
ROWS = []


def strip(w):
    """16-bit frame number strip, top left: one white block per set bit."""
    b = max(w // 24, 16)
    parts = [f"drawbox=x=0:y=0:w={b * 16}:h={b}:color=black:t=fill"]
    for i in range(16):
        parts.append(f"drawbox=x={i * b}:y=0:w={b}:h={b}:color=white:t=fill:enable='bitand(n,{1 << i})'")
    return ",".join(parts)


def video(w, h, fps, dur, noise=10, tail=""):
    src = f"testsrc2=size={w}x{h}:rate={fps}:duration={dur}"
    text = (f"drawtext=fontfile='{FONT}':text='%{{frame_num}}':x=w/2:y=h-th-40:"
            f"fontsize={max(h // 18, 18)}:fontcolor=white:box=1:boxcolor=black")
    chain = [f"noise=alls={noise}:allf=t", text, strip(w)]
    if tail:
        chain.append(tail)
    return ["-f", "lavfi", "-i", src], ",".join(chain)


def stereo(dur):
    return ["-f", "lavfi", "-i", f"aevalsrc='0.2*sin(2*PI*330*t)|0.2*sin(2*PI*440*t)':s={SR}:c=stereo:d={dur}"]


def surround(dur):
    tones = "|".join(f"0.15*sin(2*PI*{f}*t)" for f in (220, 277, 330, 80, 392, 494))
    return ["-f", "lavfi", "-i", f"aevalsrc='{tones}':s={SR}:c=5.1(side):d={dur}"]


def run(args):
    r = subprocess.run(FF + args, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if r.returncode != 0:
        raise RuntimeError(r.stderr[-1500:])


def row(rid, cls, name, make, **meta):
    ROWS.append(dict(id=rid, cls=cls, name=name, make=make, meta=meta))


def x264(extra=()):
    return ["-c:v", "libx264", "-preset", "veryfast", *extra]


def encode(out, vin, vf, ain, vcodec, acodec, extra=()):
    args = [*vin, *ain, "-vf", vf, *vcodec, *acodec, *extra, str(out)]
    run(args)


AAC = ["-c:a", "aac", "-b:a", "192k"]


def simple(w, h, fps, dur, vcodec, acodec=AAC, ain=None, extra=(), tail="", noise=10):
    def make(out):
        vin, vf = video(w, h, fps, dur, noise=noise, tail=tail)
        encode(out, vin, vf, ain if ain is not None else stereo(dur), vcodec, acodec, extra)
    return make


# ---- subtitle text ----------------------------------------------------------
AR = ["مرحباً بك في اختبار الترجمة العربية.", "الحروف يجب أن تكون متصلة بشكل صحيح.", "هل يعمل هذا السطر كما ينبغي؟",
      "(ضحك) نعم!", "S01 هو الأول.", 'قال "hello" ثم مشى.', "عام 2026 كان جيداً، أليس كذلك؟", "إنها الساعة 10:45 الآن."]


def ts_srt(t):
    ms = int(t * 1000)
    return f"{ms // 3600000:02}:{ms // 60000 % 60:02}:{ms // 1000 % 60:02},{ms % 1000:03}"


def ts_vtt(t):
    return ts_srt(t).replace(",", ".")


def ts_ass(t):
    cs = int(t * 100)
    return f"{cs // 360000}:{cs // 6000 % 60:02}:{cs // 100 % 60:02}.{cs % 100:02}"


def srt_text(lines=AR, dur=30, step=3):
    out, i, t = [], 1, 0.5
    while t + step <= dur:
        out.append(f"{i}\n{ts_srt(t)} --> {ts_srt(t + step - 0.3)}\n{lines[(i - 1) % len(lines)]}\n")
        i += 1
        t += step
    return "\n".join(out)


def vtt_text(lines=AR, dur=30, step=3):
    out, i, t = ["WEBVTT\n"], 1, 0.5
    while t + step <= dur:
        out.append(f"{ts_vtt(t)} --> {ts_vtt(t + step - 0.3)}\n{lines[(i - 1) % len(lines)]}\n")
        i += 1
        t += step
    return "\n".join(out)


ASS_HEAD = """[Script Info]
ScriptType: v4.00+
PlayResX: 1920
PlayResY: 1080
WrapStyle: 0
ScaledBorderAndShadow: yes

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Main,{font},64,&H00FFFFFF,&H0000FFFF,&H00000000,&H64000000,0,0,0,0,100,100,0,0,1,4,2,2,60,60,50,178
Style: Top,{font},52,&H0000E5FF,&H0000FFFF,&H00301000,&H64000000,0,0,0,0,100,100,0,0,1,3,1,8,60,60,50,178

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
"""


def ass_styled(font, dur=30):
    ev, t, i = [], 0.5, 0
    fx = ["", "{\\fad(300,300)}", "{\\an8}", "{\\pos(960,300)}", "{\\c&H0000FF&}", "{\\bord6}"]
    while t + 3 <= dur:
        style = "Top" if i % 5 == 4 else "Main"
        ev.append(f"Dialogue: 0,{ts_ass(t)},{ts_ass(t + 2.7)},{style},,0,0,0,,{fx[i % len(fx)]}{AR[i % len(AR)]}")
        t += 3
        i += 1
    return ASS_HEAD.format(font=font) + "\n".join(ev) + "\n"


def ass_heavy(font, dur=120, events=5000):
    ev = []
    for i in range(events):
        s = dur * i / events
        e = min(s + 2.5, dur)
        x, y = 200 + i * 37 % 1500, 100 + i * 53 % 800
        kar = "".join(f"{{\\k{10 + (j * 7) % 30}}}{w} " for j, w in enumerate(AR[i % len(AR)].split()))
        ev.append(f"Dialogue: 0,{ts_ass(s)},{ts_ass(e)},Main,,0,0,0,,"
                  f"{{\\move({x},{y},{x + 200},{y + 60})\\t(0,1200,\\fscx130\\fscy130\\frz6)\\fad(120,120)}}{kar}")
    return ASS_HEAD.format(font=font) + "\n".join(ev) + "\n"


def write(path, text, encoding="utf-8", newline="\n"):
    with open(path, "w", encoding=encoding, newline=newline) as f:
        f.write(text)


def font():
    f = FONTS / "NotoNaskhArabic-Regular.ttf"
    if not f.exists():
        raise RuntimeError(f"missing font {f}")
    return f


# ---- rows -------------------------------------------------------------------
HEVC = ["-c:v", "libx265", "-preset", "veryfast", "-x265-params", "log-level=error"]
HEVC10 = HEVC + ["-pix_fmt", "yuv420p10le", "-profile:v", "main10"]
F10 = "format=yuv420p10le"

row("V1", "Core", "V1_h264_8bit_1080p24.mp4", simple(1920, 1080, "24", 30, x264(["-pix_fmt", "yuv420p", "-g", "120", "-b:v", "8M"])), fps=24)
row("V2", "Core", "V2_h264_10bit_hi10p_1080p.mkv", simple(1920, 1080, "24000/1001", 30, x264(["-pix_fmt", "yuv420p10le", "-profile:v", "high10", "-g", "120", "-b:v", "8M"])), fps=23.976)
row("V3", "Core", "V3_hevc_8bit_1080p.mkv", simple(1920, 1080, "24", 30, HEVC + ["-pix_fmt", "yuv420p", "-g", "120", "-b:v", "6M"]), fps=24)
row("V4", "Core", "V4_hevc_10bit_1080p.mkv", simple(1920, 1080, "24", 30, HEVC10 + ["-g", "120", "-b:v", "6M"]), fps=24)
row("V5", "Core", "V5_hevc_10bit_4k60_80m.mkv", simple(3840, 2160, "60", 120, HEVC10 + ["-preset", "ultrafast", "-g", "300", "-b:v", "80M", "-maxrate", "100M", "-bufsize", "160M"], noise=8), fps=60)
row("V6", "Core", "V6_av1_10bit_1080p.mkv", simple(1920, 1080, "24", 30, ["-c:v", "libsvtav1", "-preset", "10", "-pix_fmt", "yuv420p10le", "-g", "120", "-b:v", "5M"], ["-c:a", "libopus", "-b:a", "128k"]), fps=24)
row("V7", "Core", "V7_av1_10bit_4k.mkv", simple(3840, 2160, "24", 30, ["-c:v", "libsvtav1", "-preset", "11", "-pix_fmt", "yuv420p10le", "-g", "120", "-b:v", "20M"], ["-c:a", "libopus", "-b:a", "128k"]), fps=24)
row("V8", "Core", "V8_vp9_p2_10bit_4k.webm", simple(3840, 2160, "24", 30, ["-c:v", "libvpx-vp9", "-profile:v", "2", "-pix_fmt", "yuv420p10le", "-deadline", "realtime", "-cpu-used", "8", "-row-mt", "1", "-g", "120", "-b:v", "20M"], ["-c:a", "libopus", "-b:a", "128k"]), fps=24)
row("V9", "Core", "V9_xvid_asp.avi", simple(1280, 720, "24", 30, ["-c:v", "libxvid", "-bf", "2", "-flags", "+qpel", "-vtag", "XVID", "-b:v", "2500k"], ["-c:a", "libmp3lame", "-b:a", "160k"]), fps=24)


def rotated(out):
    tmp = out.with_suffix(".tmp.mp4")
    simple(1280, 720, "30", 30, x264(["-pix_fmt", "yuv420p"]))(tmp)
    run(["-display_rotation", "90", "-i", str(tmp), "-c", "copy", str(out)])
    tmp.unlink()


row("V10a", "Core", "V10a_rotated.mp4", rotated, fps=30)
row("V10b", "Core", "V10b_anamorphic_720x576_16x9.mkv", simple(720, 576, "25", 30, x264(["-pix_fmt", "yuv420p", "-aspect", "16:9"])), fps=25)
for tag, rate, f in (("23976", "24000/1001", 23.976), ("25", "25", 25), ("2997", "30000/1001", 29.97), ("60", "60", 60), ("120", "120", 120)):
    row(f"V11_{tag}", "Core", f"V11_{tag}fps.mkv", simple(1920, 1080, rate, 30, x264(["-pix_fmt", "yuv420p", "-b:v", "8M"])), fps=f)


def vfr(out):
    vin, vf = video(1280, 720, "30", 30)
    run([*vin, *stereo(30), "-vf", vf + ",setpts='N/30/TB+0.012*sin(N/7)/TB'", "-fps_mode", "vfr", *x264(["-pix_fmt", "yuv420p"]), *AAC, str(out)])


row("V11_vfr", "Core", "V11_vfr.mkv", vfr, fps=30, vfr=True)
row("V12", "Stretch", "V12_mpeg2_interlaced.ts", simple(1920, 1080, "25", 30, ["-c:v", "mpeg2video", "-flags", "+ilme+ildct", "-top", "1", "-b:v", "12M"], ["-c:a", "mp2", "-b:a", "192k"], tail="tinterlace=interleave_top"), fps=25)
row("V13", "Stretch", "V13_wmv2.wmv", simple(1280, 720, "30", 30, ["-c:v", "wmv2", "-b:v", "3M"], ["-c:a", "wmav2", "-b:a", "160k"]), fps=30)
row("V14", "Stretch", "V14_rv20.rm", simple(352, 288, "25", 30, ["-c:v", "rv20", "-b:v", "400k", "-maxrate", "400k", "-bufsize", "200k", "-strict", "-2"], ["-c:a", "ac3", "-b:a", "128k"], noise=2), fps=25)
row("V15", "Stretch", "V15_vvc_1080p.mkv", simple(1920, 1080, "24", 10, ["-c:v", "libvvenc", "-preset", "faster", "-b:v", "4M", "-pix_fmt", "yuv420p10le"], noise=4), fps=24)
row("V16", "Stretch", "V16_flv_h264.flv", simple(1280, 720, "30", 30, x264(["-pix_fmt", "yuv420p"])), fps=30)

MD = "G(13250,34500)B(7500,3000)R(34000,16000)WP(15635,16450)L(10000000,1)"
row("H1", "Core", "H1_hdr10_4k60.mkv", simple(3840, 2160, "60", 120, ["-c:v", "libx265", "-preset", "ultrafast", "-pix_fmt", "yuv420p10le", "-profile:v", "main10", "-g", "300", "-b:v", "80M", "-maxrate", "100M", "-bufsize", "160M",
    "-color_primaries", "bt2020", "-color_trc", "smpte2084", "-colorspace", "bt2020nc",
    "-x265-params", f"log-level=error:hdr10=1:repeat-headers=1:colorprim=bt2020:transfer=smpte2084:colormatrix=bt2020nc:master-display={MD}:max-cll=1000,400"], noise=8), fps=60, hdr="HDR10")
row("H2", "Core", "H2_hlg_1080p.mkv", simple(1920, 1080, "30", 30, ["-c:v", "libx265", "-preset", "veryfast", "-pix_fmt", "yuv420p10le", "-profile:v", "main10", "-b:v", "12M",
    "-color_primaries", "bt2020", "-color_trc", "arib-std-b67", "-colorspace", "bt2020nc",
    "-x265-params", "log-level=error:repeat-headers=1:colorprim=bt2020:transfer=arib-std-b67:colormatrix=bt2020nc"]), fps=30, hdr="HLG")


def hdr10plus(out):
    frames = 30 * 24
    info = []
    for i in range(frames):
        info.append({"BezierCurveData": {"Anchors": [102, 205, 307, 410, 512, 614, 717, 819, 922], "KneePointX": 0, "KneePointY": 0},
                     "LuminanceParameters": {"AverageRGB": 200 + i % 400, "LuminanceDistributions": {"DistributionIndex": [1, 5, 10, 25, 50, 75, 90, 95, 99], "DistributionValues": [0, 10, 50, 100, 300, 500, 800, 900, 1000]},
                                             "MaxScl": [1000, 1000, 1000]},
                     "NumberOfWindows": 1, "TargetedSystemDisplayMaximumLuminance": 1000})
    js = OUT / "hdr10plus.json"
    write(js, json.dumps({"JSONInfo": {"HDR10plusProfile": "B", "Version": "1.0"}, "SceneInfo": info,
                          "SceneInfoSummary": {"SceneFirstFrameIndex": [0], "SceneFrameNumbers": [frames]},
                          "ToolInfo": {"Tool": "frame-glass", "Version": "1"}}))
    os.chdir(OUT)  # x265 splits its params on colons, so the path must have none
    simple(1920, 1080, "24", 30, ["-c:v", "libx265", "-preset", "veryfast", "-pix_fmt", "yuv420p10le", "-profile:v", "main10", "-b:v", "12M",
                                  "-color_primaries", "bt2020", "-color_trc", "smpte2084", "-colorspace", "bt2020nc",
                                  "-x265-params", f"log-level=error:hdr10=1:repeat-headers=1:colorprim=bt2020:transfer=smpte2084:colormatrix=bt2020nc:master-display={MD}:max-cll=1000,400:dhdr10-info=hdr10plus.json"])(out)


row("H3", "Core", "H3_hdr10plus_1080p.mkv", hdr10plus, fps=24, hdr="HDR10+")

SMALL = x264(["-pix_fmt", "yuv420p", "-b:v", "2M"])
row("A1a", "Core", "A1a_aac_stereo.mp4", simple(1280, 720, "24", 30, SMALL), fps=24, ch=2)
row("A1b", "Core", "A1b_mp3_stereo.mkv", simple(1280, 720, "24", 30, SMALL, ["-c:a", "libmp3lame", "-b:a", "192k"]), fps=24, ch=2)
row("A2", "Core", "A2_ac3_51.mkv", simple(1280, 720, "24", 30, SMALL, ["-c:a", "ac3", "-b:a", "448k"], surround(30)), fps=24, ch=6)
row("A3", "Core", "A3_eac3_51.mkv", simple(1280, 720, "24", 30, SMALL, ["-c:a", "eac3", "-b:a", "640k"], surround(30)), fps=24, ch=6)
row("A4", "Core", "A4_dts_51.mkv", simple(1280, 720, "24", 30, SMALL, ["-c:a", "dca", "-strict", "-2", "-b:a", "1509k"], surround(30)), fps=24, ch=6)
row("A5", "Core", "A5_truehd_51.mkv", simple(1280, 720, "24", 30, SMALL, ["-c:a", "truehd", "-strict", "-2"], surround(30)), fps=24, ch=6)
row("A6a", "Core", "A6a_flac_51.mkv", simple(1280, 720, "24", 30, SMALL, ["-c:a", "flac"], surround(30)), fps=24, ch=6)
row("A6b", "Core", "A6b_opus_51.mkv", simple(1280, 720, "24", 30, SMALL, ["-af", "aformat=channel_layouts=5.1", "-c:a", "libopus", "-b:a", "384k", "-mapping_family", "1"], surround(30)), fps=24, ch=6)
row("A6c", "Core", "A6c_vorbis.mkv", simple(1280, 720, "24", 30, SMALL, ["-c:a", "libvorbis", "-b:a", "192k"]), fps=24, ch=2)
row("A8a", "Stretch", "A8a_alac.mp4", simple(1280, 720, "24", 30, SMALL, ["-c:a", "alac"]), fps=24, ch=2)
row("A8b", "Stretch", "A8b_pcm24.mkv", simple(1280, 720, "24", 30, SMALL, ["-c:a", "pcm_s24le"]), fps=24, ch=2)
row("A8c", "Stretch", "A8c_wma2.wmv", simple(1280, 720, "24", 30, ["-c:v", "wmv2", "-b:v", "2M"], ["-c:a", "wmav2", "-b:a", "160k"]), fps=24, ch=2)

BASE = simple(1920, 1080, "24", 30, x264(["-pix_fmt", "yuv420p", "-b:v", "4M"]), noise=4)


def s1(out):
    BASE(out)
    b = out.with_suffix("")
    write(f"{b}.utf8.srt", srt_text())
    write(f"{b}.utf8bom.srt", "\ufeff" + srt_text())
    write(f"{b}.utf16.srt", "\ufeff" + srt_text(), encoding="utf-16-le")
    write(f"{b}.cp1256.srt", srt_text(), encoding="cp1256")


row("S1", "Core", "S1_srt_encodings.mp4", s1, fps=24, sidecars=[".utf8.srt", ".utf8bom.srt", ".utf16.srt", ".cp1256.srt"])


def s2(out):
    BASE(out)
    b = out.with_suffix("")
    write(f"{b}.vtt", vtt_text())
    write(f"{b}.tmp.srt", srt_text())
    run(["-i", str(out), "-i", f"{b}.tmp.srt", "-map", "0", "-map", "1", "-c", "copy", "-c:s", "mov_text", "-metadata:s:s:0", "language=ara", f"{b}_movtext.mp4"])
    os.unlink(f"{b}.tmp.srt")


row("S2", "Core", "S2_vtt_movtext.mp4", s2, fps=24, sidecars=[".vtt"], extra=["S2_vtt_movtext_movtext.mp4"])


def s3(out):
    base = out.with_suffix(".base.mkv")
    BASE(base)
    sub = out.with_suffix(".ass")
    write(sub, ass_styled("Noto Naskh Arabic"))
    run(["-i", str(base), "-i", str(sub), "-map", "0", "-map", "1", "-c", "copy", "-c:s", "ass", "-metadata:s:s:0", "language=ara",
         "-attach", str(font()), "-metadata:s:t", "mimetype=font/ttf", str(out)])
    base.unlink()


row("S3", "Core", "S3_ass_arabic_styled.mkv", s3, fps=24, subs="embedded")


def s4(out):
    src = OUT / "V5_hevc_10bit_4k60_80m.mkv"
    sub = out.with_suffix(".ass")
    write(sub, ass_heavy("Noto Naskh Arabic"))
    run(["-i", str(src), "-i", str(sub), "-map", "0", "-map", "1", "-c", "copy", "-c:s", "ass", "-metadata:s:s:0", "language=ara",
         "-attach", str(font()), "-metadata:s:t", "mimetype=font/ttf", str(out)])


row("S4", "Core", "S4_ass_heavy_over_v5.mkv", s4, fps=60, subs="embedded")


def s5(out):
    base = out.with_suffix(".base.mkv")
    BASE(base)
    a, s = out.with_suffix(".tmp.ass"), out.with_suffix(".tmp.srt")
    write(a, ass_styled("Noto Naskh Arabic"))
    write(s, srt_text())
    run(["-i", str(base), "-i", str(a), "-i", str(s), "-map", "0", "-map", "1", "-map", "2", "-c", "copy", "-c:s:0", "ass", "-c:s:1", "srt",
         "-metadata:s:s:0", "language=ara", "-metadata:s:s:1", "language=ara", "-attach", str(font()), "-metadata:s:t", "mimetype=font/ttf", str(out)])
    base.unlink(); a.unlink(); s.unlink()


row("S5", "Core", "S5_embedded_ass_font_srt.mkv", s5, fps=24, subs="embedded")


def s6(out):
    base = out.with_suffix(".base.mkv")
    BASE(base)
    a = out.with_suffix(".ass")
    write(a, ass_styled("Font That Does Not Exist"))
    run(["-i", str(base), "-i", str(a), "-map", "0", "-map", "1", "-c", "copy", "-c:s", "ass", "-metadata:s:s:0", "language=ara", str(out)])
    base.unlink()


row("S6", "Core", "S6_ass_missing_font.mkv", s6, fps=24, subs="embedded")


def s7(out):
    base = out.with_suffix(".base.mkv")
    BASE(base)
    lines = AR[3:] + ["(ضحك) نعم!", "S01E08 هو الأخير.", "رقم 123، ثم 456."]
    srt = out.with_suffix(".srt")
    write(srt, srt_text(lines))
    a = out.with_suffix(".tmp.ass")
    write(a, ASS_HEAD.format(font="Noto Naskh Arabic") + "\n".join(
        f"Dialogue: 0,{ts_ass(i * 3 + .5)},{ts_ass(i * 3 + 3.2)},Main,,0,0,0,,{lines[i % len(lines)]}" for i in range(9)) + "\n")
    run(["-i", str(base), "-i", str(a), "-map", "0", "-map", "1", "-c", "copy", "-c:s", "ass", "-attach", str(font()), "-metadata:s:t", "mimetype=font/ttf", str(out)])
    base.unlink(); a.unlink()


row("S7", "Core", "S7_mixed_bidi.mkv", s7, fps=24, subs="embedded", sidecars=[".srt"])


def s8(out):
    tmp = out.parent / "s8tmp"
    tmp.mkdir(exist_ok=True)
    subs = []
    for i, (lang, txt) in enumerate((("ara", AR), ("eng", ["English line one.", "English line two."]), ("fre", ["Ligne française."]), ("ara", AR[:2]), ("spa", ["Línea española."]))):
        p = tmp / f"{i}.srt"
        write(p, srt_text(txt))
        subs.append((p, lang))
    vin, vf = video(1280, 720, "24", 30, noise=4)
    args = [*vin, *stereo(30), *surround(30), "-f", "lavfi", "-i", f"sine=frequency=600:sample_rate={SR}:duration=30"]
    for p, _ in subs:
        args += ["-i", str(p)]
    args += ["-vf", vf, "-map", "0:v", "-map", "1:a", "-map", "2:a", "-map", "3:a"]
    for i in range(len(subs)):
        args += ["-map", str(4 + i)]
    args += [*SMALL, "-c:a:0", "aac", "-c:a:1", "ac3", "-c:a:2", "aac", "-c:s", "srt",
             "-metadata:s:a:0", "language=ara", "-metadata:s:a:1", "language=eng", "-metadata:s:a:2", "language=jpn"]
    for i, (_, lang) in enumerate(subs):
        args += [f"-metadata:s:s:{i}", f"language={lang}"]
    args += ["-disposition:s:0", "default", "-disposition:s:3", "forced", str(out)]
    run(args)
    for p, _ in subs:
        p.unlink()
    tmp.rmdir()


row("S8", "Core", "S8_multi_tracks.mkv", s8, fps=24, tracks=dict(audio=3, text=5))


def chapters():
    meta = OUT / "chapters.txt"
    body = ";FFMETADATA1\n"
    for i, name in enumerate(("Intro", "Part One", "Part Two", "Credits")):
        body += f"[CHAPTER]\nTIMEBASE=1/1000\nSTART={i * 7500}\nEND={(i + 1) * 7500}\ntitle={name}\n"
    write(meta, body)
    return meta


def c1(out):
    base = out.with_suffix(".base.mp4")
    BASE(base)
    m = chapters()
    run(["-i", str(base), "-i", str(m), "-map_metadata", "1", "-map_chapters", "1", "-map", "0", "-c", "copy", str(out)])
    run(["-i", str(base), "-i", str(m), "-map_metadata", "1", "-map_chapters", "1", "-map", "0", "-c", "copy", str(out.with_suffix(".mp4"))])
    base.unlink(); m.unlink()


row("C1", "Core", "C1_chapters.mkv", c1, fps=24, chapters=4, extra=["C1_chapters.mp4"])


def c2(out):
    tmp = out.with_suffix(".tmp.mkv")
    BASE(tmp)
    data = tmp.read_bytes()
    out.write_bytes(data[: int(len(data) * 0.6)])
    tmp.unlink()


row("C2", "Core", "C2_truncated.mkv", c2, fps=24, truncated=True)


def c3(out):
    out.write_bytes(b"")
    out.with_name("C3_unreadable.mkv").write_bytes(os.urandom(4096))


row("C3", "Core", "C3_zero_byte.mkv", c3, broken=True, extra=["C3_unreadable.mkv"])


def c4(out):
    BASE(out)


row("C4", "Core", "C4 ملف عربي بمسافات 🎬.mp4", c4, fps=24)
row("C5", "Core", "C5_five_hours.mkv", simple(320, 180, "10", 18000, x264(["-preset", "ultrafast", "-pix_fmt", "yuv420p", "-g", "100", "-b:v", "150k"]), ["-c:a", "aac", "-b:a", "48k"], noise=1), fps=10, long=True)


def y1(out):
    run(["-f", "lavfi", "-i", "color=black:size=1280x720:rate=60:duration=60",
         "-f", "lavfi", "-i", f"aevalsrc='0.6*sin(2*PI*1000*t)*lt(mod(t,1),0.05)':s={SR}:c=stereo:d=60",
         "-vf", "drawbox=x=0:y=0:w=iw:h=ih:color=white:t=fill:enable='lt(mod(t,1),0.05)'", *x264(["-pix_fmt", "yuv420p"]), *AAC, str(out)])


row("Y1", "Core", "Y1_sync_flash_beep.mp4", y1, fps=60)
row("Y2", "Core", "Y2_frames_23976.mkv", simple(1920, 1080, "24000/1001", 30, x264(["-pix_fmt", "yuv420p", "-g", "48", "-b:v", "8M"]), noise=4), fps=23.976)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    want = sys.argv[1:]
    done, failed = [], []
    for r in ROWS:
        out = OUT / r["name"]
        meta = dict(id=r["id"], cls=r["cls"], file=r["name"], **r["meta"])
        if want and not any(r["id"].startswith(w) for w in want):
            if out.exists():
                done.append(meta)
            continue
        t = time.time()
        if out.exists() and (out.stat().st_size > 0 or r["id"] == "C3"):
            print(f"skip {r['id']}", flush=True)
            done.append(meta)
            continue
        tmp = out.with_name("__tmp__" + out.name)
        try:
            r["make"](tmp)
            for p in OUT.glob("__tmp__*"):
                p.rename(p.with_name(p.name.replace("__tmp__", "", 1)))
            print(f"ok {r['id']} {out.stat().st_size >> 20} MB {time.time() - t:.0f}s", flush=True)
            done.append(meta)
        except Exception as e:
            failed.append(r["id"])
            for p in OUT.glob("__tmp__*"):
                p.unlink()
            print(f"FAIL {r['id']}: {e}", flush=True)
    write(OUT / "manifest.json", json.dumps(done, ensure_ascii=False, indent=1))
    total = sum(p.stat().st_size for p in OUT.iterdir() if p.is_file())
    print(f"rows {len(done)}, failed {failed}, total {total >> 20} MB", flush=True)


if __name__ == "__main__":
    main()
