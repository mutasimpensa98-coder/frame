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
| Portrait art | Height over 1.1 × width | A poster or an upright phone video is shown whole, never cropped to a wide frame (`glass/.../PosterArt.kt`) |
| Poster wash | Decoded at 96 px, blurred at 48 px wide, radius 4 px, 3 passes; canvas veil 0.42 dark, 0.30 light | Field behind a whole poster in Hero and Details. Blurred once on load, so it is not one of the three blurred layers. |
| Stage poster | Shadow 20 dp, black 55% | The whole poster lifted off its wash |
| Hero stage | Title logo 56 dp high; fade black 28% at the top to clear at 16%, canvas 90% at 60%, full canvas at the bottom | Hero page that shows a poster |

## Glass recipes in force

| Kind | Blur | Base tint | Rim | Shadow | Sampled |
|---|---|---|---|---|---|
| Tab bar | 24 dp | 0.40 | 1 dp, white 18% to 4% | y 8 dp, blur 24 dp, black 40% | Yes |
| Top bar | 24 dp | 0.40 | Bottom only, white 8% | None | Yes |
| Sheet | 32 dp | 0.76 | 1 dp, white 18% to 4% | y 12 dp, blur 32 dp, black 45% | No |
| Menu | 32 dp | 0.76 | 1 dp, white 18% to 4% | y 12 dp, blur 32 dp, black 45% | No |
| Badge | 16 dp | 0.46 | 0.5 dp, white 16% | None | No |
| Control | 16 dp | 0.46 | 0.5 dp, white 16% | None | No |

## Themes (2026-11-21)

Waiver "2026-11-21: More themes and custom colours". The values live in `glass/.../FramePalette.kt`. None has been seen on a phone.

| Theme | Canvas | Raised | Cards | Text | Own accent | Bar tint, worst backdrop | Sheet tint |
|---|---|---|---|---|---|---|---|
| Black (default) | `#0D0D0D` | `#1A1A1A` | `#242424` | `#FFFFFF` | `#FFFFFF` | 0.57 | 0.76 |
| Pure black | `#000000` | `#121214` | `#202023` | `#FFFFFF` | `#FFFFFF` | 0.54 | 0.76 |
| Midnight | `#131C28` | `#1F2B3B` | `#28374A` | `#EAF0F8` | `#5AA9FF` | 0.66 | 0.83 |
| Violet | `#16111F` | `#241B33` | `#2F2342` | `#F1ECFA` | `#A98BFF` | 0.64 | 0.81 |
| Forest | `#0E1714` | `#18261F` | `#203227` | `#E9F3EE` | `#3DDC97` | 0.64 | 0.81 |
| Coffee | `#18130F` | `#261E17` | `#31261D` | `#F5EEE6` | `#FFB13B` | 0.64 | 0.81 |
| Light | `#F5F4F1` | `#E9E7E1` | `#FFFFFF` | `#141414` | `#141414` | 0.66, top bar 0.70 | 0.84 |
| Paper | `#F3EBDD` | `#E7DDCB` | `#FBF5EA` | `#2A2118` | `#8A4B1F`, as text `#80471F` | 0.66, top bar 0.70 | 0.89 |

- "Raised" is chips, fields, placeholders and the bars in the small pictures. On a dark theme it is the storyboard's "card", and the cards of Settings are one step further: the raised surface sits 56.5% of the way from the canvas to the cards, as in Black. On a light theme the storyboard's "card" is the cards, and the raised surface is darker than the canvas.
- Secondary text is the text colour at 70%. Rims and dividers are the text colour at 8%, 12% and 24% on dark themes, and black (the text colour on Paper) at 7%, 12% and 24% on light ones. Paper's outline is 27%, the least that shows.
- The tint of all glass is the canvas. A bar tint is what the contrast rule gives over pure white on a dark theme and pure black on a light one, and a sheet or menu holds that worst case always.

| Accent | On dark themes | On light themes |
|---|---|---|
| White (Black on light) | `#FFFFFF` | the theme's text colour |
| Blue | `#7AB8FF` | `#0A54C2` |
| Red | `#FF8A80` | `#AD2219` |
| Green | `#5FD38D` | `#0A6636` |
| Amber | `#FFC14D` | `#754B00` as text, `#FFC14D` as a fill |
| Violet | `#BFA2FF` | `#6631C2` |
| Pink | `#FF8AD1` | `#A8185F` |
| Cyan | `#3CD3E0` | `#00626D` |

These are the tones on Black and Light. On another theme a tone moves toward the text colour, in steps of 5%, until it keeps 4.5:1 as text on all three surfaces and on a chip washed with 12% of itself. The pill keeps the colour as picked wherever it stands 3:1 apart from all three surfaces. "Any colour" is treated the same way.

Custom theme, from three numbers:

| Value | Rule |
|---|---|
| Canvas | Hue as picked, HSL saturation 0.42, CIE lightness 0 to 36 over shades 0 to 0.6, then 58 to 97 over shades above 0.6 |
| Cards | Lightness of the canvas plus -10 to +20 over the surface slider. Never past 46 on a dark canvas or under 54 on a light one |
| Raised | 55% of the cards' distance, at least 2.5 away, never past 41 on a dark canvas. On a light canvas always darker than it, by 3 at least, never under 56.5 |
| Text | Lightness 95 of the hue on a dark canvas, 12 on a light one, where that keeps 7:1 on all three surfaces. Otherwise pure white or pure black |
| Default | Hue 220, shade 0.26, surface 0.8, accent cyan: canvas `#1A2740`, raised `#283856`, cards `#33466B` |

Other values added:

| Token | Value | Use |
|---|---|---|
| Theme change | 200 ms, the tint animation | Every colour fades from the old theme to the new one |
| Coloured tint margin | 3 levels | The contrast limit of a tint that is not pure grey |
| Tile rim | 1 dp, the outline colour | A Settings icon tile closer than 2.5:1 to the card under it |
| Action bar recipe | The tab bar's, with the limit of the error colour | The bar of selection mode and of Media Manager, whose delete labels are in the error colour. Sampled on dark themes; on Black it is the tab bar up to backdrop level 51 |
