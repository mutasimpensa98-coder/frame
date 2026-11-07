# Owner requests waiting for the next build

- IMDb ratings: fetch them as soon as the OMDb key is added, without waiting for a swipe to refresh. The blue TMDB badge is done.
- After the app language changes, fetch titles, genres and plots again in that language.
- Home hero: skip items with no image, and never show a broken file as a hero.
- Anime filter: real personal clips with short plain names land in Anime. Fix the hint, or require a match.
- Details, episodes: scrolled content shows through under the status bar and collides with the top bar.
- Cinema mode, a Settings option: Home and Library's All show only films, series and anime, so the app feels like a private cinema. Random videos stay reachable through the Videos chip. Off by default, and with no real titles Home says so plainly and offers to turn it off.
- Automatic subtitle sync: after a subtitle is chosen, analyse where speech falls in the video's audio on the phone, compare with cue timing, and set the delay by itself, with a ratio for 23.976 and 25 fps mismatches. Show the applied offset with Undo. Keep manual delay. When confidence is low, change nothing and say so. Nothing leaves the phone. A hash-matched result skips this.

Found on the phone on 2026-11-07:

- The long-press menu does not close with Back.
- About, light theme: the white app mark is invisible on the light canvas. Tint it with `FrameColors.White`.
- Files pushed by adb have no MediaStore row, so delete calls them permanent. Check the trash path on a real camera or download clip.
