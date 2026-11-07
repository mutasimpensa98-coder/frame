# Phase 0 evidence

`matrix.csv` is the result table of the latest run. The findings are in `docs/phase-0-results.md`.

`raw/` mirrors the phone's results folder and stays on this PC. It is outside git, because it holds phone screenshots.

| In `raw/` | Contents |
|---|---|
| `<key>.json` | Measured values for one row, read by `tools/matrix.py` |
| `<key>.png` | Frame capture for that row |
| `<key>-subN.png` | Subtitle capture at seek N |
| `DONE-*.txt` | Marker for a finished run |

The S1 encoding captures are identical on purpose. `S2-sub0` to `S2-sub5` are blank because that row has no subtitle track.

Earlier runs are in `D:\FrameGlass\logs\`: `matrix-run1.csv` and `matrix-run2.log`.
