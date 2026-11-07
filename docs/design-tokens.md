# Design tokens

Final tuned values, per brief 7.2. Everything not listed here matches the brief's tables. The values live in `glass/.../Tokens.kt`, `FrameMotion.kt` and `GlassSurface.kt`.

None of these values has been tuned on the phone yet, except that the first build showed the sheet tint was too low.

## Changed from the brief

| Token | Brief | Now | Why |
|---|---|---|---|
| Sheet and menu base tint | 0.52 | 0.76 | At 0.52 bright art and page text fought the text of a whole sheet. See `evidence/phase-1/first-build/07.png`. |
| Sheet and menu with nothing to blur | not stated | Solid `#0D0D0D` | A sheet with no blur source fell back to a plain tint, so page text showed through. |
| Player sheets | Dimmed video, image 08 | Solid | `SurfaceView` gives Haze nothing to sample, so the rule above applies. The owner has not decided on this. See `NEXT.md`. |

## Added to the brief

| Token | Value | Use |
|---|---|---|
| Control recipe | Blur 16 dp, tint 0.46, rim 0.5 dp white 16% | Glass buttons, same as Badge |
| Degraded tint | 0.72 minimum | Section 7.9 degraded mode |
| Edge refraction | 20 dp | Tab bar, sheets, menus |
| Top bar rim | Bottom edge only, white 8% | Top bar |
| Tint animation | 200 ms | Contrast tint change |

## Glass recipes in force

| Kind | Blur | Base tint | Rim | Shadow | Sampled |
|---|---|---|---|---|---|
| Tab bar | 24 dp | 0.40 | 1 dp, white 18% to 4% | y 8 dp, blur 24 dp, black 40% | Yes |
| Top bar | 24 dp | 0.40 | Bottom only, white 8% | None | Yes |
| Sheet | 32 dp | 0.76 | 1 dp, white 18% to 4% | y 12 dp, blur 32 dp, black 45% | No |
| Menu | 32 dp | 0.76 | 1 dp, white 18% to 4% | y 12 dp, blur 32 dp, black 45% | No |
| Badge | 16 dp | 0.46 | 0.5 dp, white 16% | None | No |
| Control | 16 dp | 0.46 | 0.5 dp, white 16% | None | No |
