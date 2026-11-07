# Phase 0 results

Measured on the owner's Galaxy S26 Ultra (SM-S948B), Android 17, One UI 9, build S948BXXU5BZJA, on 2026-11-07. Raw data: `docs/evidence/phase-0/`. Device facts: `docs/device/census.md`.

## Summary

Core rows: 50 of 53 pass. Rows V7 onward ran for 8 s each, not the full clip, because the owner needed the phone. The dropped-frame check on those rows covers those 8 s only.

| Item | Result |
|---|---|
| 4K60 HEVC 10-bit, 80 Mbps | Hardware, 0 of 1,799 frames dropped over 30 s, first frame 121 ms |
| AV1 10-bit, 1080p | Hardware, 0 of 720 dropped |
| Hi10P | FFmpeg software, 0 of 718 dropped, so the `[UNVERIFIED]` note in 4.1 is closed |
| HDR10, HLG, HDR10+ | Hardware, 0 dropped. How they look is an `[OWNER]` check, still open |
| AC-3, E-AC-3 | Dolby decoders on the phone. Sound level measured, not heard |
| DTS, TrueHD, ALAC | FFmpeg. Sound level measured, not heard |
| Xvid ASP | `c2.sec.mpeg4.decoder`, passes |
| Exact seek | Within 1 frame on normal rows. Slowest ready time 466 ms at 4K, 955 ms under heavy ASS |
| Subtitle delay | SRT +1000 ms and -800 ms landed within 25 ms of expected in cue logs |
| Audio delay | 4.6 to 16.7 ms from the setting, from -1500 to +1500 ms. `AudioTimestamp` was not used: the gap between video frame time and audio sink position was measured |
| Arabic ASS | Joined letters, correct order after the `AssBidi` fix, fallback for a missing font works |
| 16 KB | `zipalign -c -P 16` passes, though the phone's page size is 4096 |

## Remaining Core rows

- **C2, truncated MKV.** Plays from the start now. The row stopped at 8 s by the short run, so the friendly message at the cut was not reached. Run it for the full clip.
- **S4, heavy ASS over 4K60.** 0 dropped. One of 5 seeks landed 1.1 frames off, against a limit of 1. First frame 1,484 ms, within the 3 s limit.
- **V11_vfr.** Expected frame numbers assume constant frame rate, so the check does not fit a VFR clip. Not a playback fault. The row needs a different check.

## Stretch rows

WMV, RM and WMA fail because Media3 has no extractor for these containers. VVC has no decoder path. FLV plays, but seeks land far off because the file has no index. MPEG-2 and ALAC pass.

## Decisions and changes

- **`:third_party:ass-media`** is built locally from the 0.5.1 sources. Reasons: `AssBidi` fixes Arabic base direction, and the published AAR targets Media3 1.8.0. Every local change is listed in `third_party/ass-media/LOCAL-CHANGES.md`.
- **`DecoderManager` with ass-media.** Its renderer check failed when `AssRenderersFactory` appended a renderer, so `AssRenderer` joins inside `NextRenderersFactory.buildMiscellaneousRenderers`.
- **SSIM** is now reported only. Scores ran 0.59 to 0.93 with the frame numbers matching exactly. The clips carry per-frame noise and the two colour paths differ, so the score does not separate good from bad. The frame bit strip is the accuracy check.
- **mpv** is not needed by any Core row. WMV and RM are Stretch. Per D3, it stays out until the owner has real files that need it.

## Not done

- Step 0.7, glass over video: `setBlurRegions` not measured, because the plain player's button sat off screen in portrait. The scrim fallback ships meanwhile.
- 30 minute soak of V5 with S4.
- Step 0.8 on USB: no drive was available.
- Step 0.9, mpv measure: skipped per the line above.
- Real files for H4 to H6, A7, S9, S10.
- All `[OWNER]` checks of Gate 0.
